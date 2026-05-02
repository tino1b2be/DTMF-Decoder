package com.tino1b2be.dtmf.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Service Provider Interface (SPI) for format-specific audio decoders.
 * Each format module (for example {@code dtmf-io-wav},
 * {@code dtmf-io-mp3}, or a future FLAC/OGG module) ships exactly one
 * implementation of this interface and registers it via
 * {@code META-INF/services/com.tino1b2be.dtmf.io.AudioSourceProvider}
 * (Requirement 9.2, 10.2). The {@link AudioSources} facade discovers every
 * registered provider through {@link java.util.ServiceLoader}, asks each
 * one to score the input with {@link #canOpen(Path)} or
 * {@link #canOpen(InputStream, String)}, and dispatches
 * {@link #open(Path)} or {@link #open(InputStream, String)} on the
 * provider with the strictly greatest score (Requirement 5.6).
 *
 * <h2>Implementation requirements</h2>
 *
 * <p><strong>Public no-arg constructor.</strong> Implementations must
 * expose a public no-argument constructor so that
 * {@link java.util.ServiceLoader} can instantiate them reflectively.
 * Providers with no state are fine; stateful providers must make their
 * own-state instantiation cheap, because {@code ServiceLoader} creates
 * one instance per classloader and caches it for the lifetime of the
 * loader.
 *
 * <p><strong>Thread safety.</strong> Individual provider instances may
 * be called concurrently by {@link AudioSources} from multiple threads.
 * Implementations should keep {@link #canOpen(Path)},
 * {@link #canOpen(InputStream, String)}, {@link #open(Path)}, and
 * {@link #open(InputStream, String)} stateless (allocating fresh
 * {@link java.nio.channels.FileChannel} or
 * {@link java.io.InputStream} objects per call). Returned
 * {@link AudioSource} instances are <em>not</em> required to be
 * thread-safe — see {@link AudioSource} for that contract.
 *
 * <h2>SPI Priority Score</h2>
 *
 * <p>The score returned by {@code canOpen(...)} is an integer in the
 * closed range {@code [0, 100]} or the sentinel value {@code -1}
 * (Requirement 4.4, 4.5). Higher scores indicate stronger confidence
 * that this provider can open the input:
 * <ul>
 *   <li>{@code 100} — the input's magic bytes match unambiguously
 *       (for example {@code "RIFF" ... "WAVE"} at offset 0 for WAV).</li>
 *   <li>{@code 0}{@literal –}{@code 99} — a weaker signal, typically
 *       because the provider's identifying pattern is heuristic rather
 *       than magic (for example an MPEG audio frame sync pattern).</li>
 *   <li>{@code -1} — the provider is not applicable to this input and
 *       must not be selected, even if it is the only provider on the
 *       classpath. A {@code -1} return is also how a provider declines
 *       when asked about a non-markable stream it cannot read ahead on
 *       (see {@link #canOpen(InputStream, String)} below).</li>
 * </ul>
 *
 * <p>When two providers return the same positive score,
 * {@link AudioSources} breaks the tie with {@link #priority()} (higher
 * wins; Requirement 4.3).
 *
 * <h2>Stream ownership</h2>
 *
 * <p>{@link #open(Path)} and {@link #open(InputStream, String)} never
 * close caller-supplied {@link InputStream}s (Requirement 4.10). The
 * {@link AudioSource#close()} method on the returned source closes only
 * the resources the provider opened itself — for example a
 * {@link java.nio.channels.FileChannel} the provider opened on a
 * {@link Path}, or a {@link java.io.BufferedInputStream} the provider
 * wrapped internally around a caller stream. When the caller supplies
 * the {@code InputStream}, closing the returned {@link AudioSource} must
 * <em>not</em> close that caller stream; ownership stays with whoever
 * opened it.
 *
 * <h2>Null parameters</h2>
 *
 * <p>Every parameter of every SPI method is non-null unless explicitly
 * documented otherwise. Implementations must throw
 * {@link NullPointerException} identifying the offending parameter when
 * a non-null argument is {@code null} (Requirement 4.11). The
 * {@code hint} parameter on {@link #canOpen(InputStream, String)} and
 * {@link #open(InputStream, String)} is the one exception: it is
 * explicitly nullable and implementations must tolerate {@code null}
 * without throwing.
 *
 * @since 2.0.0
 */
public interface AudioSourceProvider {

    /**
     * Human-readable identifier for this provider. Returned values must
     * be non-null and non-empty (Requirement 4.2). Typical values are
     * short uppercase tags such as {@code "WAV"} or {@code "MP3"};
     * {@link AudioSources#registeredFormats()} exposes the list of every
     * registered provider's name in discovery order, and
     * {@link UnsupportedAudioFormatException#providerScores()} keys its
     * score map on this value, so the returned string should be stable
     * across JVM runs and unique across the providers a caller expects
     * to have on the classpath at the same time.
     *
     * @return this provider's format name; never {@code null}, never empty
     */
    String formatName();

    /**
     * Tie-breaker priority used when two providers return the same
     * positive score from {@link #canOpen(Path)} or
     * {@link #canOpen(InputStream, String)} (Requirement 4.3). Higher
     * values win. The default implementation returns {@code 0}, which
     * is the right answer for every built-in provider; a format module
     * only overrides this when it ships two providers for overlapping
     * inputs and wants to express a preferred order.
     *
     * @return this provider's tie-break priority
     */
    default int priority() {
        return 0;
    }

    /**
     * Score this provider's confidence that it can open {@code path}
     * (Requirement 4.4). Implementations typically open a
     * {@link java.nio.channels.FileChannel} or a short-lived
     * {@link InputStream} on the file, read a small prefix (the WAV
     * provider reads 12 bytes; the MP3 provider reads up to 10 KiB after
     * any ID3v2 tag), score against that prefix, and close the channel
     * or stream before returning. The file itself is not opened for
     * reading beyond the prefix — that happens in {@link #open(Path)}.
     *
     * <p>The returned value is an <em>SPI Priority Score</em>: an
     * integer in {@code [0, 100]} or {@code -1} (see the class-level
     * "SPI Priority Score" section). Implementations must not return
     * any other value; {@link AudioSources} does not validate the
     * score range and out-of-range scores will break tie-breaking.
     *
     * <p>If the file cannot be read at all (for example a
     * {@link java.nio.file.NoSuchFileException}, a permission error, or
     * a disk-level I/O failure), this method propagates
     * {@link IOException}. The {@link AudioSources} facade catches those
     * exceptions, treats the provider as having returned {@code -1},
     * logs a warning, and continues scoring the remaining providers
     * (Requirement 5.9) — so implementations do not need to defend
     * against "the file is unreadable" themselves.
     *
     * @param path absolute or relative path to the file to score; must be non-null
     * @return an SPI Priority Score in {@code [0, 100]}, or {@code -1} if this provider is not applicable
     * @throws IOException              if the file cannot be read to score it
     * @throws NullPointerException     if {@code path} is {@code null}
     */
    int canOpen(Path path) throws IOException;

    /**
     * Score this provider's confidence that it can open {@code stream}
     * (Requirement 4.5). The {@code hint} parameter is an optional
     * caller-supplied file name, URL path segment, MIME type, or
     * {@code null}; providers that cannot read ahead on a non-markable
     * stream may use the hint as a fallback signal, but content-based
     * scoring always takes precedence over any hint on a markable
     * stream.
     *
     * <p><strong>Mark/reset contract.</strong> When {@code stream}
     * supports {@code mark}/{@code reset}, implementations call
     * {@link InputStream#mark(int) stream.mark(readLimit)}, read a
     * header prefix, score against that prefix, and call
     * {@link InputStream#reset() stream.reset()} before returning —
     * leaving the stream positioned exactly as it was on entry so that
     * {@link AudioSources} can pass the same stream to subsequent
     * providers and eventually to {@link #open(InputStream, String)}
     * (Requirement 4.6).
     *
     * <p><strong>Non-markable streams.</strong> If
     * {@link InputStream#markSupported() stream.markSupported()} is
     * {@code false}, implementations return {@code -1} without
     * consuming any bytes (Requirement 4.7). Reading from a
     * non-markable stream would leave it in a consumed state that
     * neither the caller nor any subsequent provider can recover from.
     * In practice {@link AudioSources#open(InputStream, String)}
     * guarantees the stream it forwards is always markable by wrapping
     * non-markable inputs in a {@link java.io.BufferedInputStream}
     * sized for at least 16 KiB of header inspection
     * (Requirement 5.12), so providers see a markable stream every time
     * they are called through the facade; this branch exists for
     * direct callers who invoke a provider without going through
     * {@link AudioSources}.
     *
     * <p>The returned value is an <em>SPI Priority Score</em>: an
     * integer in {@code [0, 100]} or {@code -1} — see
     * {@link #canOpen(Path)} and the class-level "SPI Priority Score"
     * section.
     *
     * @param stream the stream to score; must be non-null
     * @param hint   an optional caller-supplied hint (file name, URL path segment, MIME type); may be {@code null}
     * @return an SPI Priority Score in {@code [0, 100]}, or {@code -1} if this provider is not applicable
     * @throws IOException          on I/O failure while reading the header prefix
     * @throws NullPointerException if {@code stream} is {@code null}
     */
    int canOpen(InputStream stream, String hint) throws IOException;

    /**
     * Open an {@link AudioSource} for {@code path} (Requirement 4.8).
     * Callers typically reach this method indirectly through
     * {@link AudioSources#open(Path)}, which first picks the winning
     * provider by scoring and then delegates to its {@code open(...)}
     * — but providers may be invoked directly when a caller already
     * knows which format they have.
     *
     * <p>The returned {@link AudioSource} owns the underlying file
     * handle or stream the provider opened on {@code path}:
     * {@link AudioSource#close()} closes that handle and releases any
     * decoder resources. Caller-supplied streams are never involved on
     * this overload, so there is nothing for the provider to avoid
     * closing.
     *
     * <p>A non-negative score from {@link #canOpen(Path)} does not
     * guarantee {@code open(Path)} will succeed: the header prefix may
     * match but a later structural defect (for example a WAV
     * {@code fmt } chunk declaring a compressed encoding such as
     * {@code μ}-law; Requirement 9.10) only surfaces during the full
     * parse. In that case implementations throw
     * {@link UnsupportedAudioFormatException} identifying the defect;
     * real I/O failures propagate as plain {@link IOException}.
     *
     * @param path file to open; must be non-null
     * @return an opened {@link AudioSource}
     * @throws UnsupportedAudioFormatException if the file's header matched but the full parse rejected it
     * @throws IOException                     on any other I/O failure
     * @throws NullPointerException            if {@code path} is {@code null}
     */
    AudioSource open(Path path) throws IOException;

    /**
     * Open an {@link AudioSource} for a markable {@code InputStream}
     * (Requirement 4.9). The {@code hint} parameter has the same
     * semantics as on {@link #canOpen(InputStream, String)}: file name,
     * URL path segment, MIME type, or {@code null}.
     *
     * <p>This overload never closes {@code stream}. Ownership stays
     * with the caller (Requirement 4.10); the returned
     * {@link AudioSource}'s {@link AudioSource#close()} only closes
     * resources the provider opened internally (for example a
     * {@link java.io.BufferedInputStream} wrapper, decoder buffers, or
     * a spawned worker). If the caller needs the underlying stream
     * closed, they must close it themselves after closing the
     * {@link AudioSource}.
     *
     * <p>As with {@link #open(Path)}, a prior non-negative
     * {@link #canOpen(InputStream, String)} score does not guarantee
     * success here: structural defects past the header surface as
     * {@link UnsupportedAudioFormatException} during the full parse.
     *
     * @param stream markable stream to open; must be non-null
     * @param hint   an optional caller-supplied hint; may be {@code null}
     * @return an opened {@link AudioSource}
     * @throws UnsupportedAudioFormatException if the stream's header matched but the full parse rejected it
     * @throws IOException                     on any other I/O failure
     * @throws NullPointerException            if {@code stream} is {@code null}
     */
    AudioSource open(InputStream stream, String hint) throws IOException;
}
