package com.tino1b2be.dtmf.io;

// Feature: dtmf-io, Property 5: AudioSources scoring and selection

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;

/**
 * Property-based tests for {@link AudioSources} scoring and selection.
 *
 * <p><strong>Property 5: {@code AudioSources} scoring and
 * selection.</strong> <strong>Validates: Requirements 5.6, 5.7, 5.9,
 * 5.11.</strong>
 *
 * <p>For any list of {@link ProviderSpec}s — each one describing a
 * provider that returns a fixed SPI Priority Score from its
 * {@code canOpen(Path)}, optionally throws {@link IOException} from
 * {@code canOpen(Path)} instead, and declares a fixed
 * {@link AudioSourceProvider#priority() priority()} — exercised through
 * the package-private {@code AudioSources.openForTesting(Path, List)}
 * and {@code AudioSources.registeredFormatsForTesting(List)} seams:
 *
 * <ul>
 *   <li>If every spec yields a score of {@code -1}
 *       non-exceptionally (i.e. {@code score == -1} and
 *       {@code throwsFromCanOpen == false}), then
 *       {@link AudioSources#openForTesting(Path, List)} must throw
 *       {@link UnsupportedAudioFormatException} whose
 *       {@link UnsupportedAudioFormatException#providersConsulted()}
 *       equals the list of spec names in discovery order and whose
 *       {@link UnsupportedAudioFormatException#providerScores()}
 *       records {@code -1} for every consulted spec (Requirement
 *       5.7).</li>
 *   <li>Otherwise, the winner must be the provider whose
 *       {@code (score, priority)} pair is strictly greater than every
 *       other eligible spec's under the lexicographic ordering
 *       {@code score} first, then {@code priority} (Requirement 5.6).
 *       The winner's {@code open(Path)} is the one whose {@link
 *       AudioSource} {@code openForTesting} returns.</li>
 *   <li>{@link AudioSources#registeredFormatsForTesting(List)} returns
 *       the spec names in the list's iteration order, regardless of
 *       the scoring decisions made above (Requirement 5.11).</li>
 *   <li>A spec whose {@code canOpen(Path)} throws is scored {@code -1}
 *       <em>and</em> produces at least one {@link Level#WARNING WARNING}
 *       log record on the {@link AudioSources} logger naming the
 *       throwing provider's {@link AudioSourceProvider#formatName()
 *       formatName()} (Requirement 5.9).</li>
 * </ul>
 *
 * <h2>Generator shape</h2>
 *
 * <p>Each {@link ProviderSpec} carries a {@code name}, a {@code score}
 * in {@code {-1} ∪ [0, 100]}, a {@code priority} in {@code [-10, 10]}
 * (the Req 4.3 default is {@code 0} so a small window around that
 * exercises tie-break), and a {@code throwsFromCanOpen} boolean. Names
 * across a single generated list are forced to be unique in a
 * post-generation step so {@link UnsupportedAudioFormatException#providerScores()}
 * — which keys on {@code formatName()} — carries one entry per consulted
 * spec (Requirement 6.5). Uniqueness is achieved by appending a
 * discovery-index suffix to collision cases, not by filtering, so the
 * generator never stalls on a draw.
 *
 * <p>List sizes vary from {@code 0} to {@code 8} providers. The empty
 * list is the "no providers registered" path (Requirement 5.8) which is
 * not this property's focus — Property 6 covers that — so this property
 * uses {@code @Size(min = 1, max = 8)} to restrict itself to the non-
 * empty case.
 *
 * <h2>Scope</h2>
 *
 * <p>This property targets Requirements 5.6, 5.7, 5.9, and 5.11 as
 * called out above. Two adjacent behaviours are intentionally out of
 * scope because they belong to later/other properties and tests:
 * <ul>
 *   <li><em>No-providers-registered</em> (Requirement 5.8) — covered
 *       by {@code AudioSourcesTest} unit tests and by Property 6
 *       (UAFE diagnostics).</li>
 *   <li><em>File-not-found special case</em> (Requirements 12.3,
 *       12.4) — when any consulted provider declines by throwing
 *       {@link IOException} and no eligible provider is found,
 *       {@link AudioSources} re-throws the first captured exception
 *       verbatim (so {@link java.nio.file.NoSuchFileException} and
 *       permission errors propagate unchanged) rather than folding
 *       into {@link UnsupportedAudioFormatException}. The
 *       {@code nonEmptyAllRejectingSpecs} generator therefore emits
 *       only non-throwing {@code -1} rejectors so Invariant A can
 *       assert the pure-Req-5.7 behaviour without stepping on the
 *       special case; that branch is exercised by
 *       {@code AudioSourcesTest}'s
 *       {@code openPathFileNotFoundWithSingleWavProviderPropagatesNoSuchFileException}
 *       unit test. The "throwing is scored {@code -1} and logged at
 *       {@code WARNING}" half of Requirement 5.9 is anchored instead
 *       from the eligible-winner path
 *       ({@code atLeastOneEligibleWinnerHasStrictlyMaxScorePriorityPair}),
 *       where the special case cannot fire because at least one
 *       eligible provider keeps dispatch on the winner-selection
 *       branch.</li>
 * </ul>
 */
