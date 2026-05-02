package com.tino1b2be.dtmf.io;

// Feature: dtmf-io, Property 2: AudioSource close and seek lifecycle

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Objects;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

/**
 * Property-based tests for the {@link AudioSource} close and seek
 * lifecycle contract.
 *
 * <p><strong>Property 2: {@code AudioSource} close and seek
 * lifecycle.</strong> <strong>Validates: Requirements 3.9, 3.10, 3.11,
 * 3.12, 3.13, 3.14, 10.11.</strong>
 *
 * <p>For any {@link AudioSource} implementation {@code s}:
 *
 * <ul>
 *   <li>After {@link AudioSource#close() s.close()} returns, any
 *       subsequent call to {@link AudioSource#read(double[], int, int)},
 *       {@link AudioSource#read(double[])}, or
 *       {@link AudioSource#seek(long)} throws {@link IOException} whose
 *       message identifies the source as closed (Requirement 3.14).</li>
 *   <li>{@code close()} itself is idempotent &mdash; a second and
 *       subsequent invocation is a no-op and must not throw
 *       (Requirement 3.14, {@code Closeable} convention).</li>
 *   <li>When {@link AudioSource#canSeek()} returns {@code false}, any
 *       call to {@link AudioSource#seek(long)} throws
 *       {@link UnsupportedOperationException} whose message identifies
 *       the implementing class (Requirements 3.9, 3.11, 10.11).</li>
 *   <li>When {@link AudioSource#canSeek()} returns {@code true}, a
 *       successful {@code seek(f)} with {@code f} in the valid range
 *       leaves {@link AudioSource#currentFrame() s.currentFrame()}
 *       equal to {@code f} and subsequent reads start from frame
 *       {@code f} (Requirements 3.10, 3.13).</li>
 *   <li>A {@code seek(f)} with {@code f < 0} or, when
 *       {@link AudioSource#totalFrames()} is non-negative,
 *       {@code f > totalFrames()}, throws
 *       {@link IllegalArgumentException} whose message identifies the
 *       offending value and the valid range
 *       (Requirement 3.12).</li>
 *   <li>{@code currentFrame()} after a successful read of {@code n}
 *       frames advances by exactly {@code n}; interleaving seek with
 *       read maintains {@code currentFrame()} in lockstep with the
 *       cursor (Requirement 3.13 &mdash; anchored here in combination
 *       with seek, even though the pure read-advances-cursor invariant
 *       is already covered by Property 1).</li>
 * </ul>
 *
 * <h2>Implementations under test</h2>
 *
 * <ul>
 *   <li>{@link RawPcmAudioSource} is the seekable ({@code canSeek()
 *       == true}) real implementation driven by every
 *       seek-related property in this file. It is the only
 *       seekable {@code AudioSource} shipped by {@code dtmf-io}
 *       itself; the WAV provider in {@code dtmf-io-wav} adds another
 *       seekable source, but exercising that would drag in
 *       cross-module compilation order and is covered by that
 *       module's own property tests.</li>
 *   <li>{@link NonSeekableStubAudioSource} is a minimal in-file
 *       {@code AudioSource} implementation backed by a pre-decoded
 *       {@code double[]} array that reports {@code canSeek() ==
 *       false} and whose {@code seek(long)} throws
 *       {@link UnsupportedOperationException} identifying its own
 *       class. It mirrors exactly what the forthcoming MP3 provider
 *       will do (Requirement 10.11) without depending on that
 *       module's classes. The close / post-close / read-advances
 *       invariants apply identically to both seekable and
 *       non-seekable sources, so the stub also exercises those
 *       invariants where the spec does not gate them on
 *       {@code canSeek()}.</li>
 * </ul>
 *
 * <p>The stub mirrors a typical forward-only provider's state machine
 * closely enough that the properties defined here are a useful
 * dress-rehearsal for the MP3 provider's own lifecycle checks in the
 * sibling module.
 */
class AudioSourceLifecyclePropertyTest {

    // ------------------------------------------------------------------
    // Invariant A &mdash; post-close read throws IOException "closed"
    // (Requirement 3.14)
    // ------------------------------------------------------------------

