package com.tino1b2be.dtmf.io.mp3;

import com.tino1b2be.dtmf.io.AudioSource;
import com.tino1b2be.dtmf.io.internal.SampleConversion;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.IOException;
import java.util.Objects;

/**
 * {@link AudioSource} implementation returned by
 * {@link Mp3AudioSourceProvider}. The decode pipeline rides entirely on
 * {@code javax.sound.sampled}: {@code mp3spi} contributes the MPEG Layer
 * III {@code FormatConversionProvider} that {@link AudioSystem} picks up
 * via its own {@link java.util.ServiceLoader}, and {@code JLayer} does
 * the bit-stream work underneath.
 *
 * <p><strong>This class is package-private on purpose.</strong> External
 * callers observe an MP3 source through the {@link AudioSource} contract
 * returned from {@link Mp3AudioSourceProvider#open(java.nio.file.Path)}
 * or from {@code AudioSources.open(...)}; no consumer needs to name this
 * type. Keeping it non-public avoids committing to a stability contract
 * for its constructor or static factory &mdash; the only supported
 * construction path is {@link #wrap(AudioInputStream)}, invoked
 * exclusively by {@link Mp3AudioSourceProvider} in the same package.
 *
 * <h2>Conversion to PCM16 LE</h2>
 *
 * {@code mp3spi} initially hands back an {@link AudioInputStream} in the
 * MP3's native (compressed) format. The {@link #wrap(AudioInputStream)}
 * factory asks {@link AudioSystem#getAudioInputStream(AudioFormat, AudioInputStream)}
 * to convert that stream into a 16-bit signed little-endian PCM stream
 * with the same sample rate and channel count as the source. The
 * {@code javax.sound.sampled} conversion pipeline is the one place in
 * this module that actually performs MP3 &rarr; PCM decoding; everything
 * else on the read path is byte-walking across the PCM payload.
 *
 * <p>The target {@link AudioFormat} is pinned to:
 * <ul>
 *   <li>encoding {@link AudioFormat.Encoding#PCM_SIGNED} (Requirement 10.9),</li>
 *   <li>bit depth {@code 16} (Requirement 10.9),</li>
 *   <li>channel count and sample rate equal to the source's
 *       (Requirements 10.7, 10.10),</li>
 *   <li>frame size {@code channelCount * 2}, which is the exact byte
 *       footprint of one PCM16 frame at the declared channel count,</li>
 *   <li>little-endian byte order &mdash; {@code mp3spi} emits PCM16 LE
 *       and {@link SampleConversion#decodePcm16LE(byte[], int)} consumes
 *       exactly that layout.</li>
 * </ul>
 *
 * <h2>Read path</h2>
 *
 * Each {@link #read(double[], int, int)} call:
 * <ol>
 *   <li>Checks the closed-state guard from Requirement 3.14 up front.</li>
 *   <li>Sizes a scratch {@code byte[]} to
 *       {@code length * channelCount * 2} bytes &mdash; the exact PCM16
 *       footprint of the requested frame count &mdash; reusing the
 *       field-cached buffer when it is already large enough.</li>
 *   <li>Pulls bytes from the PCM-converted {@link AudioInputStream} in a
 *       loop until either the buffer is full or EOS is observed, because
 *       the {@link java.io.InputStream#read(byte[], int, int)} contract
 *       {@code AudioInputStream} inherits allows short reads at any
 *       point.</li>
 *   <li>Divides the filled byte count by the frame footprint to recover
 *       the whole-frame count, decodes each sample via
 *       {@link SampleConversion#decodePcm16LE(byte[], int)} into the
 *       caller's {@code double[]}, and advances the internal frame
 *       cursor.</li>
 *   <li>Returns the frame count on success or {@code -1} when EOS is
 *       reached before any frames are produced (Requirement 3.6).</li>
 * </ol>
 *
 * <p>A short trailing read that does not cover a full frame is treated
 * as EOS on the subsequent call: the dangling tail bytes cannot be
 * resolved into a valid PCM16 sample without the rest of the frame, and
 * {@code mp3spi} in practice delivers whole frames anyway. Returning the
 * complete frames observed so far matches the {@code AudioSource.read}
 * contract and keeps the caller's decode loop making forward progress.
 *
 * <h2>Seek</h2>
 *
 * MP3 is forward-only from this module's perspective. {@link #canSeek()}
 * returns {@code false} (Requirement 10.11) and {@link #seek(long)}
 * unconditionally throws {@link UnsupportedOperationException}
 * identifying this class (Requirements 3.11, 10.11). Callers who need
 * random access must use a WAV source; the design note in {@code design.md}
 * records this deliberate scope cut.
 *
 * <h2>Total-frame reporting</h2>
 *
 * {@link #totalFrames()} forwards {@code mp3spi}'s reported frame length
 * when it is present and returns {@code -1L} when {@code mp3spi} reports
 * {@link AudioSystem#NOT_SPECIFIED} (Requirement 10.13). The module never
 * pre-scans the stream to synthesise a frame count; a VBR MP3 without a
 * Xing header therefore reports {@code -1L}, and callers that need a
 * definite total must decode to completion and count.
 *
 * <h2>Lifecycle and thread safety</h2>
 *
 * Instances are <em>not</em> thread-safe &mdash; the frame cursor, the
 * closed flag, and the underlying {@link AudioInputStream}'s read
 * position are all mutable state shared across reads. Post-close
 * behaviour follows the {@link AudioSource} contract: any call to
 * {@link #read(double[], int, int)}, {@link #read(double[])}, or
 * {@link #seek(long)} after {@link #close()} throws {@link IOException}
 * identifying the source as closed (Requirement 3.14).
 * {@link #close()} itself is idempotent.
 *
 * @since 2.0.0
 * @see AudioSource
 * @see Mp3AudioSourceProvider
 */