class AudioSourcesScoringPropertyTest {

    /** Logger captured during each {@code @Property} try so Requirement
     *  5.9's WARNING assertion is observable. */
    private static final Logger AUDIO_SOURCES_LOGGER =
            Logger.getLogger(AudioSources.class.getName());

    private CapturingHandler capturingHandler;
    private Level priorLevel;
    private boolean priorUseParent;

    @BeforeTry
    void attachLoggingCapture() {
        capturingHandler = new CapturingHandler();
        priorLevel = AUDIO_SOURCES_LOGGER.getLevel();
        priorUseParent = AUDIO_SOURCES_LOGGER.getUseParentHandlers();
        AUDIO_SOURCES_LOGGER.setLevel(Level.ALL);
        AUDIO_SOURCES_LOGGER.setUseParentHandlers(false);
        AUDIO_SOURCES_LOGGER.addHandler(capturingHandler);
    }

    @AfterTry
    void detachLoggingCapture() {
        AUDIO_SOURCES_LOGGER.removeHandler(capturingHandler);
        AUDIO_SOURCES_LOGGER.setUseParentHandlers(priorUseParent);
        AUDIO_SOURCES_LOGGER.setLevel(priorLevel);
    }

    // ------------------------------------------------------------------
    // Invariant A — all specs yield -1 ⇒ UnsupportedAudioFormatException
    // with populated diagnostics (Req 5.7 + Req 5.9 for throwers)
    // ------------------------------------------------------------------