    /**
     * After {@code close()} returns, {@code read(...)} on a previously
     * usable source throws {@link IOException} whose message mentions
     * "closed" so callers can distinguish it from a disk-level I/O
     * failure.
     *
     * <p>The property varies the factory for the source under test
     * (seekable vs non-seekable) and the overload under test
     * ({@code read(buf)} vs {@code read(buf, off, len)}) so both
     * implementations and both overloads get the same treatment: the
     * post-close rule is stated in Requirement 3.14 without any
     * {@code canSeek()} qualification.
     */
    @Property(tries = 100)
    void postCloseReadThrowsIOExceptionMentioningClosed(
            @ForAll("seedForSourceShape") long shapeSeed,
            @ForAll boolean useThreeArgOverload,
            @ForAll boolean useSeekableSource) throws IOException {

        AudioSource source = makeSource(useSeekableSource, shapeSeed);
        source.close();

        double[] buffer = new double[4 * source.channelCount()];

        IOException thrown;
        if (useThreeArgOverload) {
            thrown = assertThrows(IOException.class,
                    () -> source.read(buffer, 0, 1),
                    () -> "read(double[], int, int) on a closed "
                            + source.getClass().getSimpleName()
                            + " must throw IOException");
        } else {
            thrown = assertThrows(IOException.class,
                    () -> source.read(buffer),
                    () -> "read(double[]) on a closed "
                            + source.getClass().getSimpleName()
                            + " must throw IOException");
        }

        assertMessageMentionsClosed(thrown, "read");
    }

    // ------------------------------------------------------------------
    // Invariant B &mdash; post-close seek throws IOException "closed"
    // (Requirement 3.14)
    // ------------------------------------------------------------------

    /**
     * After {@code close()} returns, {@code seek(...)} must throw
     * {@link IOException} identifying the source as closed. This holds
     * even on non-seekable sources: post-close takes precedence over
     * {@link UnsupportedOperationException} because a closed source
     * has no state in which to interpret any call. That ordering
     * matches Requirement 3.14's unconditional language ("IF the
     * caller invokes {@code read(...)} or {@code seek(...)} after
     * {@code close()}, THEN {@code IOException}") against
     * Requirement 3.11's conditional language (gated on {@code
     * canSeek() == false}), and it matches the present
     * {@link RawPcmAudioSource} implementation, which checks {@code
     * closed} before any other preconditions.
     */
    @Property(tries = 100)
    void postCloseSeekThrowsIOExceptionMentioningClosed(
            @ForAll("seedForSourceShape") long shapeSeed,
            @ForAll boolean useSeekableSource,
            @ForAll @LongRange(min = 0L, max = 8L) long seekTarget) throws IOException {

        AudioSource source = makeSource(useSeekableSource, shapeSeed);
        source.close();

        IOException thrown = assertThrows(IOException.class,
                () -> source.seek(seekTarget),
                () -> "seek(" + seekTarget + ") on a closed "
                        + source.getClass().getSimpleName()
                        + " must throw IOException");

        assertMessageMentionsClosed(thrown, "seek");
    }

    // ------------------------------------------------------------------
    // Invariant C &mdash; close() is idempotent (Requirement 3.14,
    // Closeable convention)
    // ------------------------------------------------------------------

    /**
     * Calling {@code close()} more than once is a no-op after the first
     * call: neither throws, and the post-close behaviour of
     * {@code read(...)} / {@code seek(...)} is unchanged by repeated
     * closure. jqwik varies the number of repeated {@code close()}
     * calls between 2 and 5 so a regression where {@code close()}
     * increments a counter or flips a state on every call would show
     * up as a failing property with a small shrunk counterexample.
     */
    @Property(tries = 100)
    void closeIsIdempotent(
            @ForAll("seedForSourceShape") long shapeSeed,
            @ForAll boolean useSeekableSource,
            @ForAll @IntRange(min = 2, max = 5) int numCloses) throws IOException {

        AudioSource source = makeSource(useSeekableSource, shapeSeed);

        for (int i = 0; i < numCloses; i++) {
            // The first call performs the close; every subsequent call
            // must be a silent no-op. The assertion is that none of
            // them throw.
            try {
                source.close();
            } catch (IOException | RuntimeException ex) {
                final int attempt = i;
                fail(() -> "close() call #" + (attempt + 1) + " on "
                        + source.getClass().getSimpleName()
                        + " threw " + ex.getClass().getSimpleName()
                        + ": " + ex.getMessage()
                        + " (close() must be idempotent)");
            }
        }

        // After N closes, the source remains closed: read and seek
        // still throw IOException. We re-anchor this here so a broken
        // implementation that inadvertently re-opens itself on a
        // second close() would fail this property, not just the two
        // post-close properties above.
        double[] buf = new double[4 * source.channelCount()];
        IOException readException = assertThrows(IOException.class,
                () -> source.read(buf, 0, 1),
                "read(...) after multiple close() calls must still"
                        + " throw IOException");
        assertMessageMentionsClosed(readException, "read");
    }