final class Mp3AudioSource implements AudioSource {

    /**
     * Error message used by {@link #seek(long)}. Matches the wording the
     * task list (Task 7.2) and Requirement 3.11 prescribe: the message
     * identifies the implementing class so callers that catch the
     * exception generically can still tell which source refused the
     * seek.
     */
    private static final String SEEK_UNSUPPORTED_MESSAGE =
            "Mp3AudioSource does not support seek";

    /**
     * Bit depth reported by {@link #bitDepth()}. Fixed at {@code 16}
     * because the conversion target in {@link #wrap(AudioInputStream)}
     * pins the PCM output to 16-bit signed (Requirement 10.9).
     */
    private static final int PCM16_BIT_DEPTH = 16;

    /**
     * Bytes per PCM16 sample. The target {@link AudioFormat} is
     * 16-bit signed little-endian, so each sample is exactly two bytes.
     */
    private static final int BYTES_PER_SAMPLE = 2;

    /**
     * PCM-converted audio stream produced by
     * {@link AudioSystem#getAudioInputStream(AudioFormat, AudioInputStream)}
     * in {@link #wrap(AudioInputStream)}. Reads on this stream return
     * raw PCM16 LE bytes; we normalise them to {@code double} via
     * {@link SampleConversion#decodePcm16LE(byte[], int)}.
     */
    private final AudioInputStream pcmStream;

    /**
     * Cached {@code channelCount}, taken from the target format. Held as
     * a field so the per-read byte-footprint arithmetic does not go
     * through {@link AudioFormat#getChannels()} on every call.
     */
    private final int channelCount;

    /**
     * Cached {@code sampleRate}, taken from the target format. Held as
     * a field so {@link #sampleRate()} is a trivial accessor.
     */
    private final int sampleRate;

    /**
     * Cached {@code bytesPerFrame = channelCount * BYTES_PER_SAMPLE}.
     * Used both to size the scratch buffer and to translate a byte
     * count returned by the underlying stream into whole-frame counts.
     */
    private final int bytesPerFrame;

    /**
     * Total frame count reported by {@code mp3spi}'s framing layer, or
     * {@code -1L} when {@code mp3spi} returns
     * {@link AudioSystem#NOT_SPECIFIED} (Requirement 10.13). Captured
     * once at construction time; the MP3 provider never pre-scans the
     * stream to synthesise a more accurate value.
     */
    private final long totalFrames;

    /**
     * Zero-based index of the next frame that will be returned by
     * {@link #read(double[], int, int)} (Requirement 3.13). Starts at
     * {@code 0} and advances strictly monotonically through successful
     * reads; never reset (MP3 is forward-only).
     */
    private long frameCursor;

    /**
     * Scratch byte buffer reused across read calls. Sized on demand to
     * {@code framesRequested * bytesPerFrame} and grown monotonically
     * up to the largest request the caller has ever made.
     */
    private byte[] scratch;

    /**
     * Flipped to {@code true} by {@link #close()}. Subsequent reads and
     * seeks consult this flag up front to honour Requirement 3.14; a
     * second call to {@link #close()} short-circuits here, making the
     * method idempotent.
     */
    private boolean closed;

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    /**
     * Private constructor. Invoked exclusively from
     * {@link #wrap(AudioInputStream)}, which holds the conversion logic
     * that produces the PCM-stream argument.
     */
    private Mp3AudioSource(AudioInputStream pcmStream, AudioFormat targetFormat) {
        this.pcmStream = pcmStream;
        this.channelCount = targetFormat.getChannels();
        this.sampleRate = (int) targetFormat.getSampleRate();
        this.bytesPerFrame = channelCount * BYTES_PER_SAMPLE;
        long reported = pcmStream.getFrameLength();
        this.totalFrames = (reported == AudioSystem.NOT_SPECIFIED) ? -1L : reported;
        this.frameCursor = 0L;
        this.closed = false;
    }