    /**
     * When every {@link ProviderSpec} in the list yields {@code -1}
     * (either directly via {@code score == -1} or indirectly via
     * {@code throwsFromCanOpen == true}, which the facade maps to
     * {@code -1} per Requirement 5.9), {@link AudioSources#openForTesting(Path, List)}
     * must throw {@link UnsupportedAudioFormatException} whose
     * {@code providersConsulted()} lists every spec's name in discovery
     * order and whose {@code providerScores()} records {@code -1} for
     * each.
     */
    @Property(tries = 100)
    void allSpecsYieldMinusOneProducesUnsupportedAudioFormatException(
            @ForAll("nonEmptyAllRejectingSpecs") List<ProviderSpec> specs) throws IOException {

        // Sanity: the generator yields non-throwing -1 specs only.
        for (ProviderSpec spec : specs) {
            assertTrue(spec.score() == -1 && !spec.throwsFromCanOpen(),
                    () -> "Generator precondition violated: spec " + spec
                            + " is not a non-throwing -1 rejector");
        }

        List<AudioSourceProvider> providers = toProviders(specs);
        Path dummy = Files.createTempFile("audio-sources-scoring-prop-", ".bin");
        try {
            UnsupportedAudioFormatException ex = assertThrows(
                    UnsupportedAudioFormatException.class,
                    () -> AudioSources.openForTesting(dummy, providers),
                    "All -1 scores must surface as UnsupportedAudioFormatException "
                            + "(Req 5.7)");

            // providersConsulted == discovery-order spec names.
            List<String> expectedNames = specs.stream().map(ProviderSpec::name).toList();
            assertEquals(expectedNames, ex.providersConsulted(),
                    () -> "providersConsulted() must list every consulted spec's "
                            + "formatName() in discovery order (Req 5.7, 6.4)");
            // providerScores records -1 for each.
            assertEquals(expectedNames.size(), ex.providerScores().size(),
                    () -> "providerScores() must have one entry per consulted spec "
                            + "(Req 6.5); got " + ex.providerScores());
            for (String name : expectedNames) {
                assertEquals(-1, ex.providerScores().get(name),
                        () -> "providerScores()[" + name + "] must be -1 "
                                + "(Req 5.7, Req 5.9 for throwers); got "
                                + ex.providerScores());
            }

            // Message must include every spec's name and the score -1
            // (Req 5.7 "lists every discovered provider's formatName() and its
            // returned score").
            String message = ex.getMessage();
            assertNotNull(message, "UnsupportedAudioFormatException must carry a message");
            for (String name : expectedNames) {
                assertTrue(message.contains(name),
                        () -> "Message must identify spec '" + name + "' (Req 5.7); was: "
                                + message);
            }
            assertTrue(message.contains("-1"),
                    () -> "Message must mention the -1 score for all-reject case (Req 5.7); was: "
                            + message);

            // No throwing specs in this generator, so no WARNING
            // records are expected from the scoring loop itself.
            // Requirement 5.9's throwing-is-logged half is asserted
            // from the eligible-winner property instead.
        } finally {
            Files.deleteIfExists(dummy);
        }
    }

    // ------------------------------------------------------------------
    // Invariant B — at least one spec is eligible ⇒ winner has strictly
    // maximum (score, priority) lex pair (Req 5.6)
    // ------------------------------------------------------------------

    /**
     * When at least one {@link ProviderSpec} has {@code score >= 0} and
     * does not throw, the provider returned by
     * {@link AudioSources#openForTesting(Path, List)} must be the one
     * whose {@code (score, priority)} pair is strictly greater than
     * every other eligible spec under the lexicographic ordering
     * (Requirement 5.6). The property enforces uniqueness of the
     * winning pair by constructing the spec list with a single, unique
     * top-rank entry; ties below the winner are allowed and represent
     * exactly the ambiguous cases the spec says the algorithm does not
     * need to disambiguate.
     */
    @Property(tries = 100)
    void atLeastOneEligibleWinnerHasStrictlyMaxScorePriorityPair(
            @ForAll("listWithUniqueTopRank") List<ProviderSpec> specs) throws IOException {

        // Sanity: at least one spec is eligible (score >= 0, not throwing).
        long eligibleCount = specs.stream().filter(ProviderSpec::isEligible).count();
        Assume.that(eligibleCount >= 1);

        // Identify the expected winner: the unique spec with the strictly
        // maximum (score, priority) lex pair among eligible specs.
        ProviderSpec expected = expectedWinner(specs);
        assertNotNull(expected,
                "Generator precondition: listWithUniqueTopRank must produce a "
                        + "list with a strictly maximum (score, priority) eligible spec");

        List<AudioSourceProvider> providers = toProviders(specs);
        // Each FakeProvider is instantiated fresh per invocation of
        // toProviders, so we capture a direct reference to the winner's
        // provider here to assert open(Path) was routed to it.
        FakeProvider winnerProvider =
                (FakeProvider) providers.get(indexOf(specs, expected));

        Path dummy = Files.createTempFile("audio-sources-scoring-prop-", ".bin");
        try {
            AudioSource returned = AudioSources.openForTesting(dummy, providers);
            assertSame(winnerProvider.stubSource(), returned,
                    () -> "open(Path) must return the AudioSource produced by the "
                            + "provider with the strictly max (score, priority) pair "
                            + "(Req 5.6); winner was expected=" + expected
                            + " from list=" + specs);
            assertEquals(1, winnerProvider.openPathCallCount(),
                    () -> "Winner's open(Path) must be invoked exactly once "
                            + "(Req 5.6); winner=" + expected);
            // No losing eligible spec's open(Path) was invoked.
            for (int i = 0; i < specs.size(); i++) {
                ProviderSpec spec = specs.get(i);
                if (spec == expected) {
                    continue;
                }
                FakeProvider fp = (FakeProvider) providers.get(i);
                assertEquals(0, fp.openPathCallCount(),
                        () -> "Non-winner '" + spec.name() + "' open(Path) must not "
                                + "be invoked; winner=" + expected + ", list=" + specs);
            }

            assertAtLeastOneWarningPerThrowingSpec(specs);
        } finally {
            deleteQuietly(dummy);
        }
    }