    // ------------------------------------------------------------------
    // Invariant D &mdash; seek on a non-seekable source throws
    // UnsupportedOperationException naming the implementing class
    // (Requirements 3.9, 3.11, 10.11)
    // ------------------------------------------------------------------

    /**
     * A source whose {@code canSeek()} returns {@code false} must throw
     * {@link UnsupportedOperationException} from {@code seek(long)},
     * and the exception message must identify the implementing class
     * by simple name (Requirement 3.11 and Requirement 10.11, which is
     * the specific instance for {@code Mp3AudioSource}).
     *
     * <p>This property exercises the in-file
     * {@link NonSeekableStubAudioSource}; the forthcoming
     * {@code Mp3AudioSource} has its own unit test in the
     * {@code dtmf-io-mp3} module. The stub reproduces the exception
     * contract exactly so a drift in how other non-seekable sources
     * identify themselves would show up as a failure here.
     */
    @Property(tries = 100)
    void seekOnNonSeekableThrowsUnsupportedNamingClass(
            @ForAll("seedForSourceShape") long shapeSeed,
            @ForAll @LongRange(min = -4L, max = 64L) long seekTarget) throws IOException {

        try (NonSeekableStubAudioSource source = new NonSeekableStubAudioSource(
                decodePcm16MonoLe(samplesFromSeed(shapeSeed, 2)),
                /* sampleRate */ 44_100,
                /* channelCount */ 1,
                /* bitDepth */ 16)) {

            assertTrue(!source.canSeek(),
                    "stub must report canSeek()==false to make this"
                            + " property meaningful");

            UnsupportedOperationException thrown = assertThrows(
                    UnsupportedOperationException.class,
                    () -> source.seek(seekTarget),
                    () -> "seek(" + seekTarget + ") on a non-seekable"
                            + " source must throw"
                            + " UnsupportedOperationException");

            String message = Objects.requireNonNullElse(thrown.getMessage(), "");
            String expectedClassName = source.getClass().getSimpleName();
            assertTrue(
                    message.contains(expectedClassName),
                    () -> "UnsupportedOperationException from seek(" + seekTarget
                            + ") must mention the implementing class '"
                            + expectedClassName + "', got: " + message);
        }
    }

    // ------------------------------------------------------------------
    // Invariant E &mdash; valid seek(f) updates currentFrame() to f
    // (Requirements 3.10, 3.13)
    // ------------------------------------------------------------------

