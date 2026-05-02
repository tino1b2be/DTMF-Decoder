package com.tino1b2be.dtmf.io;

// Feature: dtmf-io, Property 6: UnsupportedAudioFormatException diagnostics populated

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for {@link UnsupportedAudioFormatException}
 * diagnostics populated by {@link AudioSources}.
 *
 * <p><strong>Property 6: {@code UnsupportedAudioFormatException}
 * diagnostics populated.</strong> <strong>Validates: Requirements 5.7,
 * 5.8, 6.4, 6.5, 6.6.</strong>
 *
 * <p>For any list of {@link ProviderSpec}s each of which yields an
 * effective score of {@code -1} (either directly via
 * {@code score == -1} or indirectly via {@code throwsFromCanOpen ==
 * true}, which {@link AudioSources} maps to {@code -1} per Requirement
 * 5.9), exercised through the package-private
 * {@code AudioSources.openForTesting(...)} seam:
 *
 * <ul>
 *   <li>{@link AudioSources#openForTesting(InputStream, String, List)}
 *       must throw {@link UnsupportedAudioFormatException} whose
 *       {@link UnsupportedAudioFormatException#providersConsulted()
 *       providersConsulted()} equals the discovery-order list of each
 *       spec's {@link AudioSourceProvider#formatName() formatName()}
 *       (Requirement 6.4), and whose
 *       {@link UnsupportedAudioFormatException#providerScores()
 *       providerScores()} contains exactly one entry per consulted
 *       spec name with the value {@code -1} — including entries for
 *       specs whose {@code canOpen} threw (Requirements 6.5, 6.6).
 *       The facade always populates both collections (Requirement 6.6)
 *       and always lists every consulted provider's format name and
 *       returned score (Requirement 5.7).</li>
 *   <li>{@link AudioSources#openForTesting(Path, List)} with
 *       <em>non-throwing</em> {@code -1} specs must surface the same
 *       populated-diagnostics behaviour. Throwing specs are excluded
 *       from the {@code Path} arm because the design's
 *       <em>File-not-found special case</em> (Requirement 12.3, 12.4)
 *       re-throws the first captured {@link IOException} verbatim
 *       rather than folding into {@link UnsupportedAudioFormatException}
 *       as soon as one is captured and no eligible provider is found.
 *       The {@link InputStream} arm above covers the throwing case
 *       uniformly because the special case does not apply to that
 *       overload.</li>
 *   <li>For the empty provider list, both overloads throw
 *       {@link UnsupportedAudioFormatException} whose
 *       {@link UnsupportedAudioFormatException#providersConsulted()}
 *       and {@link UnsupportedAudioFormatException#providerScores()}
 *       are empty (Requirement 5.8).</li>
 * </ul>
 *
 * <h2>Generator shape</h2>
 *
 * <p>Each {@link ProviderSpec} carries a {@code name}, a {@code score}
 * fixed to {@code -1} (this property is about the all-reject path
 * only), a {@code priority} in {@code [-10, 10]} (irrelevant to
 * diagnostics but kept non-trivial so the generator does not silently
 * stall on a default), and a {@code throwsFromCanOpen} boolean that is
 * set per-arm: the {@link InputStream} arm mixes throwing and
 * non-throwing specs; the {@link Path} arm uses only non-throwing ones
 * for the reason stated above. Names are globally unique per generated
 * list (via suffix disambiguation) so
 * {@link UnsupportedAudioFormatException#providerScores()} — which is
 * a {@link Map} keyed on {@link AudioSourceProvider#formatName()
 * formatName()} — carries one entry per consulted spec (Requirement
 * 6.5).
 *
 * <p>Non-empty list sizes vary from {@code 1} to {@code 8}. The
 * zero-provider path is exercised by a dedicated non-property test
 * method because it has no parameters to randomise.
 */
class UnsupportedAudioFormatExceptionDiagnosticsPropertyTest {

    // ------------------------------------------------------------------
    // Invariant A — InputStream arm: every spec yields -1 (throwing or
    // not) ⇒ UnsupportedAudioFormatException with populated diagnostics
    // (Req 5.7, 6.4, 6.5, 6.6)
    // ------------------------------------------------------------------

    /**
     * When every spec in a non-empty list yields an effective score of
     * {@code -1} — whether by returning {@code -1} non-exceptionally
     * or by throwing {@link IOException} from {@code canOpen} —
     * {@link AudioSources#openForTesting(InputStream, String, List)}
     * must throw {@link UnsupportedAudioFormatException} whose
     * {@code providersConsulted()} equals the discovery-order list of
     * format names (Req 6.4) and whose {@code providerScores()}
     * records {@code -1} for every consulted spec (Req 6.5).
     *
     * <p>The {@link InputStream} overload is used here because the
     * design's file-not-found special case (Requirements 12.3, 12.4)
     * does not apply to it: an {@link IOException} during
     * {@code canOpen(InputStream, String)} is a header-read failure,
     * not a missing-source signal, so every throwing {@code -1} still
     * surfaces through {@link UnsupportedAudioFormatException} with
     * populated diagnostics.
     */
    @Property(tries = 100)
    void inputStreamArmPopulatesDiagnosticsForEveryAllMinusOneList(
            @ForAll("nonEmptyMixedMinusOneSpecs") List<ProviderSpec> specs)
            throws IOException {

        // Sanity: every spec in this generator yields an effective
        // score of -1 (either directly or by throwing).
        for (ProviderSpec spec : specs) {
            assertEquals(-1, spec.effectiveScore(),
                    () -> "Generator precondition violated: spec " + spec
                            + " is not an effective -1 rejector");
        }

        List<AudioSourceProvider> providers = toProviders(specs);
        InputStream stream = new ByteArrayInputStream(new byte[0]);

        UnsupportedAudioFormatException ex = assertThrows(
                UnsupportedAudioFormatException.class,
                () -> AudioSources.openForTesting(stream, /* hint */ null, providers),
                "All -1 scores on open(InputStream) must surface as "
                        + "UnsupportedAudioFormatException (Req 5.7)");

        assertDiagnosticsPopulated(ex, specs);
    }

    // ------------------------------------------------------------------
    // Invariant B — Path arm (non-throwing specs only): every spec
    // yields -1 ⇒ UnsupportedAudioFormatException with populated
    // diagnostics. Throwing specs excluded because of the file-not-found
    // special case (Req 12.3, 12.4) in the Path overload.
    // ------------------------------------------------------------------

    /**
     * Same invariant as the {@link InputStream} arm, restricted to
     * non-throwing {@code -1} specs so the {@link Path} overload's
     * file-not-found special case does not fire. Anchors Req 6.6's
     * "{@code AudioSources} populates the diagnostics on every
     * exception it throws from {@code open(...)}" promise on the
     * {@code Path} arm as well.
     */
    @Property(tries = 100)
    void pathArmPopulatesDiagnosticsForNonThrowingAllMinusOneList(
            @ForAll("nonEmptyNonThrowingMinusOneSpecs") List<ProviderSpec> specs)
            throws IOException {

        // Sanity: no throwers, all -1.
        for (ProviderSpec spec : specs) {
            assertTrue(spec.score() == -1 && !spec.throwsFromCanOpen(),
                    () -> "Generator precondition violated: spec " + spec
                            + " is not a non-throwing -1 rejector");
        }

        List<AudioSourceProvider> providers = toProviders(specs);
        Path dummy = Files.createTempFile("uafe-diagnostics-prop-", ".bin");
        try {
            UnsupportedAudioFormatException ex = assertThrows(
                    UnsupportedAudioFormatException.class,
                    () -> AudioSources.openForTesting(dummy, providers),
                    "All non-throwing -1 scores on open(Path) must surface "
                            + "as UnsupportedAudioFormatException (Req 5.7)");

            assertDiagnosticsPopulated(ex, specs);
        } finally {
            deleteQuietly(dummy);
        }
    }

    // ------------------------------------------------------------------
    // Invariant C — Empty provider list ⇒ both diagnostics collections
    // are empty (Req 5.8, 6.4)
    // ------------------------------------------------------------------

    /**
     * With no providers registered, both overloads must throw
     * {@link UnsupportedAudioFormatException} whose
     * {@code providersConsulted()} and {@code providerScores()} are
     * empty (Req 5.8, Req 6.4: "empty list when no providers were
     * consulted"). Written as a non-property method because the input
     * has no free parameters to randomise over.
     */
    @org.junit.jupiter.api.Test
    void emptyProviderListYieldsEmptyDiagnosticsOnBothOverloads() throws IOException {
        List<AudioSourceProvider> empty = List.of();

        // InputStream arm.
        InputStream stream = new ByteArrayInputStream(new byte[0]);
        UnsupportedAudioFormatException streamEx = assertThrows(
                UnsupportedAudioFormatException.class,
                () -> AudioSources.openForTesting(stream, /* hint */ null, empty),
                "Empty provider list on open(InputStream) must throw "
                        + "UnsupportedAudioFormatException (Req 5.8)");
        assertTrue(streamEx.providersConsulted().isEmpty(),
                () -> "providersConsulted() must be empty for zero-provider "
                        + "input (Req 5.8, 6.4); got " + streamEx.providersConsulted());
        assertTrue(streamEx.providerScores().isEmpty(),
                () -> "providerScores() must be empty for zero-provider "
                        + "input (Req 5.8, 6.4); got " + streamEx.providerScores());

        // Path arm.
        Path dummy = Files.createTempFile("uafe-diagnostics-empty-", ".bin");
        try {
            UnsupportedAudioFormatException pathEx = assertThrows(
                    UnsupportedAudioFormatException.class,
                    () -> AudioSources.openForTesting(dummy, empty),
                    "Empty provider list on open(Path) must throw "
                            + "UnsupportedAudioFormatException (Req 5.8)");
            assertTrue(pathEx.providersConsulted().isEmpty(),
                    () -> "providersConsulted() must be empty for zero-provider "
                            + "input (Req 5.8, 6.4); got " + pathEx.providersConsulted());
            assertTrue(pathEx.providerScores().isEmpty(),
                    () -> "providerScores() must be empty for zero-provider "
                            + "input (Req 5.8, 6.4); got " + pathEx.providerScores());
        } finally {
            deleteQuietly(dummy);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Assert that the exception's diagnostics collections fully describe
     * the consulted spec list: {@code providersConsulted()} matches the
     * discovery-order spec names exactly (Req 6.4), and
     * {@code providerScores()} contains exactly one entry per consulted
     * name with the value {@code -1} (Req 6.5, Req 5.7, Req 5.9). Also
     * asserts the formatted message lists every consulted name and the
     * {@code -1} score token, per Req 5.7 ("message lists every
     * discovered provider's formatName() and its returned score").
     */
    private static void assertDiagnosticsPopulated(
            UnsupportedAudioFormatException ex, List<ProviderSpec> specs) {
        List<String> expectedNames = specs.stream().map(ProviderSpec::name).toList();

        assertEquals(expectedNames, ex.providersConsulted(),
                () -> "providersConsulted() must equal the discovery-order "
                        + "list of spec names (Req 6.4); specs=" + specs);

        Map<String, Integer> scores = ex.providerScores();
        assertEquals(expectedNames.size(), scores.size(),
                () -> "providerScores() must have exactly one entry per "
                        + "consulted spec (Req 6.5); got " + scores);
        assertEquals(new HashSet<>(expectedNames), scores.keySet(),
                () -> "providerScores() keys must equal the consulted "
                        + "spec names (Req 6.5); got " + scores);
        for (String name : expectedNames) {
            assertEquals(-1, scores.get(name),
                    () -> "providerScores()[" + name + "] must be -1 (Req "
                            + "5.7 for non-throwing -1, Req 5.9 for throwers); "
                            + "got " + scores);
        }

        // Req 5.7: the message lists every provider's formatName() and
        // its returned score (-1 in the all-reject case).
        String message = ex.getMessage();
        assertNotNull(message, "UnsupportedAudioFormatException must carry a message");
        for (String name : expectedNames) {
            assertTrue(message.contains(name),
                    () -> "Message must identify spec '" + name + "' "
                            + "(Req 5.7); was: " + message);
        }
        assertTrue(message.contains("-1"),
                () -> "Message must mention the -1 score for the all-reject "
                        + "case (Req 5.7); was: " + message);
    }

    /** Build one {@link FakeProvider} per spec, in the spec list's order. */
    private static List<AudioSourceProvider> toProviders(List<ProviderSpec> specs) {
        List<AudioSourceProvider> providers = new ArrayList<>(specs.size());
        for (ProviderSpec spec : specs) {
            providers.add(new FakeProvider(spec));
        }
        return providers;
    }

    /** Best-effort temp-file cleanup; a leftover file does not invalidate
     *  assertions. */
    private static void deleteQuietly(Path dummy) {
        try {
            Files.deleteIfExists(dummy);
        } catch (IOException ignored) {
            // Best-effort cleanup.
        }
    }

    // ------------------------------------------------------------------
    // Arbitraries
    // ------------------------------------------------------------------

    /**
     * List of specs each with {@code score == -1} and mixed
     * {@code throwsFromCanOpen} (either {@code true} or {@code false}).
     * Used by the {@link InputStream} arm where the file-not-found
     * special case does not apply and throwing {@code -1} specs still
     * surface through {@link UnsupportedAudioFormatException} with
     * populated diagnostics.
     */
    @Provide
    Arbitrary<List<ProviderSpec>> nonEmptyMixedMinusOneSpecs() {
        Arbitrary<ProviderSpec> specAnyThrowing = Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(6),
                Arbitraries.integers().between(-10, 10),
                Arbitraries.of(true, false)
        ).as((name, priority, throwsFromCanOpen) ->
                new ProviderSpec(name, /* score */ -1, priority, throwsFromCanOpen));

        return specAnyThrowing.list().ofMinSize(1).ofMaxSize(8)
                .map(UnsupportedAudioFormatExceptionDiagnosticsPropertyTest::disambiguateNames);
    }

    /**
     * List of specs each with {@code score == -1} and
     * {@code throwsFromCanOpen == false}. Used by the {@link Path} arm
     * because the design's file-not-found special case (Req 12.3,
     * 12.4) would otherwise re-throw the first captured
     * {@link IOException} verbatim as soon as one was captured and no
     * eligible provider was found, bypassing the populated-diagnostics
     * assertion this property is about.
     */
    @Provide
    Arbitrary<List<ProviderSpec>> nonEmptyNonThrowingMinusOneSpecs() {
        Arbitrary<ProviderSpec> nonThrowingMinusOne = Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(6),
                Arbitraries.integers().between(-10, 10)
        ).as((name, priority) ->
                new ProviderSpec(name, /* score */ -1, priority, /* throws */ false));

        return nonThrowingMinusOne.list().ofMinSize(1).ofMaxSize(8)
                .map(UnsupportedAudioFormatExceptionDiagnosticsPropertyTest::disambiguateNames);
    }

    /**
     * Post-process a spec list so no two specs share a {@code name}.
     * Collisions are resolved by appending {@code "#i"} to duplicates,
     * where {@code i} is the spec's index in the list. This guarantees
     * unique names without filtering (which would stall the generator
     * on small draw ranges) and without changing list size or ordering.
     * Uniqueness is required because {@code providerScores()} is keyed
     * by {@code formatName()} — duplicate names would collapse entries
     * (Req 6.5 expects one entry per consulted spec).
     */
    private static List<ProviderSpec> disambiguateNames(List<ProviderSpec> specs) {
        Set<String> seen = new HashSet<>();
        List<ProviderSpec> out = new ArrayList<>(specs.size());
        for (int i = 0; i < specs.size(); i++) {
            ProviderSpec original = specs.get(i);
            String unique = original.name();
            if (!seen.add(unique)) {
                unique = original.name() + "#" + i;
                int suffix = i;
                while (!seen.add(unique)) {
                    suffix++;
                    unique = original.name() + "#" + suffix;
                }
            }
            out.add(new ProviderSpec(unique, original.score(),
                    original.priority(), original.throwsFromCanOpen()));
        }
        return Collections.unmodifiableList(out);
    }

    // ------------------------------------------------------------------
    // Spec record
    // ------------------------------------------------------------------

    /**
     * Describes a single generated provider: its format name, the score
     * its {@code canOpen} should return (always {@code -1} for this
     * property), its {@link AudioSourceProvider#priority() priority()}
     * (irrelevant for diagnostics but kept non-trivial), and whether
     * its {@code canOpen} should throw {@link IOException} instead of
     * returning the score.
     *
     * @param name               non-null, non-empty format name; unique
     *                           within any single generated list
     * @param score              score to return from {@code canOpen};
     *                           always {@code -1} in this property
     * @param priority           value returned from
     *                           {@link AudioSourceProvider#priority()}
     * @param throwsFromCanOpen  when {@code true}, the provider's
     *                           {@code canOpen} throws
     *                           {@link IOException} rather than
     *                           returning {@code score}
     */
    record ProviderSpec(
            String name, int score, int priority, boolean throwsFromCanOpen) {

        /** Effective score as seen by {@link AudioSources}: throwing
         *  is mapped to {@code -1} per Requirement 5.9. */
        int effectiveScore() {
            return throwsFromCanOpen ? -1 : score;
        }
    }

    // ------------------------------------------------------------------
    // Test doubles
    // ------------------------------------------------------------------

    /**
     * Minimal {@link AudioSourceProvider} test double driven by a
     * {@link ProviderSpec}. Returns the spec's score (or throws) from
     * both {@code canOpen} overloads; the {@code open(...)} methods
     * are never reached on the all-reject path this property
     * exercises, so they throw {@link UnsupportedOperationException}
     * to flag any future regression that routed dispatch to them.
     */
    private static final class FakeProvider implements AudioSourceProvider {
        private final ProviderSpec spec;

        FakeProvider(ProviderSpec spec) {
            this.spec = spec;
        }

        @Override
        public String formatName() {
            return spec.name();
        }

        @Override
        public int priority() {
            return spec.priority();
        }

        @Override
        public int canOpen(Path path) throws IOException {
            if (spec.throwsFromCanOpen()) {
                throw new IOException("synthetic canOpen(Path) failure for "
                        + spec.name());
            }
            return spec.score();
        }

        @Override
        public int canOpen(InputStream stream, String hint) throws IOException {
            if (spec.throwsFromCanOpen()) {
                throw new IOException("synthetic canOpen(InputStream) failure for "
                        + spec.name());
            }
            return spec.score();
        }

        @Override
        public AudioSource open(Path path) {
            throw new UnsupportedOperationException(
                    "open(Path) must not be reached when every provider returns -1; "
                            + "spec=" + spec);
        }

        @Override
        public AudioSource open(InputStream stream, String hint) {
            throw new UnsupportedOperationException(
                    "open(InputStream) must not be reached when every provider returns -1; "
                            + "spec=" + spec);
        }
    }
}
