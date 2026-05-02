package com.tino1b2be.dtmf.io;

import com.tino1b2be.dtmf.io.internal.ProviderScore;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point for opening audio from any source. Discovers
 * {@link AudioSourceProvider} instances via {@link java.util.ServiceLoader},
 * runs content-based scoring against every registered provider, and returns
 * an {@link AudioSource} produced by the provider with the strictly greatest
 * SPI Priority Score. Ties on score are broken by {@link
 * AudioSourceProvider#priority() priority()} (Requirement 5.6).
 *
 * <p>{@code AudioSources} is the {@code dtmf-io} module's façade over the
 * provider SPI: callers hand in a {@link Path}, a markable
 * {@link InputStream} (plus optional file-name / URL-segment / MIME-type
 * hint), or a {@link URL}, and get back an {@link AudioSource} without
 * having to know which format module is on the classpath. Non-markable
 * streams are transparently wrapped in a {@link BufferedInputStream} sized
 * to {@value #HEADER_BUFFER_BYTES} bytes before scoring, so providers always
 * see a markable stream they can rewind after reading the header prefix
 * (Requirement 5.12, 11.2).
 *
 * <h2>Dispatch</h2>
 *
 * <p>For every public {@code open(...)} call, the facade:
 * <ol>
 *   <li>Discovers providers via {@link ServiceLoader#load(Class, ClassLoader)
 *       ServiceLoader.load(AudioSourceProvider.class, loader)} on the
 *       context class loader (falling back to {@code AudioSources}'s own
 *       class loader when the context loader is {@code null}), caching the
 *       result for the lifetime of the JVM (Requirement 5.5).</li>
 *   <li>Invokes {@code canOpen(...)} on every cached provider, catching
 *       {@link IOException} and logging it at {@link Level#WARNING WARNING}
 *       before treating the provider as having returned {@code -1}
 *       (Requirement 5.9).</li>
 *   <li>Picks the winner as the provider with the strictly greatest
 *       {@code (score, priority())} lexicographic pair over all scores
 *       {@code >= 0} (Requirement 5.6).</li>
 *   <li>Delegates the actual open to the winner's {@code open(...)} method.</li>
 * </ol>
 *
 * <h2>Error handling</h2>
 *
 * <p>If every registered provider returns {@code -1} (including the case
 * where each one's {@code canOpen} threw an {@link IOException}), this
 * facade throws {@link UnsupportedAudioFormatException} with
 * {@link UnsupportedAudioFormatException#providersConsulted()} and
 * {@link UnsupportedAudioFormatException#providerScores()} populated from
 * the scoring loop (Requirements 5.7, 6.4, 6.5, 6.6). If no providers are
 * registered at all, the thrown exception carries a distinct message
 * pointing the caller at the {@code dtmf-io-wav} / {@code dtmf-io-mp3}
 * modules (Requirement 5.8).
 *
 * <p><strong>File-not-found special case.</strong> On {@link #open(Path)},
 * if <em>every</em> provider returned {@code -1} <em>because</em> each
 * one's {@code canOpen(Path)} threw an {@link IOException}, the facade
 * re-throws the first captured {@link IOException} instead of throwing
 * {@link UnsupportedAudioFormatException}. This preserves {@link
 * java.nio.file.NoSuchFileException} (and its siblings — permission
 * errors, access-denied, etc.) as distinct error signals rather than
 * masquerading as "format not supported" (Requirement 12.3, 12.4). The
 * special case does not apply to {@link #open(InputStream, String)}
 * because an {@code IOException} from {@code canOpen(InputStream, ...)}
 * is a read-failure during header inspection, not a missing-source
 * signal.
 *
 * <h2>Thread safety</h2>
 *
 * <p>{@code AudioSources} is thread-safe. The provider cache is a
 * {@code volatile List<AudioSourceProvider>} field populated under
 * double-checked lazy initialization guarded by the class monitor; the
 * {@link ServiceLoader} iterator — which is not itself thread-safe — is
 * consumed exclusively inside the synchronized block. The cache is
 * computed once per JVM and returned immutably for every subsequent
 * call. Provider instances themselves may be called concurrently from
 * multiple threads; see {@link AudioSourceProvider} for that contract.
 *
 * @since 2.0.0
 * @see AudioSource
 * @see AudioSourceProvider
 * @see UnsupportedAudioFormatException
 */
public final class AudioSources {

    private static final Logger LOG = Logger.getLogger(AudioSources.class.getName());

    /**
     * Minimum buffer size (in bytes) used to wrap non-markable streams
     * before scoring (Requirement 5.12, 11.2). Providers can safely
     * {@code mark()} up to this many bytes, read a header prefix, and
     * {@code reset()} back to the stream's original position.
     */
    private static final int HEADER_BUFFER_BYTES = 16 * 1024;

    /**
     * Lazy cache of {@link ServiceLoader} discovery results. Accessed via
     * double-checked lazy initialization through {@link #providers()}; see
     * the Thread Safety section of the class Javadoc.
     */
    private static volatile List<AudioSourceProvider> cachedProviders;

    private AudioSources() {
        // Non-instantiable facade.
    }

    /**
     * Open an {@link AudioSource} for the file at {@code path} by scoring
     * every registered {@link AudioSourceProvider} against it and
     * dispatching to the winner (Requirement 5.2).
     *
     * <p>See the class Javadoc for the dispatch algorithm and error
     * handling — including the special case where the sole error signal
     * from every provider is an {@link IOException} (the first such
     * exception is re-thrown verbatim to preserve
     * {@link java.nio.file.NoSuchFileException} and permission errors).
     *
     * @param path the file to open; must be non-null
     * @return an opened {@link AudioSource} produced by the winning provider
     * @throws UnsupportedAudioFormatException if no provider is applicable,
     *         or if no providers are registered at all
     * @throws IOException                     on any other I/O failure,
     *         including the pass-through of a captured
     *         {@link IOException} when every provider rejected
     *         {@code path} by throwing one
     * @throws NullPointerException            if {@code path} is {@code null}
     */
    public static AudioSource open(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        return openWithProviders(path, providers());
    }

    /**
     * Open an {@link AudioSource} for the given {@link InputStream},
     * scoring every registered provider against it and dispatching to the
     * winner (Requirement 5.3).
     *
     * <p>The optional {@code hint} argument — a file name, URL path
     * segment, or MIME type — is forwarded verbatim to every provider's
     * {@link AudioSourceProvider#canOpen(InputStream, String) canOpen}
     * and (on dispatch) {@link AudioSourceProvider#open(InputStream, String)
     * open} methods. Providers may use it as a fallback signal when
     * content-based detection is ambiguous or when the stream is not
     * markable; content-based scoring always takes precedence on a
     * markable stream.
     *
     * <p>If {@code stream} does not support
     * {@link InputStream#mark(int) mark}/{@link InputStream#reset() reset},
     * it is wrapped in a {@link BufferedInputStream} sized to at least
     * {@value #HEADER_BUFFER_BYTES} bytes before being forwarded to
     * providers (Requirement 5.12, 11.2). Providers can therefore always
     * assume the stream they receive is markable.
     *
     * <p>The returned {@link AudioSource} does <em>not</em> close
     * {@code stream} when its own {@link AudioSource#close()} is invoked;
     * ownership of the caller-supplied stream stays with the caller
     * (Requirement 4.10). If the facade wrapped the stream in a
     * {@link BufferedInputStream}, that wrapper is also not closed on the
     * caller's behalf.
     *
     * @param stream markable (or wrappable) stream to open; must be non-null
     * @param hint   optional caller-supplied hint (file name, URL path
     *               segment, or MIME type); may be {@code null}
     * @return an opened {@link AudioSource} produced by the winning provider
     * @throws UnsupportedAudioFormatException if no provider is applicable,
     *         or if no providers are registered at all
     * @throws IOException                     on any other I/O failure
     * @throws NullPointerException            if {@code stream} is {@code null}
     */
    public static AudioSource open(InputStream stream, String hint) throws IOException {
        Objects.requireNonNull(stream, "stream");
        InputStream markable = stream.markSupported()
                ? stream
                : new BufferedInputStream(stream, HEADER_BUFFER_BYTES);
        return openWithProviders(markable, hint, providers());
    }

    /**
     * Open an {@link AudioSource} for the resource at {@code url}. Opens
     * the URL via {@link URL#openStream() url.openStream()}, derives the
     * hint from the last {@code '/'}-separated segment of
     * {@link URL#getPath() url.getPath()}, and delegates to
     * {@link #open(InputStream, String)} (Requirement 5.4, 11.1).
     *
     * <p>If {@link #open(InputStream, String)} throws, the stream opened
     * by {@code url.openStream()} is closed before the exception
     * propagates so the underlying URL connection does not leak
     * (Requirement 11.4). On a successful return, the stream's lifecycle
     * is governed by the returned {@link AudioSource}: the caller should
     * close the {@code AudioSource} to release the connection.
     *
     * @param url the URL to open; must be non-null
     * @return an opened {@link AudioSource} produced by the winning provider
     * @throws UnsupportedAudioFormatException if no provider is applicable,
     *         or if no providers are registered at all
     * @throws IOException                     on any other I/O failure,
     *         including {@link URL#openStream()} failing to connect
     * @throws NullPointerException            if {@code url} is {@code null}
     */
    public static AudioSource open(URL url) throws IOException {
        Objects.requireNonNull(url, "url");
        String hint = hintFromUrl(url);
        // We own this stream until we successfully hand it off to a provider
        // through open(InputStream, String). Close it on exception so the
        // URL connection does not leak (Req 11.4).
        InputStream raw = url.openStream();
        try {
            return open(raw, hint);
        } catch (IOException | RuntimeException | Error ex) {
            try {
                raw.close();
            } catch (IOException closeEx) {
                ex.addSuppressed(closeEx);
            }
            throw ex;
        }
    }

    /**
     * Names of every loaded {@link AudioSourceProvider} in
     * {@link ServiceLoader} discovery order (Requirement 5.11). Provider
     * discovery is cached, so repeated calls return the same list.
     *
     * @return immutable list of provider format names in discovery order
     */
    public static List<String> registeredFormats() {
        return providers().stream()
                .map(AudioSourceProvider::formatName)
                .toList();
    }

    // ---------------------------------------------------------------------
    // Package-private test seams
    // ---------------------------------------------------------------------
    //
    // These seams exist so unit tests in the same package can exercise the
    // scoring / dispatch logic against a hand-injected list of providers
    // without depending on the JVM-wide `ServiceLoader` state. Integration
    // tests in `src/integrationTest/java` exercise the real discovery path
    // end-to-end; these seams cover the negative cases (empty list,
    // throwing `canOpen`, tie-break) that are expensive to reproduce with
    // real providers.
    //
    // Scope is deliberately narrow: the seams accept exactly the same
    // {@code List<AudioSourceProvider>} the scoring loop consumes and
    // return whatever {@code openWithProviders} would have produced — no
    // extra behaviour is added here.

    /**
     * Test-only entry point: dispatch {@link #open(Path)} scoring against
     * the caller-supplied provider list instead of the cached
     * {@code ServiceLoader} results. Package-private so only same-package
     * unit tests can reach it.
     *
     * @param path      the file to open
     * @param providers the providers to score against (discovery-order list)
     * @return the winning provider's {@link AudioSource}
     */
    static AudioSource openForTesting(Path path, List<AudioSourceProvider> providers)
            throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(providers, "providers");
        return openWithProviders(path, providers);
    }

    /**
     * Test-only entry point: dispatch {@link #open(InputStream, String)}
     * scoring against the caller-supplied provider list. Wraps
     * non-markable streams the same way the production entry point does
     * (Requirement 5.12, 11.2) so the wrapping behaviour is exercisable
     * without having to route through {@code ServiceLoader}.
     *
     * @param stream    the stream to open
     * @param hint      optional filename / path segment / MIME-type hint
     * @param providers the providers to score against (discovery-order list)
     * @return the winning provider's {@link AudioSource}
     */
    static AudioSource openForTesting(
            InputStream stream, String hint, List<AudioSourceProvider> providers)
            throws IOException {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(providers, "providers");
        InputStream markable = stream.markSupported()
                ? stream
                : new BufferedInputStream(stream, HEADER_BUFFER_BYTES);
        return openWithProviders(markable, hint, providers);
    }

    /**
     * Test-only entry point: dispatch {@link #open(URL)} scoring against
     * the caller-supplied provider list. Mirrors the production method
     * exactly — opens the URL stream, derives the hint, delegates to
     * {@link #openForTesting(InputStream, String, List)}, closes the URL
     * stream on exception (Requirement 11.4).
     *
     * @param url       the URL to open
     * @param providers the providers to score against (discovery-order list)
     * @return the winning provider's {@link AudioSource}
     */
    static AudioSource openForTesting(URL url, List<AudioSourceProvider> providers)
            throws IOException {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(providers, "providers");
        String hint = hintFromUrl(url);
        InputStream raw = url.openStream();
        try {
            return openForTesting(raw, hint, providers);
        } catch (IOException | RuntimeException | Error ex) {
            try {
                raw.close();
            } catch (IOException closeEx) {
                ex.addSuppressed(closeEx);
            }
            throw ex;
        }
    }

    /**
     * Test-only entry point: snapshot {@link #registeredFormats()} against
     * a caller-supplied provider list instead of the cached
     * {@code ServiceLoader} result. Preserves the discovery-order
     * semantics of the production method (Requirement 5.11).
     *
     * @param providers the providers to enumerate (discovery-order list)
     * @return format names in the list's iteration order
     */
    static List<String> registeredFormatsForTesting(List<AudioSourceProvider> providers) {
        Objects.requireNonNull(providers, "providers");
        return providers.stream()
                .map(AudioSourceProvider::formatName)
                .toList();
    }

    // ---------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------

    /**
     * Accessor for the provider cache implementing the double-checked lazy
     * initialization idiom described in the Thread Safety section of the
     * class Javadoc. The double-read guards against publication races on
     * the {@code volatile} field; the synchronized block guards {@link
     * ServiceLoader}'s non-thread-safe iterator.
     */
    private static List<AudioSourceProvider> providers() {
        List<AudioSourceProvider> cache = cachedProviders;
        if (cache == null) {
            synchronized (AudioSources.class) {
                cache = cachedProviders;
                if (cache == null) {
                    cache = discoverProviders();
                    cachedProviders = cache;
                }
            }
        }
        return cache;
    }

    /**
     * Run {@link ServiceLoader} discovery exactly once, catching and
     * logging {@link ServiceConfigurationError} per misconfigured service
     * entry so a single broken provider does not prevent the rest of the
     * classpath from loading (Requirement 5.10). Called while holding the
     * {@link AudioSources} class monitor.
     */
    private static List<AudioSourceProvider> discoverProviders() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = AudioSources.class.getClassLoader();
        }
        List<AudioSourceProvider> result = new ArrayList<>();
        Iterator<AudioSourceProvider> it =
                ServiceLoader.load(AudioSourceProvider.class, loader).iterator();
        // Use hasNext()/next() in a try/catch to tolerate per-entry
        // ServiceConfigurationError without aborting the whole iteration.
        while (hasNextSafely(it)) {
            try {
                AudioSourceProvider provider = it.next();
                result.add(provider);
            } catch (ServiceConfigurationError err) {
                LOG.log(Level.WARNING, "AudioSourceProvider failed to load", err);
            }
        }
        return List.copyOf(result);
    }

    /**
     * {@link Iterator#hasNext()} on the {@link ServiceLoader} iterator
     * itself can throw {@link ServiceConfigurationError} when a service
     * file entry names a class that cannot be resolved. Wrap that case the
     * same way we wrap {@code next()} so one broken entry does not end
     * discovery early.
     */
    private static boolean hasNextSafely(Iterator<AudioSourceProvider> it) {
        while (true) {
            try {
                return it.hasNext();
            } catch (ServiceConfigurationError err) {
                LOG.log(Level.WARNING, "AudioSourceProvider failed to load", err);
                // Loop and ask again — the iterator will advance past the
                // broken entry on the next call.
            }
        }
    }

    /**
     * Scoring loop for the {@link Path} overload. Captures the first
     * {@link IOException} thrown from any provider's {@code canOpen}: if
     * every provider ultimately returns {@code -1} <em>because</em> of an
     * {@link IOException}, the facade re-throws that first captured
     * exception instead of throwing {@link UnsupportedAudioFormatException}
     * so {@link java.nio.file.NoSuchFileException} and permission errors
     * propagate cleanly (class Javadoc: "File-not-found special case").
     */
    private static AudioSource openWithProviders(
            Path path,
            List<AudioSourceProvider> providers) throws IOException {
        if (providers.isEmpty()) {
            throw noProvidersRegistered();
        }
        Map<String, Integer> scores = new LinkedHashMap<>();
        List<ProviderScore> eligible = new ArrayList<>();
        IOException firstCapturedIoException = null;
        for (AudioSourceProvider provider : providers) {
            int score;
            try {
                score = provider.canOpen(path);
            } catch (IOException ex) {
                LOG.log(Level.WARNING,
                        () -> provider.formatName() + " canOpen(Path) threw");
                LOG.log(Level.WARNING, "canOpen(Path) exception detail", ex);
                score = -1;
                if (firstCapturedIoException == null) {
                    firstCapturedIoException = ex;
                }
            }
            scores.put(provider.formatName(), score);
            if (score >= 0) {
                eligible.add(new ProviderScore(provider, score, provider.priority()));
            }
        }
        if (eligible.isEmpty()) {
            // Special case: every "no" was an IOException. Preserve the
            // caller's mental model of file-not-found vs format-not-supported
            // by re-throwing the first captured exception verbatim.
            if (firstCapturedIoException != null) {
                throw firstCapturedIoException;
            }
            throw noApplicableProvider(providers, scores);
        }
        ProviderScore winner = Collections.max(eligible, ProviderScore.BY_SCORE_THEN_PRIORITY);
        return winner.provider().open(path);
    }

    /**
     * Scoring loop for the {@link InputStream} overload. Mirrors the
     * {@link Path} loop except the file-not-found special case does not
     * apply (an {@link IOException} here is a read failure during header
     * inspection, not a missing-source signal, and the caller already owns
     * the stream).
     */
    private static AudioSource openWithProviders(
            InputStream stream,
            String hint,
            List<AudioSourceProvider> providers) throws IOException {
        if (providers.isEmpty()) {
            throw noProvidersRegistered();
        }
        Map<String, Integer> scores = new LinkedHashMap<>();
        List<ProviderScore> eligible = new ArrayList<>();
        for (AudioSourceProvider provider : providers) {
            int score;
            try {
                score = provider.canOpen(stream, hint);
            } catch (IOException ex) {
                LOG.log(Level.WARNING,
                        () -> provider.formatName() + " canOpen(InputStream) threw");
                LOG.log(Level.WARNING, "canOpen(InputStream) exception detail", ex);
                score = -1;
            }
            scores.put(provider.formatName(), score);
            if (score >= 0) {
                eligible.add(new ProviderScore(provider, score, provider.priority()));
            }
        }
        if (eligible.isEmpty()) {
            throw noApplicableProvider(providers, scores);
        }
        ProviderScore winner = Collections.max(eligible, ProviderScore.BY_SCORE_THEN_PRIORITY);
        return winner.provider().open(stream, hint);
    }

    /**
     * Derive the hint for {@link #open(URL)} from the last {@code '/'}
     * segment of the URL's path (Requirement 11.1). Returns {@code null}
     * for URLs whose path is empty; returns the full path for URLs whose
     * path has no {@code '/'} or ends in one (so the hint is never empty
     * when a non-empty path was present).
     */
    private static String hintFromUrl(URL url) {
        String path = url.getPath();
        if (path == null || path.isEmpty()) {
            return null;
        }
        int slash = path.lastIndexOf('/');
        if (slash < 0) {
            return path;
        }
        if (slash == path.length() - 1) {
            // Path ends in '/': no last-segment filename available; fall
            // back to the whole path so providers that inspect the hint
            // still get *something* non-null to work with.
            return path;
        }
        return path.substring(slash + 1);
    }

    // ---------------------------------------------------------------------
    // Exception factories
    // ---------------------------------------------------------------------

    private static UnsupportedAudioFormatException noProvidersRegistered() {
        String message =
                "No AudioSourceProvider implementations are registered on the classpath. "
                        + "Add dtmf-io-wav, dtmf-io-mp3, or another provider module to enable decoding.";
        return new UnsupportedAudioFormatException(
                message, null, List.of(), Map.of());
    }

    private static UnsupportedAudioFormatException noApplicableProvider(
            List<AudioSourceProvider> providers,
            Map<String, Integer> scores) {
        List<String> consulted = new ArrayList<>(providers.size());
        for (AudioSourceProvider provider : providers) {
            consulted.add(provider.formatName());
        }
        StringBuilder sb = new StringBuilder(
                "No AudioSourceProvider could open the input. Consulted providers:");
        for (String name : consulted) {
            Integer score = scores.get(name);
            sb.append(System.lineSeparator())
                    .append("  - ")
                    .append(name)
                    .append(" (score: ")
                    .append(score)
                    .append(")");
        }
        return new UnsupportedAudioFormatException(
                sb.toString(), null, consulted, scores);
    }
}