    // ------------------------------------------------------------------
    // Invariant C — registeredFormatsForTesting preserves discovery
    // order (Req 5.11)
    // ------------------------------------------------------------------

    /**
     * {@link AudioSources#registeredFormatsForTesting(List)} must
     * return each spec's {@link AudioSourceProvider#formatName()
     * formatName()} in the exact iteration order of the input list
     * (Requirement 5.11). The property asserts this independently of
     * scoring: list membership and order drive the output, nothing
     * about {@code score} / {@code priority} / throwing behaviour
     * does.
     */
    @Property(tries = 100)
    void registeredFormatsPreservesDiscoveryOrder(
            @ForAll("uniqueNameSpecs") List<ProviderSpec> specs) {

        List<AudioSourceProvider> providers = toProviders(specs);
        List<String> expected = specs.stream().map(ProviderSpec::name).toList();

        List<String> actual = AudioSources.registeredFormatsForTesting(providers);

        assertEquals(expected, actual,
                () -> "registeredFormatsForTesting must return format names in "
                        + "discovery order (Req 5.11); specs=" + specs);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Expected winner under Requirement 5.6: the eligible spec whose
     * {@code (score, priority)} pair is strictly greater than every
     * other eligible spec's under lex order. Returns {@code null} when
     * no such unique winner exists (which {@code listWithUniqueTopRank}
     * rules out by construction but a defensive null-return here keeps
     * the property's assertions honest).
     */
    private static ProviderSpec expectedWinner(List<ProviderSpec> specs) {
        ProviderSpec best = null;
        boolean tiedAtBest = false;
        for (ProviderSpec spec : specs) {
            if (!spec.isEligible()) {
                continue;
            }
            if (best == null) {
                best = spec;
                continue;
            }
            int cmp = compareByScoreThenPriority(spec, best);
            if (cmp > 0) {
                best = spec;
                tiedAtBest = false;
            } else if (cmp == 0) {
                tiedAtBest = true;
            }
        }
        return tiedAtBest ? null : best;
    }

    private static int compareByScoreThenPriority(ProviderSpec a, ProviderSpec b) {
        int byScore = Integer.compare(a.score(), b.score());
        if (byScore != 0) {
            return byScore;
        }
        return Integer.compare(a.priority(), b.priority());
    }

    private static int indexOf(List<ProviderSpec> specs, ProviderSpec target) {
        for (int i = 0; i < specs.size(); i++) {
            if (specs.get(i) == target) {
                return i;
            }
        }
        throw new AssertionError("Spec not found in list: " + target);
    }

    /** Build one {@link FakeProvider} per spec, in the spec list's order. */
    private static List<AudioSourceProvider> toProviders(List<ProviderSpec> specs) {
        List<AudioSourceProvider> providers = new ArrayList<>(specs.size());
        for (ProviderSpec spec : specs) {
            providers.add(new FakeProvider(spec));
        }
        return providers;
    }

    /**
     * For each spec whose {@code canOpen} throws, at least one
     * {@link Level#WARNING WARNING} log record must identify the
     * throwing provider's {@link AudioSourceProvider#formatName()
     * formatName()} (Requirement 5.9). Providers that return
     * {@code -1} without throwing do not need to be logged.
     */
    private void assertAtLeastOneWarningPerThrowingSpec(List<ProviderSpec> specs) {
        Set<String> throwingNames = new HashSet<>();
        for (ProviderSpec spec : specs) {
            if (spec.throwsFromCanOpen()) {
                throwingNames.add(spec.name());
            }
        }
        if (throwingNames.isEmpty()) {
            return;
        }
        List<LogRecord> warnings = capturingHandler.warnings();
        assertFalse(warnings.isEmpty(),
                () -> "At least one WARNING must be logged for each throwing "
                        + "provider (Req 5.9); throwing specs=" + throwingNames
                        + ", but no warnings were captured");
        for (String name : throwingNames) {
            boolean seen = warnings.stream()
                    .anyMatch(r -> r.getMessage() != null
                            && r.getMessage().contains(name));
            assertTrue(seen,
                    () -> "A WARNING must identify throwing provider '" + name
                            + "' (Req 5.9); captured records=" + warnings);
        }
    }

    /**
     * Delete the temp file used as a dummy {@code Path} argument to
     * {@link AudioSources#openForTesting(Path, List)}. Called from a
     * finally block so a failing assertion does not leak files.
     */
    private static void deleteQuietly(Path dummy) {
        try {
            Files.deleteIfExists(dummy);
        } catch (IOException ignored) {
            // Best-effort cleanup; leaving a temp file behind does not
            // invalidate the property's assertions.
        }
    }

    // ------------------------------------------------------------------
    // Arbitraries
    // ------------------------------------------------------------------

    /**
     * List of {@link ProviderSpec}s in which every spec yields an
     * effective score of {@code -1} <em>non-exceptionally</em>. Every
     * spec has {@code score == -1} and {@code throwsFromCanOpen == false}.
     * Names are globally unique across the list.
     *
     * <p>Throwing providers are excluded from this generator because
     * the design's <em>File-not-found special case</em>
     * (Requirements 12.3, 12.4) re-throws the first captured
     * {@link IOException} verbatim as soon as one is captured and no
     * eligible provider is found — regardless of whether every
     * non-eligible provider threw or only some did. That branch
     * exists so {@link java.nio.file.NoSuchFileException} and
     * permission errors propagate unchanged. The "throwing is scored
     * {@code -1} and logged at {@code WARNING}" side of
     * Requirement 5.9 is asserted from the eligible-winner path in
     * {@link #atLeastOneEligibleWinnerHasStrictlyMaxScorePriorityPair},
     * where the special case cannot fire because at least one
     * eligible provider is present.
     */
    @Provide
    Arbitrary<List<ProviderSpec>> nonEmptyAllRejectingSpecs() {
        Arbitrary<ProviderSpec> nonThrowingMinusOne = Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(6),
                Arbitraries.integers().between(-10, 10)
        ).as((name, priority) ->
                new ProviderSpec(name, /* score */ -1, priority, /* throws */ false));

        return nonThrowingMinusOne.list().ofMinSize(1).ofMaxSize(8)
                .map(AudioSourcesScoringPropertyTest::disambiguateNames);
    }

    /**
     * List of {@link ProviderSpec}s in which at least one spec is
     * eligible ({@code score >= 0}, not throwing) and the eligible
     * spec with the strictly maximum {@code (score, priority)} lex
     * pair is unique. Achieved by generating an arbitrary spec list,
     * then injecting a hand-built "guaranteed winner" spec whose
     * pair beats every other spec's by at least {@code 1} on score.
     * The winner's insertion index is randomised so dispatch does not
     * hinge on list position.
     */
    @Provide
    Arbitrary<List<ProviderSpec>> listWithUniqueTopRank() {
        // Use a narrower score range [0, 50] for the "other" specs so
        // the guaranteed winner (score >= 60) is always strictly above
        // every eligible "other" spec regardless of priority.
        Arbitrary<ProviderSpec> otherSpecs = Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(6),
                Arbitraries.integers().between(-1, 50),
                Arbitraries.integers().between(-10, 10),
                Arbitraries.of(true, false)
        ).as((name, score, priority, throwsFromCanOpen) ->
                new ProviderSpec(name, score, priority, throwsFromCanOpen));

        // Winner: score ∈ [60, 100], never throws. Name prefix keeps it
        // unique after disambiguation.
        Arbitrary<ProviderSpec> winnerSpec = Combinators.combine(
                Arbitraries.integers().between(60, 100),
                Arbitraries.integers().between(-10, 10)
        ).as((score, priority) ->
                new ProviderSpec("W_winner", score, priority, false));

        return Combinators.combine(
                otherSpecs.list().ofMinSize(0).ofMaxSize(7),
                winnerSpec,
                Arbitraries.integers().between(0, 7)
        ).as((others, winner, insertAt) -> {
            List<ProviderSpec> combined = new ArrayList<>(others);
            int insertionPoint = Math.min(insertAt, combined.size());
            combined.add(insertionPoint, winner);
            return disambiguateNames(combined);
        }).filter(specs -> {
            // Post-filter: keep only lists whose expected winner is
            // unique under the (score, priority) lex order. A collision
            // at the top rank — possible when an "other" spec happens
            // to draw the same score as the winner and tie on priority
            // — is filtered out so the property asserts a determinate
            // winner.
            return expectedWinner(specs) != null;
        });
    }