    /**
     * For a seekable source, a valid {@code seek(f)} in the closed
     * interval {@code [0, totalFrames()]} leaves
     * {@code currentFrame() == f}.
     *
     * <p>The property also verifies the post-seek read: reading from
     * frame {@code f} produces exactly the same samples that an
     * independent pre-seek read of the whole source produced at the
     * corresponding positions. This catches a regression where
     * {@code seek} updates {@code currentFrame()} but fails to reset
     * the backing cursor (or vice versa) &mdash; a class of bug
     * Requirement 3.13 specifically targets in combination with
     * Requirement 3.10.
     */
    @Property(tries = 100)
    void validSeekUpdatesCurrentFrameAndNextRead(
            @ForAll("seedForSourceShape") long shapeSeed,
            @ForAll double seekFraction) throws IOException {

        byte[] data = samplesFromSeed(shapeSeed, /* bytesPerFrame */ 2);
        final int sampleRate = 8_000;
        final int channelCount = 1;

        try (RawPcmAudioSource source = new RawPcmAudioSource(
                data, sampleRate, 16,
                ByteOrder.LITTLE_ENDIAN, channelCount,
                PcmEncoding.SIGNED_INT)) {

            long total = source.totalFrames();
            // Pre-decode every sample so we can compare the post-seek
            // read's output against the ground-truth samples at the
            // target position.
            double[] reference = new double[(int) total * channelCount];
            int prefilled = source.read(reference, 0, (int) total);
            assertEquals((int) total, prefilled,
                    "pre-decode must produce every frame");
            assertEquals(total, source.currentFrame(),
                    "currentFrame must be totalFrames after a full read");

            // Pick a valid seek target f ∈ [0, total]. Endpoints
            // included because seeking to totalFrames positions the
            // cursor at EOS, which is still a valid state
            // (Requirement 3.12's "STRICTLY greater than totalFrames").
            long target = Math.round(clamp(seekFraction, 0.0, 1.0) * total);

            source.seek(target);
            assertEquals(target, source.currentFrame(),
                    () -> "currentFrame() after seek(" + target
                            + ") must equal the seek target");

            // If the target is strictly less than total, a subsequent
            // read must reproduce reference[target * channelCount ..]
            // exactly. If target == total, read must return -1 (EOS)
            // without advancing currentFrame.
            if (target < total) {
                int framesRemaining = (int) (total - target);
                double[] postSeek = new double[framesRemaining * channelCount];
                int n = source.read(postSeek, 0, framesRemaining);
                assertEquals(framesRemaining, n,
                        "read after seek(target) must return every"
                                + " remaining frame when buffer is large"
                                + " enough");
                for (int i = 0; i < postSeek.length; i++) {
                    double expected = reference[(int) target * channelCount + i];
                    double actual = postSeek[i];
                    final int idx = i;
                    assertEquals(expected, actual, 0.0,
                            () -> "post-seek read diverged from"
                                    + " pre-seek reference at index "
                                    + idx + ": expected=" + expected
                                    + ", actual=" + actual);
                }
                assertEquals(total, source.currentFrame(),
                        "after consuming the rest, currentFrame ="
                                + " totalFrames");
            } else {
                // Seek to EOS; next read must return -1 and not advance.
                double[] buf = new double[channelCount];
                int n = source.read(buf, 0, 1);
                assertEquals(-1, n,
                        "read after seek(totalFrames) must return -1 (EOS)");
                assertEquals(total, source.currentFrame(),
                        "currentFrame must not advance after an EOS read");
            }
        }
    }

    // ------------------------------------------------------------------
    // Invariant F &mdash; out-of-range seek throws IllegalArgumentException
    // identifying the offending value and the valid range
    // (Requirement 3.12)
    // ------------------------------------------------------------------

    /**
     * A {@code seek(f)} with {@code f} outside the valid range
     * ({@code f < 0} or {@code f > totalFrames()} when totalFrames is
     * non-negative) must throw {@link IllegalArgumentException} whose
     * message identifies both the offending value and the valid
     * range.
     *
     * <p>jqwik shrinks toward the boundary (typically {@code -1} and
     * {@code totalFrames + 1}), which is where implementation bugs
     * that use the wrong comparison operator ({@code <} vs {@code
     * <=}, or {@code >} vs {@code >=}) tend to hide.
     */
    @Property(tries = 100)
    void outOfRangeSeekThrowsIllegalArgumentWithRange(
            @ForAll("seedForSourceShape") long shapeSeed,
            @ForAll @LongRange(min = -1_000_000L, max = 1_000_000L) long rawTarget) throws IOException {

        byte[] data = samplesFromSeed(shapeSeed, /* bytesPerFrame */ 2);

        try (RawPcmAudioSource source = new RawPcmAudioSource(
                data, 8_000, 16,
                ByteOrder.LITTLE_ENDIAN, /* channelCount */ 1,
                PcmEncoding.SIGNED_INT)) {

            long total = source.totalFrames();

            // Only keep the out-of-range draws; in-range draws are
            // exercised by Invariant E above, and throwing them out
            // here would add noise (jqwik counts them against the
            // 100-tries budget).
            if (rawTarget >= 0L && rawTarget <= total) {
                return;
            }

            IllegalArgumentException thrown = assertThrows(
                    IllegalArgumentException.class,
                    () -> source.seek(rawTarget),
                    () -> "seek(" + rawTarget + ") must throw"
                            + " IllegalArgumentException when out of range"
                            + " [0, " + total + "]");

            String message = Objects.requireNonNullElse(thrown.getMessage(), "");
            // Message must name the offending value and the valid
            // range. The spec does not pin the exact wording, but
            // both pieces of information must be present.
            assertTrue(
                    message.contains(Long.toString(rawTarget)),
                    () -> "IllegalArgumentException message must name"
                            + " the offending value " + rawTarget
                            + ", got: " + message);
            assertTrue(
                    message.contains("0") && message.contains(Long.toString(total)),
                    () -> "IllegalArgumentException message must name"
                            + " the valid range [0, " + total + "], got: "
                            + message);
            // currentFrame() must not have moved: a rejected seek
            // leaves the cursor where it was.
            assertEquals(0L, source.currentFrame(),
                    "a rejected seek must not move currentFrame()");
        }
    }

