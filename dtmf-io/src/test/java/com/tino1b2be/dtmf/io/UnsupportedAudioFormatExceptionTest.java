package com.tino1b2be.dtmf.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link UnsupportedAudioFormatException} (Requirements 6.1,
 * 6.2, 6.3).
 *
 * <p>Lives in the {@code com.tino1b2be.dtmf.io} package so it can reach the
 * package-private four-argument constructor that {@code AudioSources} uses
 * to populate the diagnostics collections; the diagnostic-copy tests
 * exercise that constructor directly rather than routing through
 * {@code AudioSources} (which does not exist yet at this stage of the
 * spec).
 */
class UnsupportedAudioFormatExceptionTest {

    // ---------------------------------------------------------------------
    // Requirement 6.2 — public (String) constructor
    // ---------------------------------------------------------------------

    /**
     * The public {@code (String)} constructor SHALL leave
     * {@link UnsupportedAudioFormatException#providersConsulted()} and
     * {@link UnsupportedAudioFormatException#providerScores()} as empty
     * immutable collections (Requirement 6.2, Requirement 6.4 "empty list
     * when no providers were consulted").
     */
    @Test
    void stringConstructorYieldsEmptyImmutableDiagnostics() {
        UnsupportedAudioFormatException ex =
                new UnsupportedAudioFormatException("no provider recognised the input");

        assertEquals("no provider recognised the input", ex.getMessage(),
                "Detail message should pass through to IOException.getMessage()");
        assertTrue(ex.providersConsulted().isEmpty(),
                "providersConsulted() should be empty after (String) construction");
        assertTrue(ex.providerScores().isEmpty(),
                "providerScores() should be empty after (String) construction");

        // Immutability — the public contract says these collections are
        // unmodifiable views; every mutating call must throw.
        assertThrows(UnsupportedOperationException.class,
                () -> ex.providersConsulted().add("wav"),
                "providersConsulted() must be immutable after (String) construction");
        assertThrows(UnsupportedOperationException.class,
                () -> ex.providerScores().put("wav", 90),
                "providerScores() must be immutable after (String) construction");
    }

    // ---------------------------------------------------------------------
    // Requirement 6.3 — public (String, Throwable) constructor
    // ---------------------------------------------------------------------

    /**
     * The public {@code (String, Throwable)} constructor SHALL preserve
     * the given cause on {@link Throwable#getCause()} (Requirement 6.3) and
     * SHALL still yield empty immutable diagnostics (Requirement 6.4).
     */
    @Test
    void stringThrowableConstructorPreservesCauseAndYieldsEmptyDiagnostics() {
        IOException rootCause = new IOException("disk fell off the bus");

        UnsupportedAudioFormatException ex = new UnsupportedAudioFormatException(
                "decode failed", rootCause);

        assertEquals("decode failed", ex.getMessage(),
                "Detail message should pass through to IOException.getMessage()");
        assertSame(rootCause, ex.getCause(),
                "Cause passed to (String, Throwable) constructor must be preserved on getCause()");
        assertTrue(ex.providersConsulted().isEmpty(),
                "providersConsulted() should be empty after (String, Throwable) construction");
        assertTrue(ex.providerScores().isEmpty(),
                "providerScores() should be empty after (String, Throwable) construction");
    }

    // ---------------------------------------------------------------------
    // Requirement 6.1 — structural IOException subtype
    // ---------------------------------------------------------------------

    /**
     * {@link UnsupportedAudioFormatException} SHALL extend
     * {@link IOException} (Requirement 6.1), which structurally means
     * callers that only catch {@code IOException} must still pick up this
     * subtype in the same handler. Verified by throwing the subtype and
     * catching the supertype in a try/catch, i.e. the actual behaviour the
     * requirement promises callers.
     */
    @Test
    void isCaughtAsIoException() {
        try {
            throw new UnsupportedAudioFormatException("no provider recognised the input");
        } catch (IOException caught) {
            assertTrue(caught instanceof UnsupportedAudioFormatException,
                    "IOException-level catch must still see the concrete subtype");
            assertEquals("no provider recognised the input", caught.getMessage(),
                    "Detail message should survive the IOException-level catch");
        } catch (Throwable other) {
            fail("Expected IOException-level catch to handle the exception, got: " + other);
        }
    }

