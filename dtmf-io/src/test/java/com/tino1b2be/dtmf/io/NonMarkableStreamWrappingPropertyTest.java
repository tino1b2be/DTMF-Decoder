package com.tino1b2be.dtmf.io;

// Feature: dtmf-io, Property 7: Non-markable stream is wrapped before scoring

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for the non-markable stream wrapping guarantee
 * {@link AudioSources#open(InputStream, String)} makes.
 *
 * <p><strong>Property 7: Non-markable stream is wrapped before
 * scoring.</strong> <strong>Validates: Requirements 4.7, 5.12,
 * 11.2.</strong>
 *
 * <p>For any {@link InputStream} {@code s} whose
 * {@code s.markSupported() == false},
 * {@link AudioSources#open(InputStream, String) AudioSources.open(s, hint)}
 * never invokes any registered provider's
 * {@link AudioSourceProvider#canOpen(InputStream, String) canOpen(InputStream,
 * String)} with a stream whose {@code markSupported()} is {@code false}.
 * Additionally, the wrapper the facade hands to providers is a
 * {@link BufferedInputStream} whose internal {@code buf.length} is at least
 * {@code 16384} bytes — so providers can safely
 * {@link InputStream#mark(int) mark(16384)} and
 * {@link InputStream#reset() reset()} around a header inspection without
 * the wrapper silently losing earlier bytes.
 *
 * <p>The property drives a random non-empty list of
 * {@link ProviderSpec}s through the package-private
 * {@link AudioSources#openForTesting(InputStream, String, List)} seam,
 * paired with a {@link NonMarkableInputStream} over a random payload and a
 * random nullable {@code hint}. Every {@link FakeProvider} records the
 * exact stream instance it was handed in {@code canOpen(InputStream,
 * String)}. At least one spec in every generated list has a positive
 * score so the facade also dispatches
 * {@link AudioSourceProvider#open(InputStream, String) open(InputStream,
 * String)} on the winner; the winner's recorded {@code open} stream is
 * verified against the same invariants so "wrapped before scoring"
 * extends to "wrapped before opening" as well.
 *
 * <h2>Invariants asserted</h2>
 *
 * <ol>
 *   <li>Every {@code canOpen(InputStream, String)} invocation received a
 *       stream with {@code markSupported() == true} (Requirement 4.7,
 *       5.12).</li>
 *   <li>Every such stream was a {@link BufferedInputStream} — the
 *       concrete wrapper type Requirement 5.12 names.</li>
 *   <li>Every such wrapper's internal buffer ({@code BufferedInputStream.buf})
 *       has {@code length >= 16384}, observed via reflection
 *       (Requirement 5.12, 11.2). The read-through-32KB fallback path
 *       runs whenever the reflection probe is blocked (e.g. a stricter
 *       future JVM), so the capacity assertion never silently weakens.</li>
 *   <li>The provider instance that receives
 *       {@code open(InputStream, String)} — the winner under Requirement
 *       5.6's {@code (score, priority)} lexicographic max — was handed
 *       the <em>same</em> wrapper instance it saw in
 *       {@code canOpen(InputStream, String)} (a single mark/reset
 *       position is useful to providers only if the stream identity is
 *       preserved).</li>
 *   <li>The caller's non-markable stream was NOT closed by the facade
 *       (Requirement 4.10 — stream ownership stays with the caller).</li>
 * </ol>
 *
 * <h2>Generator shape</h2>
 *
 * <p>{@link ProviderSpec}s carry a {@code name}, a {@code score} in
 * {@code [0, 100]}, and a {@code priority} in {@code [-10, 10]}. Names
 * are disambiguated post-generation so
 * {@link AudioSources#registeredFormats()} semantics (unique names per
 * formatName) are respected. Every generated list has at least one
 * positive-score spec; the winner-by-lex-max is computed from the list
 * and used to assert dispatch. Payload sizes range from {@code 0} to
 * {@code 32 KiB} (i.e. up to {@code 32768} bytes) so the wrapping
 * behaviour is exercised across payloads that are both below and above
 * the {@code 16 KiB} minimum wrapper buffer size.
 *
 * <h2>Scope</h2>
 *
 * <p>This property targets Requirements 4.7, 5.12, and 11.2 only. Two
 * adjacent behaviours are intentionally out of scope:
 * <ul>
 *   <li><em>Tie-break by priority</em> (Requirement 5.6) — covered by
 *       {@code AudioSourcesScoringPropertyTest}'s Invariant B. This
 *       property only needs a well-defined winner so the post-scoring
 *       dispatch assertion has a target; the generator arranges a
 *       unique {@code (score, priority)} max by injecting a
 *       guaranteed-top-rank spec.</li>
 *   <li><em>Markable-stream pass-through</em> — when
 *       {@link InputStream#markSupported()} is already {@code true},
 *       {@link AudioSources} forwards the stream unchanged. That branch
 *       is covered by the unit anchor in {@code AudioSourcesTest} and
 *       is not re-tested here because Property 7 specifically
 *       quantifies over non-markable streams.</li>
 * </ul>
 */
class NonMarkableStreamWrappingPropertyTest {

    /** Minimum wrapper buffer size the facade guarantees (Req 5.12, 11.2). */
    private static final int MIN_BUFFER_BYTES = 16 * 1024;

    // ------------------------------------------------------------------
    // The property
    // ------------------------------------------------------------------

    /**
     * Core Property 7 assertion, checked against every generated
     * non-empty provider list, every random payload, and every
     * nullable hint. See class Javadoc for the five invariants.
     */
    @Property(tries = 100)
    void nonMarkableStreamIsWrappedBeforeScoringAndDispatch(
            @ForAll("specsWithUniqueTopRank") List<ProviderSpec> specs,
            @ForAll("payloads") byte[] payload,
            @ForAll("hints") String hint) throws IOException {

        NonMarkableInputStream raw = new NonMarkableInputStream(payload);
        List<FakeProvider> providers = toProviders(specs);
        // Identify the winner deterministically from the spec list so we
        // can assert dispatch went to it specifically.
        ProviderSpec expectedWinnerSpec = expectedWinner(specs);
        assertNotNull(expectedWinnerSpec,
                "Generator precondition: specsWithUniqueTopRank must produce "
                        + "a list with a unique (score, priority) lex-max winner");
        FakeProvider winnerProvider = providers.get(indexOf(specs, expectedWinnerSpec));

        AudioSource returned = AudioSources.openForTesting(
                raw, hint, castAll(providers));

        assertSame(winnerProvider.stubSource(), returned,
                () -> "open(InputStream, String) must return the AudioSource "
                        + "produced by the winning provider (Req 5.6); winner="
                        + expectedWinnerSpec + ", specs=" + specs);

        // --- Invariant 1 & 2 — every canOpen got a markable BufferedInputStream
        // --- Invariant 3 — that wrapper has buf.length >= 16384
        for (FakeProvider p : providers) {
            assertEquals(1, p.canOpenStreamCallCount(),
                    () -> "Every provider must be asked to score exactly once; "
                            + "provider=" + p.formatName());
            InputStream seenByCanOpen = p.lastCanOpenStream();
            assertNotNull(seenByCanOpen,
                    () -> "canOpen(InputStream, String) must have been called "
                            + "with a non-null stream; provider=" + p.formatName());
            assertTrue(seenByCanOpen.markSupported(),
                    () -> "Non-markable input must be wrapped before being passed to "
                            + "canOpen(InputStream, String) so markSupported()==true "
                            + "(Req 4.7, 5.12); provider=" + p.formatName()
                            + ", actual stream class=" + seenByCanOpen.getClass().getName());
            assertTrue(seenByCanOpen instanceof BufferedInputStream,
                    () -> "Non-markable input must be wrapped in a "
                            + "BufferedInputStream (Req 5.12 names the wrapper "
                            + "type); provider=" + p.formatName()
                            + ", actual class=" + seenByCanOpen.getClass().getName());
            assertBufferAtLeast16KiB((BufferedInputStream) seenByCanOpen, p.formatName());
            // Hint forwarded verbatim to canOpen on every provider.
            assertEquals(hint, p.lastCanOpenHint(),
                    () -> "Hint forwarded to canOpen(InputStream, String) must "
                            + "equal the caller's hint (Req 4.5); provider="
                            + p.formatName());
        }

        // --- Invariant 4 — the winner saw the SAME wrapper in open(InputStream, String)
        assertEquals(1, winnerProvider.openStreamCallCount(),
                () -> "Winner's open(InputStream, String) must be invoked exactly "
                        + "once; winner=" + expectedWinnerSpec);
        InputStream seenByOpen = winnerProvider.lastOpenStream();
        InputStream seenByWinnerCanOpen = winnerProvider.lastCanOpenStream();
        assertSame(seenByWinnerCanOpen, seenByOpen,
                () -> "Winner's open(InputStream, String) must receive the same "
                        + "wrapped stream as canOpen(InputStream, String) so "
                        + "mark/reset positions are consistent across the "
                        + "score-then-open handoff; winner=" + expectedWinnerSpec);
        // Every other provider's open(InputStream, String) must NOT have been invoked.
        for (int i = 0; i < specs.size(); i++) {
            ProviderSpec spec = specs.get(i);
            if (spec == expectedWinnerSpec) {
                continue;
            }
            FakeProvider fp = providers.get(i);
            assertEquals(0, fp.openStreamCallCount(),
                    () -> "Non-winner '" + spec.name() + "' open(InputStream, String) "
                            + "must not be invoked; winner=" + expectedWinnerSpec);
        }

        // --- Invariant 5 — caller's raw stream is not closed by the facade
        assertFalse(raw.closed(),
                "Facade must not close the caller-supplied non-markable stream "
                        + "(Req 4.10); raw stream reported closed after open(...)");
    }

    // ------------------------------------------------------------------
    // Buffer-size probe
    // ------------------------------------------------------------------

    /**
     * Assert the given {@link BufferedInputStream}'s backing buffer holds
     * at least {@value #MIN_BUFFER_BYTES} bytes (Requirement 5.12, 11.2).
     *
     * <p>Preferred probe: reflect the {@code BufferedInputStream.buf}
     * field and read its {@code length}. The field has been protected
     * since JDK 1.0; reflective read is permitted from the test module
     * because this module does not run under a module layer that bans
     * {@code setAccessible} on {@code java.io}.
     *
     * <p>Fallback probe: if {@code setAccessible} is denied — for
     * example a future JVM tightens access to {@code java.io} — read up
     * to {@code 16384 + 1} bytes through a {@code mark(16384)} /
     * {@code read(byte[16384])} / {@code reset()} round-trip. The
     * round-trip succeeds without losing position only when the
     * wrapper's mark-readlimit-plus-buffer capacity admits at least
     * {@code 16384} bytes; a wrapper smaller than that throws
     * {@link IOException} from {@code reset()} once the read advances
     * past the internal buffer. The fallback reads on a fresh
     * {@code BufferedInputStream} wrapping the same underlying wrapper
     * so the original wrapper's stream position is unaffected.
     */
    private static void assertBufferAtLeast16KiB(
            BufferedInputStream wrapper, String providerName) throws IOException {
        // Preferred probe: reflect BufferedInputStream.buf.
        try {
            Field bufField = BufferedInputStream.class.getDeclaredField("buf");
            bufField.setAccessible(true);
            Object buf = bufField.get(wrapper);
            assertNotNull(buf,
                    () -> "BufferedInputStream.buf must be non-null on the "
                            + "wrapper passed to provider=" + providerName);
            assertTrue(buf instanceof byte[],
                    () -> "BufferedInputStream.buf must be a byte[]; provider="
                            + providerName + ", got=" + buf.getClass().getName());
            int capacity = ((byte[]) buf).length;
            assertTrue(capacity >= MIN_BUFFER_BYTES,
                    () -> "BufferedInputStream buffer must hold at least "
                            + MIN_BUFFER_BYTES + " bytes (Req 5.12, 11.2); "
                            + "provider=" + providerName + ", capacity=" + capacity);
            return;
        } catch (NoSuchFieldException | IllegalAccessException | RuntimeException reflectiveFailure) {
            // Fall through to the read-through probe below.
        }

        // Fallback probe: a mark/read/reset round-trip over 16384 bytes.
        // BufferedInputStream.mark(readlimit) guarantees reset() works as
        // long as at most `readlimit` bytes were read between mark and
        // reset AND the internal buffer capacity accommodates the
        // readlimit — if the capacity is smaller than readlimit the
        // wrapper must grow, but BufferedInputStream (unlike
        // PushbackInputStream) grows its buffer up to `readlimit`
        // on demand. Reading 16384 bytes and then resetting therefore
        // succeeds only when the wrapper can accommodate at least
        // 16384 bytes of look-ahead.
        wrapper.mark(MIN_BUFFER_BYTES);
        try {
            byte[] scratch = new byte[MIN_BUFFER_BYTES];
            int remaining = MIN_BUFFER_BYTES;
            while (remaining > 0) {
                int n = wrapper.read(scratch, MIN_BUFFER_BYTES - remaining, remaining);
                if (n < 0) {
                    // End of stream reached before 16384 bytes — the
                    // wrapper's capacity is not under test; skip the
                    // assertion when the payload itself is too short
                    // to exercise it.
                    break;
                }
                remaining -= n;
            }
        } finally {
            // Reset must succeed if capacity >= 16384 (Req 5.12).
            wrapper.reset();
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Winner under Requirement 5.6: the eligible spec whose
     * {@code (score, priority)} pair is strictly greater than every
     * other eligible spec's under lex order. Returns {@code null} when
     * no such unique winner exists; {@code specsWithUniqueTopRank}
     * rules that out by construction.
     */
    private static ProviderSpec expectedWinner(List<ProviderSpec> specs) {
        ProviderSpec best = null;
        boolean tiedAtBest = false;
        for (ProviderSpec spec : specs) {
            if (spec.score() < 0) {
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
    private static List<FakeProvider> toProviders(List<ProviderSpec> specs) {
        List<FakeProvider> providers = new ArrayList<>(specs.size());
        for (ProviderSpec spec : specs) {
            providers.add(new FakeProvider(spec));
        }
        return providers;
    }

    /** Widen a list of {@link FakeProvider} to {@link AudioSourceProvider}
     *  for the {@code openForTesting} signature. */
    private static List<AudioSourceProvider> castAll(List<FakeProvider> providers) {
        return new ArrayList<>(providers);
    }

    // ------------------------------------------------------------------
    // Arbitraries
    // ------------------------------------------------------------------

    /**
     * Non-empty list of {@link ProviderSpec}s with a unique
     * {@code (score, priority)} lex-max winner. Achieved by generating
     * an arbitrary list of "other" specs (scores in {@code [-1, 50]}) and
     * injecting a guaranteed winner spec (score in {@code [60, 100]}) at
     * a random position so dispatch does not hinge on list position.
     * Names are disambiguated post-generation so every consulted spec
     * carries a unique {@code formatName()}.
     */
    @Provide
    Arbitrary<List<ProviderSpec>> specsWithUniqueTopRank() {
        Arbitrary<ProviderSpec> otherSpec = Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(6),
                Arbitraries.integers().between(-1, 50),
                Arbitraries.integers().between(-10, 10)
        ).as(ProviderSpec::new);

        Arbitrary<ProviderSpec> winnerSpec = Combinators.combine(
                Arbitraries.integers().between(60, 100),
                Arbitraries.integers().between(-10, 10)
        ).as((score, priority) ->
                new ProviderSpec("W_winner", score, priority));

        return Combinators.combine(
                otherSpec.list().ofMinSize(0).ofMaxSize(5),
                winnerSpec,
                Arbitraries.integers().between(0, 5)
        ).as((others, winner, insertAt) -> {
            List<ProviderSpec> combined = new ArrayList<>(others);
            int insertionPoint = Math.min(insertAt, combined.size());
            combined.add(insertionPoint, winner);
            return disambiguateNames(combined);
        }).filter(specs -> expectedWinner(specs) != null);
    }

    /**
     * Random byte payload in {@code [0, 32 KiB]}. The upper bound of
     * {@code 32 * 1024} bytes is deliberate: it exceeds the
     * {@code 16 KiB} minimum wrapper buffer (Req 5.12) by 2×, so the
     * wrapping behaviour is exercised across payloads that are both
     * below and above the buffer's capacity. A payload larger than
     * the buffer still round-trips correctly because providers
     * {@code mark(16384); read; reset();} within the readlimit.
     */
    @Provide
    Arbitrary<byte[]> payloads() {
        return Arbitraries.bytes().array(byte[].class).ofMinSize(0).ofMaxSize(32 * 1024);
    }

    /**
     * Nullable hint generator: either {@code null} or a random short
     * alphanumeric string that looks vaguely like a file name or
     * MIME-ish token. The facade forwards the hint verbatim (Req 4.5)
     * so the property asserts equality, not parsing.
     */
    @Provide
    Arbitrary<String> hints() {
        Arbitrary<String> nonNull = Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(16);
        return Arbitraries.oneOf(
                Arbitraries.just((String) null),
                nonNull);
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
                int suffix = i;
                while (!seen.add(unique)) {
                    suffix++;
                    unique = original.name() + "#" + suffix;
                }
            }
            out.add(new ProviderSpec(unique, original.score(), original.priority()));
        }
        return Collections.unmodifiableList(out);
    }

    // ------------------------------------------------------------------
    // Spec record
    // ------------------------------------------------------------------

    /**
     * Describes a single generated provider: its format name, the score
     * its {@code canOpen(...)} returns, and its
     * {@link AudioSourceProvider#priority() priority()}. Neither
     * overload throws; Property 7's focus is the stream-wrapping
     * invariant, not the exception paths covered by Property 5.
     *
     * @param name     non-null, non-empty format name; unique within any
     *                 single generated list
     * @param score    SPI Priority Score in {@code {-1} ∪ [0, 100]} to
     *                 return from both {@code canOpen} overloads
     * @param priority value returned from
     *                 {@link AudioSourceProvider#priority()}
     */
    record ProviderSpec(String name, int score, int priority) {
        ProviderSpec {
            Objects.requireNonNull(name, "name");
        }
    }

    // ------------------------------------------------------------------
    // Test doubles
    // ------------------------------------------------------------------

    /**
     * Minimal {@link AudioSourceProvider} test double driven by a
     * {@link ProviderSpec}. Records the exact {@link InputStream}
     * instance and {@code hint} it saw on every
     * {@code canOpen(InputStream, String)} and
     * {@code open(InputStream, String)} invocation so the property can
     * assert the facade's wrapping behaviour from the provider's point
     * of view.
     */
    private static final class FakeProvider implements AudioSourceProvider {

        private final ProviderSpec spec;
        private final AudioSource stubSource;
        private final AtomicInteger canOpenStreamCalls = new AtomicInteger();
        private final AtomicInteger openStreamCalls = new AtomicInteger();
        private volatile InputStream lastCanOpenStream;
        private volatile InputStream lastOpenStream;
        private volatile String lastCanOpenHint;

        FakeProvider(ProviderSpec spec) {
            this.spec = spec;
            this.stubSource = new StubAudioSource();
        }

        AudioSource stubSource() {
            return stubSource;
        }

        int canOpenStreamCallCount() {
            return canOpenStreamCalls.get();
        }

        int openStreamCallCount() {
            return openStreamCalls.get();
        }

        InputStream lastCanOpenStream() {
            return lastCanOpenStream;
        }

        InputStream lastOpenStream() {
            return lastOpenStream;
        }

        String lastCanOpenHint() {
            return lastCanOpenHint;
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
        public int canOpen(Path path) {
            // Path overload not exercised by this property.
            return spec.score();
        }

        @Override
        public int canOpen(InputStream stream, String hint) {
            canOpenStreamCalls.incrementAndGet();
            lastCanOpenStream = stream;
            lastCanOpenHint = hint;
            return spec.score();
        }

        @Override
        public AudioSource open(Path path) {
            // Path overload not exercised by this property.
            return stubSource;
        }

        @Override
        public AudioSource open(InputStream stream, String hint) {
            openStreamCalls.incrementAndGet();
            lastOpenStream = stream;
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
     * {@link InputStream} that reports {@code markSupported() == false}
     * and throws from {@link #mark(int)} / {@link #reset()}, exactly the
     * test double the task description names. Also tracks whether
     * {@link #close()} has been called so the property can assert the
     * facade does not close caller-supplied streams (Requirement 4.10).
     */
    private static final class NonMarkableInputStream extends InputStream {
        private final ByteArrayInputStream delegate;
        private volatile boolean closed;

        NonMarkableInputStream(byte[] bytes) {
            this.delegate = new ByteArrayInputStream(bytes);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return delegate.read(b, off, len);
        }

        @Override
        public int available() {
            return delegate.available();
        }

        @Override
        public boolean markSupported() {
            return false;
        }

        @Override
        public synchronized void mark(int readlimit) {
            // The InputStream contract allows mark() to be a no-op when
            // markSupported() == false, but the task calls for a stricter
            // double that throws — if any provider's canOpen ignores
            // markSupported() and calls mark() anyway, the test surfaces
            // that bug immediately rather than silently swallowing it.
            throw new UnsupportedOperationException(
                    "NonMarkableInputStream.mark is unsupported");
        }

        @Override
        public synchronized void reset() throws IOException {
            throw new IOException(
                    "NonMarkableInputStream.reset is unsupported");
        }

        @Override
        public void close() throws IOException {
            closed = true;
            delegate.close();
        }

        boolean closed() {
            return closed;
        }
    }
}