    // ------------------------------------------------------------------
    // Invariant G &mdash; currentFrame() after read advances correctly
    // in combination with seek (Requirement 3.13)
    // ------------------------------------------------------------------

    /**
     * Interleaving {@code seek} and {@code read} keeps
     * {@code currentFrame()} in exact lockstep with the cursor: after
     * each {@code read(buf, 0, n)} the cursor advances by the
     * returned frame count, and after each {@code seek(f)} the cursor
     * equals {@code f}. Already tested in Property 1 for pure reads;
     * re-checked here "in combination with seek" per the task brief.
     */
    @Property(tries = 100)
    void currentFrameTracksReadsAndSeeks(
            @ForAll("seedForSourceShape") long shapeSeed,
            @ForAll @IntRange(min = 1, max = 6) int steps,
            @ForAll long stepSeed) throws IOException {

        byte[] data = samplesFromSeed(shapeSeed, /* bytesPerFrame */ 2);

        try (RawPcmAudioSource source = new RawPcmAudioSource(
                data, 8_000, 16,
                ByteOrder.LITTLE_ENDIAN, /* channelCount */ 1,
                PcmEncoding.SIGNED_INT)) {

            long total = source.totalFrames();
            long expectedCursor = 0L;
            java.util.Random rng = new java.util.Random(stepSeed);

            for (int step = 0; step < steps; step++) {
                // Alternate: even step seeks, odd step reads.
                if ((step & 1) == 0) {
                    long target = total == 0 ? 0L : nextLongInRange(rng, 0L, total);
                    source.seek(target);
                    expectedCursor = target;
                    assertEquals(expectedCursor, source.currentFrame(),
                            () -> "currentFrame() must equal the"
                                    + " seek target immediately after"
                                    + " a valid seek");
                } else {
                    long remaining = total - expectedCursor;
                    if (remaining == 0L) {
                        // At EOS: read returns -1 and cursor stays put.
                        int n = source.read(new double[4], 0, 4);
                        assertEquals(-1, n,
                                "read at EOS must return -1");
                        assertEquals(expectedCursor, source.currentFrame(),
                                "currentFrame() must not advance after"
                                        + " an EOS read");
                        continue;
                    }
                    int requested = 1 + rng.nextInt((int) Math.min(
                            (long) Integer.MAX_VALUE, remaining));
                    double[] buf = new double[requested];
                    int n = source.read(buf, 0, requested);
                    assertTrue(n > 0 && n <= requested,
                            () -> "read must return a positive frame"
                                    + " count not exceeding the"
                                    + " requested length; got " + n);
                    expectedCursor += n;
                    assertEquals(expectedCursor, source.currentFrame(),
                            "currentFrame() must advance by exactly"
                                    + " the returned frame count");
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    /**
     * Sources are built from a seed so jqwik can shrink the shape
     * parameter independently of the lifecycle parameters. The shape
     * only affects frame count (driving whether EOS is reached) and
     * byte contents (affecting the post-seek read comparison); none of
     * the lifecycle invariants depend on its exact value.
     */
    @Provide
    Arbitrary<Long> seedForSourceShape() {
        return Arbitraries.longs().between(0L, Long.MAX_VALUE);
    }

    /**
     * Build either a {@link RawPcmAudioSource} ({@code canSeek() ==
     * true}) or a {@link NonSeekableStubAudioSource} ({@code canSeek()
     * == false}) at the same shape, so properties that hold across
     * both can parameterise a single code path.
     */
    private static AudioSource makeSource(boolean seekable, long seed) {
        byte[] data = samplesFromSeed(seed, /* bytesPerFrame */ 2);
        if (seekable) {
            return new RawPcmAudioSource(
                    data, 8_000, 16,
                    ByteOrder.LITTLE_ENDIAN, /* channelCount */ 1,
                    PcmEncoding.SIGNED_INT);
        }
        // Non-seekable stub uses the same pre-decoded sample count
        // but bypasses the RawPcm code path, so a bug in one does
        // not mask a bug in the other.
        double[] preDecoded = decodePcm16MonoLe(data);
        return new NonSeekableStubAudioSource(
                preDecoded, /* sampleRate */ 8_000,
                /* channelCount */ 1, /* bitDepth */ 16);
    }

    /**
     * Deterministic byte sequence for a given seed and frame size. We
     * target roughly 1..48 frames per source, which is small enough
     * that the properties can exhaustively walk the frame range
     * inside a single jqwik try but large enough that EOS-read and
     * partial-read paths both get exercised.
     */
    private static byte[] samplesFromSeed(long seed, int bytesPerFrame) {
        java.util.Random rng = new java.util.Random(seed);
        int frames = 1 + rng.nextInt(48);
        byte[] out = new byte[frames * bytesPerFrame];
        rng.nextBytes(out);
        return out;
    }

    /**
     * Hand decode PCM16 little-endian mono to double. Used only to
     * pre-populate the {@link NonSeekableStubAudioSource} so it can
     * hand back real samples; the lifecycle properties never compare
     * the stub's decoded output against this reference, so a
     * simplified decoder is sufficient here.
     */
    private static double[] decodePcm16MonoLe(byte[] data) {
        double[] out = new double[data.length / 2];
        for (int i = 0; i < out.length; i++) {
            int lo = data[i * 2] & 0xFF;
            int hi = data[i * 2 + 1]; // signed, for sign extension
            int value16 = (hi << 8) | lo;
            out[i] = value16 / 32768.0;
        }
        return out;
    }

    /**
     * Assert that an {@link IOException} raised from a post-close
     * operation identifies the source as closed. Requirement 3.14
     * requires "identifying the source as closed"; we look for the
     * lower-case substring "closed" in the message because that's
     * the unambiguous flag for the condition. A message that said
     * only "source unavailable" would pass a lax check but would not
     * satisfy the spec's intent that the caller can tell a post-close
     * misuse apart from a disk-level error.
     */
    private static void assertMessageMentionsClosed(IOException ex, String operation) {
        String message = Objects.requireNonNullElse(ex.getMessage(), "");
        assertNotNull(ex, () -> "post-close " + operation
                + "(...) must throw a non-null IOException");
        assertTrue(
                message.toLowerCase(java.util.Locale.ROOT).contains("closed"),
                () -> "post-close " + operation
                        + "(...) IOException message must identify"
                        + " the source as closed; got: '"
                        + message + "'");
    }

    /**
     * Draw a uniformly distributed long in the closed interval
     * {@code [min, max]}. Used by the interleaved-seek-and-read
     * property to pick valid seek targets from the source's actual
     * frame range.
     */
    private static long nextLongInRange(java.util.Random rng, long min, long max) {
        if (min == max) {
            return min;
        }
        long span = max - min + 1L;
        // Simple unbiased draw when span fits into the positive long
        // range, which it always does for our source sizes.
        long raw = rng.nextLong();
        if (raw == Long.MIN_VALUE) {
            raw = 0L;
        }
        long positive = Math.abs(raw);
        return min + (positive % span);
    }

    /**
     * Clamp {@code v} to {@code [lo, hi]}. jqwik's double arbitraries
     * can produce NaN and infinities; clamp makes the seek-fraction
     * parameter safe to use as a simple lerp over {@code totalFrames}.
     */
    private static double clamp(double v, double lo, double hi) {
        if (Double.isNaN(v)) return lo;
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    // ==================================================================
    // In-file test stub: a minimal AudioSource whose canSeek() == false.
    // ==================================================================

    /**
     * Minimal in-test {@link AudioSource} implementation backed by a
     * pre-decoded {@code double[]} of interleaved samples. Reports
     * {@code canSeek() == false} and throws
     * {@link UnsupportedOperationException} identifying its own
     * simple class name from {@code seek(long)}, so the
     * "non-seekable contract" property can drive it without pulling
     * in the {@code dtmf-io-mp3} module.
     *
     * <p>The stub is intentionally minimal: no buffering, no
     * decoding, no resource ownership. It exists only to exercise
     * {@code AudioSource}'s declared contract at the interface
     * level. Real non-seekable sources (MP3, future streaming
     * providers) carry additional state and are covered by their
     * own module tests.
     */
    static final class NonSeekableStubAudioSource implements AudioSource {

        private final double[] preDecodedInterleaved;
        private final int sampleRate;
        private final int channelCount;
        private final int bitDepth;
        private final long totalFrames;
        private long frameCursor = 0L;
        private boolean closed = false;

        /**
         * Wrap a pre-decoded interleaved {@code double[]} as an
         * {@code AudioSource} whose {@code canSeek()} returns
         * {@code false}.
         *
         * @param samplesInterleaved interleaved samples; length must be
         *                           a multiple of {@code channelCount}
         * @param sampleRate         sample rate in Hz; {@code > 0}
         * @param channelCount       channel count; {@code >= 1}
         * @param bitDepth           native bit depth to report (the
         *                           samples are already in {@code double}
         *                           form; this value is informational)
         */
        NonSeekableStubAudioSource(double[] samplesInterleaved,
                int sampleRate, int channelCount, int bitDepth) {
            Objects.requireNonNull(samplesInterleaved, "samplesInterleaved");
            if (sampleRate <= 0) {
                throw new IllegalArgumentException(
                        "sampleRate must be positive, was " + sampleRate);
            }
            if (channelCount <= 0) {
                throw new IllegalArgumentException(
                        "channelCount must be positive, was " + channelCount);
            }
            if (samplesInterleaved.length % channelCount != 0) {
                throw new IllegalArgumentException(
                        "samples.length=" + samplesInterleaved.length
                                + " is not a multiple of channelCount="
                                + channelCount);
            }
            this.preDecodedInterleaved = samplesInterleaved;
            this.sampleRate = sampleRate;
            this.channelCount = channelCount;
            this.bitDepth = bitDepth;
            this.totalFrames = samplesInterleaved.length / channelCount;
        }

        @Override public int sampleRate()      { return sampleRate; }
        @Override public int channelCount()    { return channelCount; }
        @Override public int bitDepth()        { return bitDepth; }
        @Override public long totalFrames()    { return totalFrames; }
        @Override public boolean canSeek()     { return false; }
        @Override public long currentFrame()   { return frameCursor; }

        @Override
        public int read(double[] buffer, int offset, int length) throws IOException {
            if (closed) {
                throw new IOException(
                        getClass().getSimpleName() + " is closed");
            }
            Objects.requireNonNull(buffer, "buffer");
            if (offset < 0 || length < 0) {
                throw new IndexOutOfBoundsException(
                        "offset and length must be non-negative,"
                                + " were offset=" + offset
                                + ", length=" + length);
            }
            long remaining = totalFrames - frameCursor;
            if (remaining <= 0L) {
                return -1;
            }
            int framesToRead = (int) Math.min((long) length, remaining);
            for (int f = 0; f < framesToRead; f++) {
                for (int c = 0; c < channelCount; c++) {
                    int srcIdx = (int) ((frameCursor + f) * channelCount + c);
                    buffer[offset + f * channelCount + c] =
                            preDecodedInterleaved[srcIdx];
                }
            }
            frameCursor += framesToRead;
            return framesToRead;
        }

        @Override
        public void seek(long frameIndex) throws IOException {
            // Requirement 3.14 takes precedence over 3.11: a closed
            // source throws IOException even for calls that would
            // otherwise throw UnsupportedOperationException. This
            // mirrors RawPcmAudioSource's ordering.
            if (closed) {
                throw new IOException(
                        getClass().getSimpleName() + " is closed");
            }
            throw new UnsupportedOperationException(
                    getClass().getSimpleName()
                            + " does not support seek (canSeek()=false)");
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