    // ---------------------------------------------------------------------
    // Defensive-copy behaviour of the package-private diagnostics ctor
    // ---------------------------------------------------------------------

    /**
     * The package-private four-argument constructor
     * {@code (String, Throwable, List, Map)} SHALL defensively copy the
     * diagnostics collections so callers can neither mutate the exception's
     * returned views nor reach into the exception by mutating the
     * originally-passed collections after construction.
     *
     * <p>The latter half is the real test of "defensive copy" — if the
     * implementation merely stored the caller's reference wrapped in
     * {@code Collections.unmodifiableList}/{@code unmodifiableMap}, the
     * returned view would still reflect post-construction mutations to the
     * original.
     */
    @Test
    void packagePrivateConstructorDefensivelyCopiesDiagnostics() {
        // Use mutable implementations so we can try to mutate them after
        // construction and prove the copy is independent.
        List<String> consulted = new ArrayList<>();
        consulted.add("wav");
        consulted.add("mp3");

        Map<String, Integer> scores = new HashMap<>();
        scores.put("wav", 90);
        scores.put("mp3", 85);

        UnsupportedAudioFormatException ex = new UnsupportedAudioFormatException(
                "no provider accepted the input", null, consulted, scores);

        // The returned views reflect the snapshot taken at construction.
        assertEquals(List.of("wav", "mp3"), ex.providersConsulted(),
                "providersConsulted() should reflect the snapshot taken at construction");
        assertEquals(Map.of("wav", 90, "mp3", 85), ex.providerScores(),
                "providerScores() should reflect the snapshot taken at construction");

        // The returned views must be unmodifiable — mutating them throws.
        List<String> returnedList = ex.providersConsulted();
        Map<String, Integer> returnedMap = ex.providerScores();
        assertThrows(UnsupportedOperationException.class, () -> returnedList.add("ogg"),
                "providersConsulted() must be an unmodifiable view");
        assertThrows(UnsupportedOperationException.class, () -> returnedList.remove(0),
                "providersConsulted() must be an unmodifiable view");
        assertThrows(UnsupportedOperationException.class, () -> returnedList.clear(),
                "providersConsulted() must be an unmodifiable view");
        assertThrows(UnsupportedOperationException.class, () -> returnedMap.put("ogg", 50),
                "providerScores() must be an unmodifiable view");
        assertThrows(UnsupportedOperationException.class, () -> returnedMap.remove("wav"),
                "providerScores() must be an unmodifiable view");
        assertThrows(UnsupportedOperationException.class, () -> returnedMap.clear(),
                "providerScores() must be an unmodifiable view");

        // And the defensive-copy half: mutating the caller-supplied
        // originals after construction must not leak into the exception.
        consulted.add("ogg");
        consulted.remove(0);
        scores.put("ogg", 50);
        scores.remove("wav");

        assertEquals(List.of("wav", "mp3"), ex.providersConsulted(),
                "providersConsulted() must not reflect post-construction mutations "
                        + "of the caller-supplied list — the constructor must defensively copy");
        assertEquals(Map.of("wav", 90, "mp3", 85), ex.providerScores(),
                "providerScores() must not reflect post-construction mutations "
                        + "of the caller-supplied map — the constructor must defensively copy");

        // Sanity: the returned collection is not the same instance as the
        // caller-supplied one (the defensive copy is a separate object).
        assertNotSame(consulted, ex.providersConsulted(),
                "providersConsulted() must return a copy, not the caller-supplied list");
        assertNotSame(scores, ex.providerScores(),
                "providerScores() must return a copy, not the caller-supplied map");
    }
}