    /** Arbitrary list of specs with globally unique names. Used by
     *  Invariant C (discovery order) which does not care about scores. */
    @Provide
    Arbitrary<List<ProviderSpec>> uniqueNameSpecs() {
        Arbitrary<ProviderSpec> anySpec = Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(6),
                Arbitraries.integers().between(-1, 100),
                Arbitraries.integers().between(-10, 10),
                Arbitraries.of(true, false)
        ).as(ProviderSpec::new);

        return anySpec.list().ofMinSize(0).ofMaxSize(8)
                .map(AudioSourcesScoringPropertyTest::disambiguateNames);
    }

    /**
     * Post-process a spec list so no two specs share a {@code name}.
     * Collisions are resolved by appending {@code "#i"} to duplicates,
     * where {@code i} is the spec's index in the list. This guarantees
     * unique names without filtering (which would stall the generator
     * on small draw ranges) and without changing list size or ordering.
     */
    private static List<ProviderSpec> disambiguateNames(List<ProviderSpec> specs) {
        Set<String> seen = new HashSet<>();
        List<ProviderSpec> out = new ArrayList<>(specs.size());
        for (int i = 0; i < specs.size(); i++) {
            ProviderSpec original = specs.get(i);
            String unique = original.name();
            if (!seen.add(unique)) {
                unique = original.name() + "#" + i;
                // Extremely unlikely to collide again given how unique
                // the suffixes are, but loop defensively.
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
     * its {@code canOpen(Path)} should return (ignored when
     * {@code throwsFromCanOpen} is {@code true}), its
     * {@link AudioSourceProvider#priority() priority()}, and whether
     * its {@code canOpen(Path)} should throw {@link IOException}
     * instead of returning.
     *
     * @param name               non-null, non-empty format name; unique
     *                           within any single generated list
     * @param score              SPI Priority Score in {@code {-1} ∪
     *                           [0, 100]} to return from
     *                           {@code canOpen(Path)}, or any value
     *                           (ignored) when {@code throwsFromCanOpen}
     *                           is {@code true}
     * @param priority           value returned from
     *                           {@link AudioSourceProvider#priority()}
     * @param throwsFromCanOpen  when {@code true}, the provider's
     *                           {@code canOpen(Path)} throws
     *                           {@link IOException} rather than
     *                           returning {@code score}
     */
    record ProviderSpec(
            String name, int score, int priority, boolean throwsFromCanOpen) {

        /**
         * Effective score as seen by {@link AudioSources}: a throwing
         * spec is treated as {@code -1} per Requirement 5.9, so the
         * winner-selection math works against this value.
         */
        int effectiveScore() {
            return throwsFromCanOpen ? -1 : score;
        }

        /** A spec is eligible for winner selection iff its effective
         *  score is in {@code [0, 100]} (Requirement 5.6). */
        boolean isEligible() {
            return effectiveScore() >= 0;
        }
    }

    // ------------------------------------------------------------------
    // Test doubles
    // ------------------------------------------------------------------

    /**
     * Minimal {@link AudioSourceProvider} test double driven by a
     * {@link ProviderSpec}. Each instance owns a unique
     * {@link #stubSource()} so dispatch assertions can use
     * {@code assertSame} to tie a returned {@link AudioSource} back to
     * the provider that produced it.
     */
    private static final class FakeProvider implements AudioSourceProvider {
        private final ProviderSpec spec;
        private final AudioSource stubSource;
        private final AtomicInteger openPathCalls = new AtomicInteger();

        FakeProvider(ProviderSpec spec) {
            this.spec = spec;
            this.stubSource = new StubAudioSource();
        }

        AudioSource stubSource() {
            return stubSource;
        }

        int openPathCallCount() {
            return openPathCalls.get();
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
                throw new IOException("synthetic canOpen failure for "
                        + spec.name());
            }
            return spec.score();
        }

        @Override
        public int canOpen(InputStream stream, String hint) {
            // Not exercised by this property (Path overload only).
            return spec.score();
        }

        @Override
        public AudioSource open(Path path) {
            openPathCalls.incrementAndGet();
            return stubSource;
        }

        @Override
        public AudioSource open(InputStream stream, String hint) {
            // Not exercised by this property (Path overload only).
            return stubSource;
        }
    }

    /** Minimal {@link AudioSource} used only for identity assertions. */
    private static final class StubAudioSource implements AudioSource {
        @Override public int sampleRate() { return 8_000; }
        @Override public int channelCount() { return 1; }
        @Override public int bitDepth() { return 16; }
        @Override public long totalFrames() { return 0L; }
        @Override public boolean canSeek() { return false; }
        @Override public long currentFrame() { return 0L; }
        @Override public int read(double[] buffer, int offset, int length) { return -1; }
        @Override public void seek(long frameIndex) {
            throw new UnsupportedOperationException("StubAudioSource");
        }
        @Override public void close() { /* nothing to release */ }
    }

    /**
     * {@link Handler} capturing {@link LogRecord}s so the property can
     * assert that {@link Level#WARNING WARNING} records naming a
     * throwing provider's {@code formatName()} were emitted
     * (Requirement 5.9).
     */
    private static final class CapturingHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public synchronized void publish(LogRecord record) {
            records.add(record);
        }

        @Override public void flush() { /* no-op */ }
        @Override public void close() { /* no-op */ }

        synchronized List<LogRecord> warnings() {
            List<LogRecord> out = new ArrayList<>();
            for (LogRecord r : records) {
                if (r.getLevel() != null
                        && r.getLevel().intValue() >= Level.WARNING.intValue()) {
                    out.add(r);
                }
            }
            return out;
        }
    }

}
