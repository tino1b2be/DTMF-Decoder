package com.tino1b2be.dtmf.io.mp3;

import com.tino1b2be.dtmf.io.AudioSource;
import com.tino1b2be.dtmf.io.AudioSourceProvider;
import com.tino1b2be.dtmf.io.UnsupportedAudioFormatException;
import com.tino1b2be.dtmf.io.mp3.internal.Mp3HeaderScanner;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * {@link AudioSourceProvider} implementation for MPEG-1 and MPEG-2 Layer
 * III ({@code .mp3}) content. This is the single public entry point of
 * the {@code dtmf-io-mp3} module, discovered by
 * {@link java.util.ServiceLoader} through the
 * {@code META-INF/services/com.tino1b2be.dtmf.io.AudioSourceProvider}
 * registration (Requirement 10.2) and normally invoked indirectly via
 * {@code AudioSources.open(...)}.
 *
 * <h2>Design</h2>
 *
 * Unlike the clean-room RIFF parser in {@code dtmf-io-wav}, this provider
 * is a <em>thin veneer</em> over {@code javax.sound.sampled}. Decoding is
 * delegated to the two external libraries declared in the module's
 * {@code build.gradle.kts} (Requirement 1.4, 10.7):
 * {@code javazoom:jlayer:1.0.1} contributes the MPEG Layer III decoder
 * and {@code com.googlecode.soundlibs:mp3spi:1.9.5.4} registers the
 * {@code FormatConversionProvider} that plugs JLayer into
 * {@link AudioSystem}'s {@link java.util.ServiceLoader}. All this
 * provider does is:
 *
 * <ol>
 *   <li>Decide whether an input looks like an MPEG Layer III stream, by
 *       delegating content detection to
 *       {@link Mp3HeaderScanner#scanForSyncLayer3(InputStream, int)}
 *       (Requirements 10.5, 10.6).</li>
 *   <li>Hand a recognised input to
 *       {@link AudioSystem#getAudioInputStream(java.io.File)} /
 *       {@link AudioSystem#getAudioInputStream(InputStream)} and wrap the
 *       resulting {@link AudioInputStream} in {@link Mp3AudioSource}
 *       (Requirement 10.7), translating {@link UnsupportedAudioFileException}
 *       into the {@link UnsupportedAudioFormatException} the
 *       {@code dtmf-io} error-handling contract mandates
 *       (Requirements 10.8, 12.4).</li>
 * </ol>
 *
 * <h2>Detection ({@code canOpen})</h2>
 *
 * Content-based detection (Requirement 4.4, 4.5) runs the shared
 * {@link Mp3HeaderScanner}: skip any leading ID3v2 tag, then scan up to
 * {@code 10_240} post-tag bytes for an MPEG sync word whose version
 * field is not reserved and whose layer field is Layer III. A match
 * returns a score of {@code 90}; anything else returns {@code -1}
 * (Requirements 10.5, 10.6).
 *
 * <p>The score is deliberately lower than WAV's {@code 100} because the
 * MP3 sync word is an 11-bit pattern (plus a small amount of contextual
 * validation) rather than a full magic number, and false positives on
 * pathological inputs are possible. Against a real {@code .mp3} file the
 * heuristic is robust; against random bytes a WAV-or-nothing fallback
 * through the provider chain is the right outcome.
 *
 * <h3>{@link Path} overload</h3>
 *
 * Opens a short-lived {@link BufferedInputStream} of {@code 16 KiB}
 * sitting on top of {@link Files#newInputStream(Path, java.nio.file.OpenOption...)},
 * via try-with-resources so the file handle is closed before
 * {@code canOpen} returns. The buffer size gives
 * {@link Mp3HeaderScanner} a few reads' worth of headroom over the
 * {@code 10 (ID3v2 header) + 10_240 (sync scan) = 10_250}-byte budget it
 * walks through before giving up.
 *
 * <h3>{@link InputStream} overload</h3>
 *
 * Marks the caller's stream at {@code 10_250} bytes (ID3v2 header +
 * post-tag scan budget), runs the scanner, and resets the stream in a
 * {@code finally} block so the caller's read position is restored on
 * both the success and failure paths (Requirement 4.6). Non-markable
 * streams are declined with {@code -1} without consuming any bytes
 * (Requirement 4.7); {@code AudioSources.open(InputStream, String)}
 * wraps non-markable inputs in a {@link BufferedInputStream} before
 * scoring (Requirement 5.12), so this branch mostly protects direct
 * callers of the provider.
 *
 * <h2>Full parse ({@code open})</h2>
 *
 * <h3>{@link Path} overload</h3>
 *
 * Delegates to {@link AudioSystem#getAudioInputStream(java.io.File)}
 * which, thanks to {@code mp3spi} being on the runtime classpath,
 * accepts MP3 files and returns an {@link AudioInputStream} in the
 * MP3's native format. {@link Mp3AudioSource#wrap(AudioInputStream)}
 * then converts that stream to PCM16 LE before handing the source back
 * to the caller. The returned source owns the {@link AudioInputStream}
 * and closes it on {@link AudioSource#close()}.
 *
 * <h3>{@link InputStream} overload</h3>
 *
 * Same pipeline, but on an {@link AudioInputStream} obtained from
 * {@link AudioSystem#getAudioInputStream(InputStream)}. That overload
 * requires a {@code mark}/{@code reset}-capable stream (it rewinds by a
 * few KiB while probing format), so non-markable inputs are wrapped in
 * a {@link BufferedInputStream} of {@code 16 KiB} here &mdash; the
 * wrapper is internal and therefore something the returned
 * {@link AudioSource} may close; it is <em>not</em> the caller's
 * stream. The caller's stream itself is <strong>never</strong> closed
 * by this provider or by the returned source (Requirement 4.10); closure
 * remains the caller's responsibility.
 *
 * <h2>Translation of {@code UnsupportedAudioFileException}</h2>
 *
 * When {@code mp3spi} recognises the container but cannot decode it
 * &mdash; for example an MPEG Layer I or Layer II payload, or a
 * structurally-malformed frame sequence &mdash; it throws
 * {@link UnsupportedAudioFileException}. Both {@code open(...)}
 * overloads catch that and rethrow it as
 * {@link UnsupportedAudioFormatException}, preserving the original
 * exception as the cause so the full diagnostic chain remains available
 * to the caller (Requirements 10.8, 12.4, 12.5). Real I/O failures
 * &mdash; the disk is broken, the stream is cut mid-read &mdash;
 * propagate as plain {@link IOException}, distinct from the
 * format-level failure above, so callers can handle the two cases
 * separately.
 *
 * <h2>Thread safety</h2>
 *
 * Instances are stateless; {@link #formatName()}, {@link #priority()},
 * every {@code canOpen(...)} overload, and every {@code open(...)}
 * overload can be called concurrently from multiple threads. The
 * {@link AudioSource} instances returned from {@code open(...)} carry
 * their own lifecycle and are <em>not</em> thread-safe &mdash; see
 * {@link Mp3AudioSource}.
 *
 * @since 2.0.0
 * @see Mp3AudioSource
 * @see Mp3HeaderScanner
 * @see AudioSourceProvider
 */
public final class Mp3AudioSourceProvider implements AudioSourceProvider {

    /**
     * Post-ID3v2-tag byte budget for the sync-word scan in
     * {@link Mp3HeaderScanner#scanForSyncLayer3(InputStream, int)}. Ten
     * kibibytes is the figure Requirement 10.5 prescribes and matches
     * the comment block on the scanner class.
     */
    private static final int SYNC_SCAN_BUDGET_BYTES = 10_240;

    /**
     * Read-limit used with {@link InputStream#mark(int)} in the
     * {@link InputStream} overload of {@link #canOpen(InputStream, String)}.
     * Covers the 10-byte ID3v2 header that may precede the payload plus
     * the {@link #SYNC_SCAN_BUDGET_BYTES}-byte post-tag scan budget, so
     * the underlying buffered stream can guarantee
     * {@link InputStream#reset()} succeeds regardless of how many bytes
     * the scanner actually consumed.
     */
    private static final int MARK_READ_LIMIT = 10 + SYNC_SCAN_BUDGET_BYTES;

    /**
     * Buffer size used when wrapping a file in a
     * {@link BufferedInputStream} for the {@link Path} overload of
     * {@link #canOpen(Path)} and when wrapping a non-markable caller
     * stream in {@link #open(InputStream, String)}. Sixteen kibibytes
     * gives {@link Mp3HeaderScanner} and {@code mp3spi} plenty of
     * headroom over the {@link #MARK_READ_LIMIT}-byte probe budget
     * without being so large that short-lived {@code canOpen(...)}
     * calls feel wasteful.
     */
    private static final int BUFFER_SIZE_BYTES = 16 * 1024;

    /**
     * Score returned on a successful Layer III sync-word match. The
     * MP3 sync word is an 11-bit heuristic rather than a full magic
     * number, so this sits below WAV's {@code 100} to preserve
     * tie-breaking when both providers somehow both score (which only
     * happens against pathological inputs in practice).
     */
    private static final int SCORE_MATCH = 90;

    /**
     * Prefix applied to the detail message of every
     * {@link UnsupportedAudioFormatException} this provider produces
     * from {@link #open(Path)} and {@link #open(InputStream, String)}.
     * Phrasing explains the two-phase "detection passed but decode
     * failed" contract so callers who log the message understand why
     * the open failed even though {@code canOpen} would have returned
     * a positive score.
     */
    private static final String OPEN_FAILURE_MESSAGE_PREFIX =
            "MP3 provider recognized headers but cannot decode: ";

    /**
     * {@link java.util.ServiceLoader} requires a public no-argument
     * constructor (Requirement 4.1). Instances are stateless and cheap
     * to construct; the provider caches no data across calls.
     */
    public Mp3AudioSourceProvider() {
        // no state
    }

    // ------------------------------------------------------------------
    // Identity / priority
    // ------------------------------------------------------------------

    @Override
    public String formatName() {
        // Requirement 10.3.
        return "MP3";
    }

    @Override
    public int priority() {
        // Requirement 10.4.
        return 0;
    }

    // ------------------------------------------------------------------
    // Detection: canOpen(Path)
    // ------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Opens a {@link BufferedInputStream} of
     * {@link #BUFFER_SIZE_BYTES} bytes on top of
     * {@link Files#newInputStream(Path, java.nio.file.OpenOption...)},
     * runs {@link Mp3HeaderScanner#scanForSyncLayer3(InputStream, int)}
     * with the {@link #SYNC_SCAN_BUDGET_BYTES}-byte post-tag budget, and
     * closes the stream via try-with-resources before returning
     * (Requirements 10.5, 10.6). The return value is {@link #SCORE_MATCH}
     * on a sync-word hit and {@code -1} otherwise.
     *
     * <p>Any {@link IOException} raised while opening or reading the
     * file propagates to the caller; {@code AudioSources} catches such
     * exceptions during scoring, records the provider as having returned
     * {@code -1}, logs a warning, and continues (Requirement 5.9).
     *
     * @param path file to score; must be non-null
     * @return {@link #SCORE_MATCH} on a Layer III sync-word match,
     *         {@code -1} otherwise
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws IOException          on I/O failure while reading the file
     */
    @Override
    public int canOpen(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        try (InputStream in = new BufferedInputStream(
                Files.newInputStream(path), BUFFER_SIZE_BYTES)) {
            return Mp3HeaderScanner.scanForSyncLayer3(in, SYNC_SCAN_BUDGET_BYTES)
                    ? SCORE_MATCH
                    : -1;
        }
    }

    // ------------------------------------------------------------------
    // Detection: canOpen(InputStream, String)
    // ------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>When {@code stream} supports {@code mark}/{@code reset}, marks
     * up to {@link #MARK_READ_LIMIT} bytes, runs
     * {@link Mp3HeaderScanner#scanForSyncLayer3(InputStream, int)}, and
     * resets the stream in a {@code finally} block so the caller's
     * position is restored on both the success and failure paths
     * (Requirement 4.6).
     *
     * <p>Non-markable streams are declined with {@code -1} without
     * consuming any bytes (Requirement 4.7); the {@code AudioSources}
     * facade wraps such streams in a {@link BufferedInputStream} before
     * scoring (Requirement 5.12), so in normal use this branch is
     * defensive against direct callers of the provider.
     *
     * @param stream the stream to score; must be non-null
     * @param hint   optional caller-supplied hint; may be {@code null}
     *               and is ignored by this provider (content-based
     *               detection)
     * @return {@link #SCORE_MATCH} on a Layer III sync-word match,
     *         {@code -1} otherwise
     * @throws NullPointerException if {@code stream} is {@code null}
     * @throws IOException          on I/O failure while reading the
     *                              header prefix
     */
    @Override
    public int canOpen(InputStream stream, String hint) throws IOException {
        Objects.requireNonNull(stream, "stream");
        if (!stream.markSupported()) {
            // Req 4.7: decline without consuming bytes.
            return -1;
        }
        stream.mark(MARK_READ_LIMIT);
        try {
            return Mp3HeaderScanner.scanForSyncLayer3(stream, SYNC_SCAN_BUDGET_BYTES)
                    ? SCORE_MATCH
                    : -1;
        } finally {
            stream.reset();
        }
    }

    // ------------------------------------------------------------------
    // Full parse: open(Path)
    // ------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to
     * {@link AudioSystem#getAudioInputStream(java.io.File)} which,
     * thanks to {@code mp3spi} on the runtime classpath, accepts MP3
     * files and returns an {@link AudioInputStream} in the MP3's native
     * format. {@link Mp3AudioSource#wrap(AudioInputStream)} then
     * converts that stream to PCM16 LE and returns a ready-to-read
     * source (Requirement 10.7). The returned source owns the
     * {@link AudioInputStream} and closes it on
     * {@link AudioSource#close()}.
     *
     * <p>An {@link UnsupportedAudioFileException} from
     * {@link AudioSystem} means {@code mp3spi} did not recognise the
     * file as a decodable MPEG Layer III container &mdash; in practice
     * a Layer I/II payload or a structurally malformed MP3 whose
     * sync-word prefix nonetheless convinced the scanner. That case is
     * translated into {@link UnsupportedAudioFormatException}
     * identifying the cause (Requirements 10.8, 12.4), so callers can
     * distinguish a format-level rejection from the generic
     * {@link IOException} that covers real I/O failures.
     *
     * @param path file to open; must be non-null
     * @return an opened {@link Mp3AudioSource}
     * @throws NullPointerException            if {@code path} is {@code null}
     * @throws UnsupportedAudioFormatException if the file's sync word
     *                                         matched but
     *                                         {@code mp3spi} could not
     *                                         decode it
     *                                         (Requirement 10.8)
     * @throws IOException                     on any other I/O failure
     */
    @Override
    public AudioSource open(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        try {
            AudioInputStream raw = AudioSystem.getAudioInputStream(path.toFile());
            return Mp3AudioSource.wrap(raw);
        } catch (UnsupportedAudioFileException e) {
            throw new UnsupportedAudioFormatException(
                    OPEN_FAILURE_MESSAGE_PREFIX + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // Full parse: open(InputStream, String)
    // ------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>If {@code stream} does not support {@code mark}/{@code reset}
     * it is wrapped internally in a {@link BufferedInputStream} of
     * {@link #BUFFER_SIZE_BYTES} bytes, because
     * {@link AudioSystem#getAudioInputStream(InputStream)} requires a
     * markable stream for the format-probing rewinds that
     * {@code mp3spi} performs. The wrapper is internal to this method;
     * it is <em>not</em> the caller's stream, and while the returned
     * {@link Mp3AudioSource} may legitimately close it (via the
     * {@link AudioInputStream} chain) the caller's own stream is
     * <strong>never</strong> closed by this provider or by the returned
     * source (Requirement 4.10).
     *
     * <p>An {@link UnsupportedAudioFileException} from
     * {@link AudioSystem} is translated into
     * {@link UnsupportedAudioFormatException} with the cause preserved
     * (Requirements 10.8, 12.4); real I/O failures propagate as plain
     * {@link IOException}.
     *
     * @param stream caller-supplied stream to open; must be non-null
     * @param hint   optional caller-supplied hint; may be {@code null}
     *               and is ignored by this provider
     * @return an opened {@link Mp3AudioSource}
     * @throws NullPointerException            if {@code stream} is
     *                                         {@code null}
     * @throws UnsupportedAudioFormatException if the stream's sync word
     *                                         matched but
     *                                         {@code mp3spi} could not
     *                                         decode it
     *                                         (Requirement 10.8)
     * @throws IOException                     on any other I/O failure
     */
    @Override
    public AudioSource open(InputStream stream, String hint) throws IOException {
        Objects.requireNonNull(stream, "stream");
        InputStream markable = stream.markSupported()
                ? stream
                : new BufferedInputStream(stream, BUFFER_SIZE_BYTES);
        try {
            AudioInputStream raw = AudioSystem.getAudioInputStream(markable);
            return Mp3AudioSource.wrap(raw);
        } catch (UnsupportedAudioFileException e) {
            throw new UnsupportedAudioFormatException(
                    OPEN_FAILURE_MESSAGE_PREFIX + e.getMessage(), e);
        }
    }
}
