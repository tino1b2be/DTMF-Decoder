package com.tino1b2be.dtmf.io;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Thrown when no {@link AudioSourceProvider} can open a given input, or when
 * a provider that scored non-negative on
 * {@link AudioSourceProvider#canOpen(java.nio.file.Path)} cannot in fact
 * decode the file once it opens it (structural mismatch, unsupported
 * compression code, malformed header after the magic-byte prefix, etc.).
 *
 * <p>This exception exists to distinguish "the bytes are not valid audio in
 * a recognised format" from bare {@link IOException} ("the disk is broken,"
 * "the network timed out," "the file was deleted between stat and open").
 * Callers can catch {@code UnsupportedAudioFormatException} on its own to
 * surface a helpful "unsupported format" message to end users, and let real
 * I/O failures propagate to a separate handler (Requirement 6, Requirement
 * 12.4). Because it extends {@link IOException}, code that only cares about
 * the generic I/O failure case can still catch {@code IOException} and pick
 * both up in one handler.
 *
 * <h2>Diagnostics</h2>
 *
 * When {@link AudioSources#open(java.nio.file.Path)} (or its
 * {@link java.io.InputStream}/{@link java.net.URL} overloads) throws this
 * exception because no provider was willing to open the input,
 * {@link #providersConsulted()} lists the {@link AudioSourceProvider#formatName()}
 * of every provider that was asked to score the input, in discovery order,
 * and {@link #providerScores()} maps each consulted provider's format name
 * to the SPI Priority Score it returned (Requirements 6.4, 6.5, 6.6). A
 * provider whose {@code canOpen} threw an {@link IOException} is recorded
 * with a score of {@code -1}. Providers that were not consulted (e.g.
 * because none are registered on the classpath) do not appear in either
 * collection.
 *
 * <p>When a {@link AudioSourceProvider} constructs and throws this
 * exception itself from inside {@code open(...)} — for example, the WAV
 * provider rejecting a μ-law compressed file after the magic bytes
 * matched — {@link #providersConsulted()} and {@link #providerScores()}
 * are both empty (Requirement 6.4: "empty list when no providers were
 * consulted"). Only {@link AudioSources} populates the diagnostics
 * collections; they are not part of the public constructor surface.
 *
 * <h2>Thread safety and immutability</h2>
 *
 * Instances are immutable after construction. The collections returned
 * from {@link #providersConsulted()} and {@link #providerScores()} are
 * unmodifiable views over defensively-copied snapshots, so handing a
 * caught exception to multiple consumers is safe; attempting to mutate
 * the returned collections throws {@link UnsupportedOperationException}.
 *
 * @since 2.1.0
 * @see AudioSources
 * @see AudioSourceProvider
 */
public class UnsupportedAudioFormatException extends IOException {

    private static final long serialVersionUID = 1L;

    private final List<String> providersConsulted;
    private final Map<String, Integer> providerScores;

    /**
     * Construct an {@code UnsupportedAudioFormatException} with the given
     * detail message and no cause. {@link #providersConsulted()} and
     * {@link #providerScores()} are both empty immutable collections
     * (Requirement 6.4: "empty list when no providers were consulted").
     *
     * @param message detail message; may be {@code null}
     */
    public UnsupportedAudioFormatException(String message) {
        this(message, null, List.of(), Map.of());
    }

    /**
     * Construct an {@code UnsupportedAudioFormatException} wrapping an
     * underlying cause. {@link #providersConsulted()} and
     * {@link #providerScores()} are both empty immutable collections.
     *
     * @param message detail message; may be {@code null}
     * @param cause   underlying cause; may be {@code null}
     */
    public UnsupportedAudioFormatException(String message, Throwable cause) {
        this(message, cause, List.of(), Map.of());
    }

    /**
     * Package-private constructor used exclusively by {@link AudioSources}
     * to populate the diagnostics collections when no provider was able to
     * open the input (Requirement 6.6). The given collections are
     * defensively copied via {@link List#copyOf(java.util.Collection)} and
     * {@link Map#copyOf(Map)} so the exception is immutable once
     * constructed; callers cannot mutate the diagnostics after the throw.
     *
     * @param message             detail message; may be {@code null}
     * @param cause               underlying cause; may be {@code null}
     * @param providersConsulted  format names of every provider that was
     *                            asked to score the input, in discovery
     *                            order; must be non-null and contain no
     *                            {@code null} elements
     * @param providerScores      scores returned by each consulted provider,
     *                            keyed by {@code formatName()}; must be
     *                            non-null and contain no {@code null} keys
     *                            or values
     */
    UnsupportedAudioFormatException(
            String message,
            Throwable cause,
            List<String> providersConsulted,
            Map<String, Integer> providerScores) {
        super(message, cause);
        this.providersConsulted = List.copyOf(providersConsulted);
        this.providerScores = Map.copyOf(providerScores);
    }

    /**
     * Format names of every {@link AudioSourceProvider} that was asked to
     * score the input, in {@code ServiceLoader} discovery order
     * (Requirement 6.4).
     *
     * <p>Returns an empty list when this exception was constructed via one
     * of the public constructors (e.g. thrown from inside a provider's
     * {@code open(...)} method) rather than by {@link AudioSources}.
     *
     * @return immutable list of consulted provider format names; never
     *         {@code null}
     */
    public List<String> providersConsulted() {
        return providersConsulted;
    }

    /**
     * Score returned by each consulted {@link AudioSourceProvider}, keyed
     * by {@code formatName()} (Requirement 6.5).
     *
     * <p>A provider whose {@code canOpen} threw an {@link IOException} is
     * recorded here with a value of {@code -1} (the score
     * {@link AudioSources} assigns to any failing or not-applicable
     * provider). Returns an empty map when this exception was not
     * constructed by {@link AudioSources}.
     *
     * @return immutable map of provider format name to SPI Priority Score;
     *         never {@code null}
     */
    public Map<String, Integer> providerScores() {
        return providerScores;
    }
}
