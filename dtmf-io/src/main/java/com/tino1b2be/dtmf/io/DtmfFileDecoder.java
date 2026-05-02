package com.tino1b2be.dtmf.io;

import com.tino1b2be.dtmf.ChannelMode;
import com.tino1b2be.dtmf.DtmfConfig;
import com.tino1b2be.dtmf.DtmfDecoder;
import com.tino1b2be.dtmf.DtmfTone;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * One-call facade over {@link AudioSources} and {@link DtmfDecoder}.
 *
 * <p>{@code DtmfFileDecoder} is the glue between the file-I/O layer and the
 * format-agnostic {@code dtmf-core} decoder. Every overload opens (or
 * accepts) an {@link AudioSource}, reads the full stream into a normalised
 * {@code double[]}, and forwards the buffer to
 * {@link DtmfDecoder#decode(double[], DtmfConfig)}.
 *
 * <h2>Auto-resolve</h2>
 *
 * The file's declared sample rate — read from the opened
 * {@link AudioSource} — takes precedence over the {@link DtmfConfig} the
 * caller supplied (Requirement 17). When {@code source.sampleRate()}
 * differs from {@code config.sampleRate()}, {@code DtmfFileDecoder}
 * internally rebuilds the config with the source's rate substituted in.
 * Every other field of the caller's config (tone/gap durations, detection
 * threshold, channel mode, window function, twist tolerances, confirmation
 * frames) is preserved verbatim. The analysis block size is <em>not</em>
 * copied; it is re-derived from the new rate via
 * {@code BlockSizer.blockSizeFor(newRate)} at {@code build()} time so the
 * effective bin width lands in {@code [40, 60]} Hz for the rate actually
 * on disk (Requirements 17.1, 17.2).
 *
 * <p>Because decoding runs at the source's rate, the {@link DtmfTone}
 * values returned from {@code decode(...)} carry
 * {@code sampleRate = source.sampleRate()} (Requirement 17.5). Callers who
 * need a {@code Duration} for a detected tone should use the
 * {@link DtmfTone#startTime()} / {@link DtmfTone#endTime()} helpers rather
 * than dividing by the {@code DtmfConfig}'s rate.
 *
 * <h2>Channel handling</h2>
 *
 * When {@code source.channelCount() == 2} and
 * {@code config.channelMode() == }{@link ChannelMode#MONO MONO}, the
 * interleaved left and right samples are averaged into a mono buffer before
 * invoking {@code DtmfDecoder} (Requirement 8.7). When
 * {@code config.channelMode()} is
 * {@link ChannelMode#STEREO_INDEPENDENT STEREO_INDEPENDENT} or
 * {@link ChannelMode#STEREO_DOWNMIX STEREO_DOWNMIX}, the interleaved
 * buffer is forwarded to {@code DtmfDecoder} unchanged and {@code
 * DtmfDecoder} applies the configured mode (Requirement 8.8).
 *
 * <h2>Unsupported inputs</h2>
 *
 * <ul>
 *   <li>Channel counts greater than {@code 2} throw
 *       {@link UnsupportedAudioFormatException} naming the count and
 *       stating that only 1 and 2 are supported (Requirement 8.10).</li>
 *   <li>A mono source paired with a stereo channel mode throws
 *       {@link UnsupportedAudioFormatException} naming the mismatch and
 *       pointing the caller at {@link ChannelMode#MONO} (Requirement 8.9).</li>
 *   <li>A sample rate outside the supported {@code [4000, 192000]} Hz range
 *       throws {@link UnsupportedAudioFormatException} naming the rate and
 *       the range (Requirement 17.3).</li>
 * </ul>
 *
 * <h2>Resource ownership</h2>
 *
 * The {@link Path}, {@link InputStream}, and {@link URL} overloads open
 * the {@link AudioSource} internally inside a try-with-resources so the
 * source is always closed before the method returns — on the normal path
 * and on any exceptional path (Requirement 8.11). The
 * {@link #decode(AudioSource, DtmfConfig)} overload never closes the
 * caller-supplied source; ownership stays with the caller (Requirement
 * 8.12).
 *
 * <h2>Null-safety</h2>
 *
 * Every public overload null-checks every parameter via
 * {@link Objects#requireNonNull(Object, String)} and throws
 * {@link NullPointerException} identifying the parameter (Requirements
 * 8.13, 12.1).
 *
 * <h2>Thread safety</h2>
 *
 * {@code DtmfFileDecoder} is stateless and every method is static; calls
 * are safe to invoke concurrently from multiple threads on different
 * inputs.
 *
 * @since 2.0.0
 * @see AudioSources
 * @see DtmfDecoder
 * @see DtmfConfig
 */
public final class DtmfFileDecoder {

    /** Supported sample-rate range, matching {@code DtmfConfig.advanced()}. */
    private static final int MIN_SAMPLE_RATE = 4000;
    private static final int MAX_SAMPLE_RATE = 192_000;

    /** Initial {@code double[]} capacity for {@code readAllFrames}: 64 KiB of doubles. */
    private static final int INITIAL_READ_CAPACITY = 64 * 1024 / Double.BYTES; // 8192

    private DtmfFileDecoder() {
        // Static-only utility; no instances.
    }

    /**
     * Decode the DTMF tones in the audio file at {@code path}.
     *
     * <p>The file is opened via {@link AudioSources#open(Path)} — i.e. the
     * registered {@link AudioSourceProvider} that scores highest on the
     * file's header bytes handles the decode — and the resulting
     * {@link AudioSource} is closed before this method returns, whether the
     * call succeeds or throws (Requirement 8.11).
     *
     * @param path   file system path to the audio file; non-null
     * @param config detection configuration; non-null. The file's declared
     *               sample rate takes precedence over
     *               {@code config.sampleRate()} (see the class-level
     *               auto-resolve notes).
     * @return the detected tones, in non-decreasing {@code startSample} order
     * @throws NullPointerException              if any parameter is {@code null}
     * @throws UnsupportedAudioFormatException   if no provider can decode the
     *                                           file, the file declares an
     *                                           unsupported channel count or
     *                                           sample rate, or the channel
     *                                           mode is incompatible with
     *                                           the source
     * @throws IOException                       if an underlying I/O error occurs
     */
    public static List<DtmfTone> decode(Path path, DtmfConfig config) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(config, "config");
        try (AudioSource source = AudioSources.open(path)) {
            return decodeInternal(source, config);
        }
    }

    /**
     * Decode the DTMF tones in {@code stream}.
     *
     * <p>The stream is passed to {@link AudioSources#open(InputStream, String)},
     * which wraps non-markable streams in a {@link java.io.BufferedInputStream}
     * before provider dispatch. The returned {@link AudioSource} is closed
     * before this method returns (normal or exceptional); per
     * {@code AudioSources}' contract, closing the source does <em>not</em>
     * close the caller-supplied {@code stream} (Requirement 8.11 / 4.10).
     *
     * @param stream audio byte stream; non-null. Caller retains ownership.
     * @param hint   optional file-name / URL-path-segment / MIME-type hint;
     *               may be {@code null}
     * @param config detection configuration; non-null. The stream's declared
     *               sample rate takes precedence over
     *               {@code config.sampleRate()}.
     * @return the detected tones, in non-decreasing {@code startSample} order
     * @throws NullPointerException              if {@code stream} or
     *                                           {@code config} is {@code null}
     * @throws UnsupportedAudioFormatException   see {@link #decode(Path, DtmfConfig)}
     * @throws IOException                       if an underlying I/O error occurs
     */
    public static List<DtmfTone> decode(InputStream stream, String hint, DtmfConfig config)
            throws IOException {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(config, "config");
        try (AudioSource source = AudioSources.open(stream, hint)) {
            return decodeInternal(source, config);
        }
    }

    /**
     * Decode the DTMF tones in the audio resource at {@code url}.
     *
     * <p>Opens {@code url.openStream()} via {@link AudioSources#open(URL)},
     * which derives a provider hint from the URL's last path segment and
     * closes the URL-backed stream when the returned {@link AudioSource} is
     * closed. The {@code AudioSource} is closed before this method returns
     * (Requirement 8.11).
     *
     * @param url    URL of the audio resource; non-null
     * @param config detection configuration; non-null
     * @return the detected tones, in non-decreasing {@code startSample} order
     * @throws NullPointerException              if any parameter is {@code null}
     * @throws UnsupportedAudioFormatException   see {@link #decode(Path, DtmfConfig)}
     * @throws IOException                       if an underlying I/O error occurs
     */
    public static List<DtmfTone> decode(URL url, DtmfConfig config) throws IOException {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(config, "config");
        try (AudioSource source = AudioSources.open(url)) {
            return decodeInternal(source, config);
        }
    }

    /**
     * Decode the DTMF tones in an already-opened {@link AudioSource}.
     *
     * <p>Useful when the caller obtained the source via
     * {@link AudioSources#open(Path) AudioSources.open} directly (e.g. to
     * inspect {@code sampleRate()} or {@code totalFrames()} first) or via
     * {@link RawPcmAudioSource} (caller has PCM bytes in memory).
     *
     * <p>This overload never closes {@code source}; ownership stays with
     * the caller (Requirement 8.12). Callers who want the typical
     * open-decode-close lifecycle should use one of the
     * {@link Path}/{@link InputStream}/{@link URL} overloads instead.
     *
     * @param source opened audio source; non-null. Not closed by this call.
     * @param config detection configuration; non-null
     * @return the detected tones, in non-decreasing {@code startSample} order
     * @throws NullPointerException              if any parameter is {@code null}
     * @throws UnsupportedAudioFormatException   if the source declares an
     *                                           unsupported channel count or
     *                                           sample rate, or the channel
     *                                           mode is incompatible with
     *                                           the source
     * @throws IOException                       if an underlying I/O error occurs
     */
    public static List<DtmfTone> decode(AudioSource source, DtmfConfig config) throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(config, "config");
        return decodeInternal(source, config);
    }

    // ---------------------------------------------------------------------
    // Package-private test seams (Task 5.3, Property 9)
    // ---------------------------------------------------------------------
    //
    // Property 9 ("DtmfFileDecoder close semantics") asserts that the
    // Path/InputStream/URL overloads close the AudioSource they opened
    // internally — on normal AND exceptional returns — while the
    // AudioSource overload NEVER closes the caller's source. Exercising
    // that invariant end-to-end requires injecting a close-counting
    // AudioSource past the AudioSources facade, but the production
    // overloads reach the facade through ServiceLoader-discovered
    // providers and there is no per-call seam to swap them out.
    //
    // These three `decodeForTesting` methods mirror the three
    // "internally-opened-source" public overloads byte-for-byte, except
    // the opening side calls AudioSources.openForTesting(..., providers)
    // instead of AudioSources.open(...). That lets Property 9 hand a
    // test-only `AudioSourceProvider` whose `open(...)` returns a
    // close-counting source, then assert the source's close count is
    // exactly one after the decode returns (or throws). No production
    // code path observes these methods; they live here solely because
    // the try-with-resources block that enforces Req 8.11's close
    // guarantee cannot be reconstructed outside the class.

    /**
     * Test-only mirror of {@link #decode(Path, DtmfConfig)} that dispatches
     * through {@link AudioSources#openForTesting(Path, List)} against the
     * caller-supplied provider list instead of the cached
     * {@code ServiceLoader} results. Package-private so only same-package
     * tests can reach it.
     *
     * <p>Opens the source inside try-with-resources identically to the
     * production overload so Req 8.11's "source closed on return,
     * normal or exceptional" invariant is exercised on the same code
     * path — only the <em>provider</em> is swapped out.
     *
     * @param path      the file to open; non-null
     * @param config    detection configuration; non-null
     * @param providers providers to score against; non-null, non-empty
     * @return detected tones in non-decreasing {@code startSample} order
     * @throws IOException on any I/O failure, including provider dispatch
     */
    static List<DtmfTone> decodeForTesting(
            Path path, DtmfConfig config, List<AudioSourceProvider> providers)
            throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(providers, "providers");
        try (AudioSource source = AudioSources.openForTesting(path, providers)) {
            return decodeInternal(source, config);
        }
    }

    /**
     * Test-only mirror of
     * {@link #decode(InputStream, String, DtmfConfig)} that dispatches
     * through {@link AudioSources#openForTesting(InputStream, String, List)}
     * against the caller-supplied provider list. Package-private.
     *
     * @param stream    byte stream to open; non-null. Caller retains
     *                  ownership.
     * @param hint      optional caller hint; may be {@code null}
     * @param config    detection configuration; non-null
     * @param providers providers to score against; non-null, non-empty
     * @return detected tones in non-decreasing {@code startSample} order
     * @throws IOException on any I/O failure, including provider dispatch
     */
    static List<DtmfTone> decodeForTesting(
            InputStream stream,
            String hint,
            DtmfConfig config,
            List<AudioSourceProvider> providers) throws IOException {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(providers, "providers");
        try (AudioSource source = AudioSources.openForTesting(stream, hint, providers)) {
            return decodeInternal(source, config);
        }
    }

    /**
     * Test-only mirror of {@link #decode(URL, DtmfConfig)} that dispatches
     * through {@link AudioSources#openForTesting(URL, List)} against the
     * caller-supplied provider list. Package-private.
     *
     * @param url       URL of the audio resource; non-null
     * @param config    detection configuration; non-null
     * @param providers providers to score against; non-null, non-empty
     * @return detected tones in non-decreasing {@code startSample} order
     * @throws IOException on any I/O failure, including provider dispatch
     */
    static List<DtmfTone> decodeForTesting(
            URL url, DtmfConfig config, List<AudioSourceProvider> providers)
            throws IOException {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(providers, "providers");
        try (AudioSource source = AudioSources.openForTesting(url, providers)) {
            return decodeInternal(source, config);
        }
    }

    // --- Internal pipeline ---

    /**
     * Shared pipeline used by every public overload. Applies channel-count
     * and sample-rate guards, auto-resolves the sample rate, reads every
     * frame the source will yield, optionally downmixes stereo to mono when
     * the caller's config is {@link ChannelMode#MONO}, and delegates to
     * {@link DtmfDecoder#decode(double[], DtmfConfig)}.
     *
     * <p>Does not close {@code source}; closing (where appropriate) is the
     * caller-overload's responsibility via try-with-resources.
     */
    private static List<DtmfTone> decodeInternal(AudioSource source, DtmfConfig config)
            throws IOException {
        int channels = source.channelCount();
        ChannelMode mode = config.channelMode();

        // Guard: >2 channels — decoder only supports mono and stereo (Req 8.10).
        if (channels > 2) {
            throw new UnsupportedAudioFormatException(
                    "Source has " + channels
                            + " channels; only 1 (mono) and 2 (stereo) are supported.");
        }

        // Guard: mono source paired with a stereo channel mode (Req 8.9).
        if (channels == 1
                && (mode == ChannelMode.STEREO_INDEPENDENT
                        || mode == ChannelMode.STEREO_DOWNMIX)) {
            throw new UnsupportedAudioFormatException(
                    "Source is mono (channelCount=1) but config.channelMode is " + mode
                            + "; use ChannelMode.MONO for mono sources.");
        }

        // Guard: sample rate outside the decoder's supported range (Req 17.3).
        int srcRate = source.sampleRate();
        if (srcRate < MIN_SAMPLE_RATE || srcRate > MAX_SAMPLE_RATE) {
            throw new UnsupportedAudioFormatException(
                    "Source sample rate " + srcRate
                            + " Hz is outside the supported range ["
                            + MIN_SAMPLE_RATE + ", " + MAX_SAMPLE_RATE + "] Hz.");
        }

        // Auto-resolve: rebuild the config with the source's rate when they
        // differ, re-deriving the analysis block size (Req 8.6, 17.1, 17.2).
        DtmfConfig effective = (srcRate == config.sampleRate())
                ? config
                : rebuildWithSampleRate(config, srcRate);

        // Read every frame the source will yield.
        double[] interleaved = readAllFrames(source, channels);

        // Downmix stereo → mono by averaging when the caller asked for MONO (Req 8.7).
        // Otherwise forward the interleaved buffer unchanged (Req 8.8).
        double[] toFeed = (channels == 2 && mode == ChannelMode.MONO)
                ? downmixStereoToMono(interleaved)
                : interleaved;

        return DtmfDecoder.decode(toFeed, effective);
    }

    /**
     * Rebuild {@code c} with the sample rate replaced by {@code newRate}.
     *
     * <p>Preserves every other field of {@code c} verbatim (Requirement
     * 17.2). Does <em>not</em> call {@code analysisBlockSize(...)} on the
     * advanced builder, so the block size is re-derived by
     * {@code BlockSizer.blockSizeFor(newRate)} at {@code build()} time
     * (Requirement 17.1).
     *
     * <p>Package-private so property tests in
     * {@code com.tino1b2be.dtmf.io} can exercise this transform directly
     * without routing through a full decode pipeline.
     *
     * @param c       the caller's config; non-null
     * @param newRate the source's sample rate, in Hz; must lie in
     *                {@code [4000, 192000]} (enforced by the builder)
     * @return a new config with {@code sampleRate() == newRate}, a freshly
     *         derived {@code analysisBlockSize()}, and every other field
     *         equal to {@code c}'s
     */
    static DtmfConfig rebuildWithSampleRate(DtmfConfig c, int newRate) {
        return DtmfConfig.advanced()
                .sampleRate(newRate)                          // triggers block-size re-derivation
                .minimumToneDuration(c.minimumToneDuration())
                .minimumGapDuration(c.minimumGapDuration())
                .detectionThreshold(c.detectionThreshold())
                .channelMode(c.channelMode())
                .windowFunction(c.windowFunction())
                .forwardTwistDb(c.forwardTwistDb())
                .reverseTwistDb(c.reverseTwistDb())
                .confirmationFrames(c.confirmationFrames())
                .build();
    }

    /**
     * Read every frame from {@code source} into an exponentially-growing
     * {@code double[]}, returning a precisely-sized array of interleaved
     * samples ({@code frames * channels} in length).
     *
     * <p>Starts at {@link #INITIAL_READ_CAPACITY} doubles and doubles
     * capacity whenever the next read would overflow, so the asymptotic
     * cost is {@code O(n)} with at most {@code log2(n)} array copies. This
     * is the {@code MP3} case where {@code source.totalFrames()} is {@code
     * -1L} and we cannot pre-size the destination.
     *
     * <p>Tolerates zero-length reads: {@link AudioSource#read(double[], int, int)}
     * is allowed to return {@code 0} transiently (the caller should retry),
     * and only {@code -1} ends the loop.
     */
    private static double[] readAllFrames(AudioSource source, int channels) throws IOException {
        double[] buffer = new double[INITIAL_READ_CAPACITY];
        int length = 0;
        while (true) {
            // Ensure there is room for at least one full frame; otherwise doubling
            // would loop forever when remaining < channels.
            if (length + channels > buffer.length) {
                int newCapacity = buffer.length * 2;
                double[] grown = new double[newCapacity];
                System.arraycopy(buffer, 0, grown, 0, length);
                buffer = grown;
            }
            // Read as many frames as fit in the remaining capacity. The
            // third argument to AudioSource.read(buffer, offset, length) is
            // a FRAME count, not a sample count (per the AudioSource
            // contract: "maximum number of frames to write") — passing a
            // sample count would over-read by `channels` × the intended
            // amount and blow past the buffer for stereo sources.
            int framesCapacity = (buffer.length - length) / channels;
            int framesRead = source.read(buffer, length, framesCapacity);
            if (framesRead < 0) {
                break;                             // end of stream
            }
            length += framesRead * channels;
        }
        if (length == buffer.length) {
            return buffer;                         // exact fit, no copy needed
        }
        double[] trimmed = new double[length];
        System.arraycopy(buffer, 0, trimmed, 0, length);
        return trimmed;
    }

    /**
     * Average interleaved left/right samples into a mono buffer of half the
     * length. Each output sample is
     * {@code (stereo[2i] + stereo[2i + 1]) / 2.0}, matching the downmix the
     * {@link ChannelMode#STEREO_DOWNMIX} path applies inside {@code
     * DtmfDetector}. Output stays in {@code [-1, 1]} because the two inputs
     * are bounded in that range.
     *
     * @param stereo interleaved L,R,L,R,... samples; length must be even
     * @return a new {@code double[]} of length {@code stereo.length / 2}
     */
    private static double[] downmixStereoToMono(double[] stereo) {
        int monoLength = stereo.length / 2;
        double[] mono = new double[monoLength];
        for (int i = 0; i < monoLength; i++) {
            mono[i] = (stereo[2 * i] + stereo[2 * i + 1]) * 0.5;
        }
        return mono;
    }
}