    /**
     * Wrap an {@code mp3spi}-produced {@link AudioInputStream} in the
     * PCM16 LE conversion pipeline and return a ready-to-read
     * {@code Mp3AudioSource}.
     *
     * <p>The input stream is expected to be the output of
     * {@link AudioSystem#getAudioInputStream(java.io.File)} or
     * {@link AudioSystem#getAudioInputStream(java.io.InputStream)} &mdash;
     * i.e. an {@link AudioInputStream} carrying the raw MP3 frames in
     * the encoded MPEG format that {@code mp3spi} recognises. The
     * conversion step below re-asks {@link AudioSystem} for a
     * {@link AudioFormat.Encoding#PCM_SIGNED} stream at the source's
     * sample rate and channel count, 16-bit depth, and little-endian
     * byte order, matching what {@link SampleConversion#decodePcm16LE(byte[], int)}
     * consumes.
     *
     * <p>Closing the returned source closes the PCM-converted stream,
     * which the {@code javax.sound.sampled} conversion layer chains back
     * through to {@code raw}. Callers do not need to close {@code raw}
     * themselves once it has been handed in; the contract is
     * {@link Mp3AudioSourceProvider} relies on when it passes the stream
     * it obtained from {@code AudioSystem} directly into this factory.
     *
     * @param raw the {@link AudioInputStream} produced by
     *            {@code AudioSystem.getAudioInputStream(...)} for the
     *            MP3 input; must be non-null
     * @return an initialised {@code Mp3AudioSource} reading PCM16 LE
     *         samples from the converted stream
     * @throws IOException              if the conversion stream cannot
     *                                  be established. In practice
     *                                  {@link AudioSystem#getAudioInputStream(AudioFormat, AudioInputStream)}
     *                                  throws {@link IllegalArgumentException}
     *                                  when the target format is not
     *                                  reachable; the factory does not
     *                                  swallow that runtime failure,
     *                                  since it indicates a provider
     *                                  configuration defect that should
     *                                  surface to the caller
     * @throws NullPointerException     if {@code raw} is {@code null}
     */
    static Mp3AudioSource wrap(AudioInputStream raw) throws IOException {
        Objects.requireNonNull(raw, "raw");
        AudioFormat src = raw.getFormat();
        // Target format: PCM_SIGNED, 16-bit, same channel count and
        // sample rate as the source, little-endian so the raw PCM bytes
        // match SampleConversion.decodePcm16LE's expected layout.
        AudioFormat target = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                src.getSampleRate(),
                PCM16_BIT_DEPTH,
                src.getChannels(),
                src.getChannels() * BYTES_PER_SAMPLE, // frame size in bytes
                src.getSampleRate(),                  // frame rate (Hz)
                false);                               // little-endian
        AudioInputStream pcm = AudioSystem.getAudioInputStream(target, raw);
        return new Mp3AudioSource(pcm, target);
    }

    // ------------------------------------------------------------------
    // Metadata accessors
    // ------------------------------------------------------------------

    @Override
    public int sampleRate() {
        return sampleRate;
    }

    @Override
    public int channelCount() {
        return channelCount;
    }

    @Override
    public int bitDepth() {
        return PCM16_BIT_DEPTH;
    }

    @Override
    public long totalFrames() {
        return totalFrames;
    }

    @Override
    public long currentFrame() {
        return frameCursor;
    }

    @Override
    public boolean canSeek() {
        // Forward-only stream: mp3spi's conversion layer has no seek
        // primitive, and synthesising one by re-decoding from the start
        // is deliberately out of scope (Req 10.11). Callers that need
        // random access open the source as WAV.
        return false;
    }

    // ------------------------------------------------------------------
    // Read path
    // ------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Reads raw PCM16 LE bytes from the converted stream into a
     * scratch buffer sized to hold {@code length} frames, then decodes
     * each sample via
     * {@link SampleConversion#decodePcm16LE(byte[], int)}. Short reads
     * from the underlying stream are resolved by looping until either
     * the requested byte count is reached or EOS is observed; a trailing
     * partial frame at EOS is dropped because a non-whole number of
     * PCM16 samples cannot be interleaved into the caller's buffer
     * without breaking the {@code AudioSource.read} contract.
     *
     * @throws IOException              if the source has been
     *                                  {@linkplain #close() closed}
     *                                  (Requirement 3.14), or if the
     *                                  underlying PCM stream throws
     * @throws NullPointerException     if {@code buffer} is {@code null}
     * @throws IllegalArgumentException if {@code offset < 0},
     *                                  {@code length < 0}, or
     *                                  {@code offset + length * channelCount}
     *                                  exceeds {@code buffer.length}
     */
    @Override
    public int read(double[] buffer, int offset, int length) throws IOException {
        if (closed) {
            throw new IOException("Mp3AudioSource is closed");
        }
        Objects.requireNonNull(buffer, "buffer");
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0, got " + offset);
        }
        if (length < 0) {
            throw new IllegalArgumentException("length must be >= 0, got " + length);
        }
        // Overflow-safe bounds check: length * channelCount could wrap a
        // 32-bit multiply on pathological inputs, so widen to long.
        long samplesRequested = (long) length * (long) channelCount;
        if ((long) offset + samplesRequested > buffer.length) {
            throw new IllegalArgumentException(
                    "offset=" + offset + " + length=" + length
                            + " * channelCount=" + channelCount
                            + " exceeds buffer.length=" + buffer.length);
        }

        if (length == 0) {
            return 0;
        }

        int bytesToRead = length * bytesPerFrame;
        ensureScratch(bytesToRead);

        // Pull bytes until the scratch is full or the stream reports EOS.
        // AudioInputStream inherits InputStream's short-read semantics,
        // so one read() call may return fewer bytes than requested even
        // when more remain.
        int bytesFilled = 0;
        while (bytesFilled < bytesToRead) {
            int n = pcmStream.read(scratch, bytesFilled, bytesToRead - bytesFilled);
            if (n < 0) {
                break;
            }
            bytesFilled += n;
        }

        if (bytesFilled == 0) {
            // Stream was already at EOS before this call; propagate
            // the -1 sentinel per Requirement 3.6.
            return -1;
        }

        int framesRead = bytesFilled / bytesPerFrame;
        if (framesRead == 0) {
            // A strictly positive partial read that did not cover a full
            // frame cannot be split into a whole number of PCM16
            // samples; treat it as EOS so the caller's decode loop
            // terminates cleanly.
            return -1;
        }

        int samplesToDecode = framesRead * channelCount;
        int byteIndex = 0;
        int bufferIndex = offset;
        for (int s = 0; s < samplesToDecode; s++) {
            buffer[bufferIndex++] = SampleConversion.decodePcm16LE(scratch, byteIndex);
            byteIndex += BYTES_PER_SAMPLE;
        }

        frameCursor += framesRead;
        return framesRead;
    }

    // ------------------------------------------------------------------
    // Seek path
    // ------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>MP3 sources are forward-only: this method unconditionally
     * throws {@link UnsupportedOperationException} identifying this
     * class (Requirements 3.11, 10.11). The closed-state guard from
     * Requirement 3.14 still fires first if the source has already
     * been closed, so a {@code seek(...)} on a closed source surfaces
     * an {@link IOException} rather than the unsupported-operation
     * signal.
     *
     * @throws IOException                   if the source has been
     *                                       {@linkplain #close() closed}
     *                                       (Requirement 3.14)
     * @throws UnsupportedOperationException always, when the source is
     *                                       not closed
     *                                       (Requirements 3.11, 10.11)
     */
    @Override
    public void seek(long frameIndex) throws IOException {
        if (closed) {
            throw new IOException("Mp3AudioSource is closed");
        }
        throw new UnsupportedOperationException(SEEK_UNSUPPORTED_MESSAGE);
    }

    // ------------------------------------------------------------------
    // Close
    // ------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Closes the PCM-converted {@link AudioInputStream}. The
     * {@code javax.sound.sampled} conversion chain propagates the close
     * call back through {@code mp3spi} to the underlying raw
     * {@link AudioInputStream} that {@link Mp3AudioSourceProvider}
     * handed to {@link #wrap(AudioInputStream)}, so the caller's
     * resource obligations around a {@link java.nio.file.Path}-opened
     * file are fully satisfied by calling {@code close()} on this
     * source (Requirements 11.3, 11.4).
     *
     * <p>This method is idempotent: a second and subsequent invocation
     * is a no-op.
     *
     * @throws IOException if the underlying PCM stream's {@code close}
     *                     fails
     */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        pcmStream.close();
    }

    // ------------------------------------------------------------------
    // Internal scratch-buffer management
    // ------------------------------------------------------------------

    /**
     * Ensure {@link #scratch} holds at least {@code required} bytes,
     * allocating or growing monotonically. The buffer is never shrunk
     * because steady-state callers (notably {@link com.tino1b2be.dtmf.io.DtmfFileDecoder})
     * re-invoke {@link #read(double[], int, int)} with the same
     * {@code length} argument many times, so any initial growth is
     * amortised across the whole decode.
     */
    private void ensureScratch(int required) {
        if (scratch == null || scratch.length < required) {
            scratch = new byte[required];
        }
    }
}
