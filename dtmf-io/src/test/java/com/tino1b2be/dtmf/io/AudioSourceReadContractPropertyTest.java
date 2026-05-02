package com.tino1b2be.dtmf.io;

// Feature: dtmf-io, Property 1: AudioSource.read contract

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based test for the {@link AudioSource#read(double[], int, int)}
 * contract.
 *
 * <p><strong>Property 1: {@code AudioSource.read} contract.</strong>
 * <strong>Validates: Requirements 3.6, 3.7, 3.8, 3.13, 3.15.</strong>
 *
 * <p>For any {@link AudioSource} implementation {@code s}, any valid
 * {@code double[] buf}, any {@code offset ∈ [0, buf.length]}, and any
 * frame {@code length} such that
 * {@code offset + length * channelCount() ≤ buf.length}:
 *
 * <ul>
 *   <li>{@code s.read(buf, offset, length)} returns either {@code -1}
 *       (end of stream) or an integer {@code n ∈ [0, length]}
 *       (Req 3.6).</li>
 *   <li>Every sample written into the caller's buffer is in
 *       {@code [-1.0 - 1e-12, 1.0 + 1e-12]} (Req 3.6 normalisation).
 *       The {@code 1e-12} slack matches the spec's stated bound; for
 *       the PCM16 stand-in used in this file, values are bit-exact in
 *       {@code [-1.0, +1.0)}, well inside the tolerance.</li>
 *   <li>After a successful read of {@code n} frames,
 *       {@code s.currentFrame()} equals the prior value plus {@code n}
 *       (Req 3.13).</li>
 *   <li>{@code s.read(buf)} is observationally equivalent to
 *       {@code s.read(buf, 0, buf.length)} (Req 3.8). "Observationally
 *       equivalent" means: the same count is returned, the same
 *       samples are written to the same indices, and
 *       {@code currentFrame()} advances by the same amount.</li>
 *   <li>For stereo sources ({@code channelCount() == 2}), frame
 *       {@code k} of a read lands at {@code buf[offset + 2k]} (left)
 *       and {@code buf[offset + 2k + 1]} (right); the two channels
 *       are not swapped (Req 3.7).</li>
 *   <li>The caller-supplied {@code double[]} is not retained after
 *       {@code read(...)} returns (Req 3.15). Once the method has
 *       returned, the source does not write into that array on any
 *       subsequent {@code read(...)} that targets a different
 *       buffer — even if the old array is mutated in the meantime.</li>
 * </ul>
 *
 * <h2>Implementation under test</h2>
 *
 * {@link RawPcmAudioSource} is the stand-in {@link AudioSource}
 * implementation driven by this property. WAV and MP3 sources are
 * exercised in later stages of the {@code dtmf-io} plan; all three
 * share the same {@code SampleConversion} decode path, so a single
 * property with a mono arm and a stereo arm covers the contract that
 * unifies them. The property picks a fixed PCM16 little-endian signed
 * format so the decoded {@code double} samples are guaranteed to
 * fall inside {@code [-1.0, 1.0]} and the stereo channel-position
 * checks can compare decoded values against bytes assembled
 * independently from the raw buffer. Other
 * {@code (bitDepth, byteOrder, encoding)} tuples are already covered
 * by the normalisation property in {@code RawPcmAudioSourcePropertyTest};
 * repeating them here would add byte-assembly code without a
 * distinct guarantee.
 */
class AudioSourceReadContractPropertyTest {

    /** Tolerance from the spec's stated normalisation range,
     *  {@code [-1.0 - 1e-12, 1.0 + 1e-12]}. */
    private static final double EPSILON = 1e-12;

    /** Sentinel pre-fill written to buffers before each read so we can
     *  detect out-of-slice writes. {@code Double.MIN_VALUE} is ≈
     *  {@code 4.9e-324}, outside the range any legal PCM16 sample can
     *  produce ({@code [-1.0, 1.0)} exactly), so a slot still equal
     *  to the sentinel means the source did not touch it. */
    private static final double PRE_FILL = Double.MIN_VALUE;

    // ------------------------------------------------------------------
    // Mono arm — single-channel source
    // ------------------------------------------------------------------

    /**
     * Mono arm of Property 1. Drives a {@link RawPcmAudioSource} of
     * random PCM16 LE signed bytes through a sequence of
     * {@code read(buf, offset, length)} calls with random offsets,
     * lengths, and buffer sizes, and verifies every contract invariant
     * after each call. A separate sub-check also verifies Req 3.8 by
     * calling the no-offset overload {@code read(buf)} and comparing
     * it to {@code read(buf, 0, buf.length)} on the same cursor
     * position.
     *
     * <p>The call count is capped at ten per draw: enough to exercise
     * "read past EOS returns {@code -1}" on the smaller source draws
     * without bloating each jqwik iteration.
     */
    @Property(tries = 100)
    void readContractHoldsForMonoSource(
            @ForAll("pcm16Bytes") byte[] rawBytes,
            @ForAll @IntRange(min = 1, max = 10) int numReadCalls,
            @ForAll long readPlanSeed) throws IOException {

        final int channelCount = 1;
        final int bytesPerFrame = 2; // PCM16 mono = 2 bytes per frame
        byte[] data = trimOrPadToFrameBoundary(rawBytes, bytesPerFrame);
        long totalFrames = data.length / bytesPerFrame;

        try (RawPcmAudioSource source = new RawPcmAudioSource(
                data, 8_000, 16, ByteOrder.LITTLE_ENDIAN,
                channelCount, PcmEncoding.SIGNED_INT)) {

            // Independent reference decoding of every frame as PCM16 LE
            // signed integers divided by 2^15. Used to verify that the
            // samples written by read(...) match the bytes at the
            // corresponding offset within `data`.
            double[] referenceFrames = decodePcm16LeSignedAsDoubles(data);
            assertEquals((int) totalFrames, referenceFrames.length,
                    "reference decode must produce totalFrames samples (mono)");

            // Req 3.8 sub-check: read(buf) ≡ read(buf, 0, buf.length).
            // Exercised only in the mono arm, where `buf.length` frames
            // fit in a `buf.length`-element buffer; in the stereo arm
            // the default-implementation delegation
            //   read(buf) → read(buf, 0, buf.length)
            // reads `buf.length` FRAMES, which requires
            // `buf.length * 2` SAMPLES and always trips the three-arg
            // overload's index check. The delegation is pure Java
            // interface code and channel-independent, so mono coverage
            // is sufficient for Req 3.8.
            verifyNoOffsetOverloadEquivalence(source, totalFrames);
            source.seek(0L);

            runReadPlanAndVerifyContract(
                    source, referenceFrames, channelCount,
                    numReadCalls, readPlanSeed);
        }
    }

    // ------------------------------------------------------------------
    // Stereo arm — two-channel source
    // ------------------------------------------------------------------

    /**
     * Stereo arm of Property 1. Verifies every mono-arm invariant
     * except the no-offset overload equivalence (see mono arm Javadoc
     * for why), plus the interleaving layout: frame {@code k} lands at
     * {@code buf[offset + 2k]} (left) and {@code buf[offset + 2k + 1]}
     * (right), never the other way around (Req 3.7).
     */
    @Property(tries = 100)
    void readContractHoldsForStereoSource(
            @ForAll("pcm16Bytes") byte[] rawBytes,
            @ForAll @IntRange(min = 1, max = 10) int numReadCalls,
            @ForAll long readPlanSeed) throws IOException {

        final int channelCount = 2;
        final int bytesPerFrame = 2 * channelCount; // PCM16 stereo = 4 bytes/frame
        byte[] data = trimOrPadToFrameBoundary(rawBytes, bytesPerFrame);

        try (RawPcmAudioSource source = new RawPcmAudioSource(
                data, 8_000, 16, ByteOrder.LITTLE_ENDIAN,
                channelCount, PcmEncoding.SIGNED_INT)) {

            // Reference samples, interleaved left, right, left, right,
            // ...; index 2k is left of frame k, index 2k + 1 is right.
            double[] referenceSamples = decodePcm16LeSignedAsDoubles(data);
            long totalFrames = data.length / bytesPerFrame;
            assertEquals((int) (totalFrames * channelCount),
                    referenceSamples.length,
                    "reference decode must produce totalFrames * 2 samples (stereo)");

            runReadPlanAndVerifyContract(
                    source, referenceSamples, channelCount,
                    numReadCalls, readPlanSeed);
        }
    }

    // ------------------------------------------------------------------
    // Buffer-not-retained arm — Req 3.15
    // ------------------------------------------------------------------

    /**
     * Req 3.15: the source must not keep a reference to a caller's
     * {@code double[]} across {@code read(...)} calls. Concretely:
     *
     * <ol>
     *   <li>Allocate buffer {@code A}; call {@code read(A, ...)}.</li>
     *   <li>Overwrite every position of {@code A} with a sentinel
     *       value ({@code Double.NaN}) that no legal decoded sample
     *       can equal.</li>
     *   <li>Allocate a fresh buffer {@code B}; call
     *       {@code read(B, ...)}.</li>
     *   <li>The second call must write into {@code B} and leave
     *       {@code A} untouched — every slot of {@code A} must still
     *       hold the sentinel.</li>
     * </ol>
     *
     * <p>If the source had kept a reference to {@code A} (e.g. cached
     * it as a scratchpad and wrote {@code B}'s samples into it), the
     * sentinel in {@code A} would be overwritten and the comparison
     * would fail.
     *
     * <p>The mutable-state surface a buffer retention would exhibit
     * is the same across formats, so PCM16 mono and stereo are
     * sufficient; other formats are redundant here and are already
     * driven by the normalisation property.
     */
    @Property(tries = 100)
    void readDoesNotRetainCallerBuffer(
            @ForAll @IntRange(min = 2, max = 64) int numFrames,
            @ForAll @IntRange(min = 1, max = 2) int channelCount)
            throws IOException {

        int bytesPerFrame = 2 * channelCount;
        // Deterministic, non-trivial byte pattern; PCM16 decode of any
        // 2-byte window produces a finite double in [-1.0, 1.0), never
        // NaN (our sentinel).
        byte[] data = new byte[numFrames * bytesPerFrame];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) ((i * 37 + 1) & 0xFF);
        }

        try (RawPcmAudioSource source = new RawPcmAudioSource(
                data, 8_000, 16, ByteOrder.LITTLE_ENDIAN,
                channelCount, PcmEncoding.SIGNED_INT)) {

            int samplesTotal = numFrames * channelCount;
            double[] bufferA = new double[samplesTotal];
            int read1 = source.read(bufferA, 0, numFrames);
            assertEquals(numFrames, read1,
                    "first read should return every frame when buffer is"
                            + " large enough");

            // Seek back so the source has more to deliver.
            source.seek(0L);

            // Overwrite bufferA with a sentinel. If the source retained
            // a reference to it, the next read would clobber our
            // sentinel; if it did not retain it, the sentinel survives.
            Arrays.fill(bufferA, Double.NaN);

            double[] bufferB = new double[samplesTotal];
            int read2 = source.read(bufferB, 0, numFrames);
            assertEquals(numFrames, read2,
                    "second read should also return every frame");

            // bufferA must still be all NaN — the source must not have
            // touched it during the second read.
            for (int i = 0; i < bufferA.length; i++) {
                final int idx = i;
                final double observed = bufferA[idx];
                assertTrue(
                        Double.isNaN(observed),
                        () -> "Source retained a reference to the caller's"
                                + " buffer: bufferA[" + idx + "] was"
                                + " overwritten with " + observed
                                + " by the second read (should still be the"
                                + " NaN sentinel)");
            }

            // Sanity check: bufferB actually got samples.
            for (int i = 0; i < bufferB.length; i++) {
                final int idx = i;
                final double observed = bufferB[idx];
                assertTrue(
                        !Double.isNaN(observed),
                        () -> "bufferB[" + idx + "] is NaN; the source failed"
                                + " to write decoded samples into the second"
                                + " buffer");
            }
        }
    }

    // ------------------------------------------------------------------
    // Shared read-plan driver
    // ------------------------------------------------------------------

    /**
     * Drive a sequence of {@code read(...)} calls against {@code source}
     * with random (but valid) {@code (offset, length, bufferSize)}
     * triples, and verify every invariant of Property 1 after each
     * call.
     *
     * <p>The driver starts at {@code currentFrame() == 0}; after an
     * EOS return it breaks out of the loop, so {@code numReadCalls}
     * is an upper bound, not an exact count.
     */
    private static void runReadPlanAndVerifyContract(
            AudioSource source,
            double[] referenceSamples,
            int channelCount,
            int numReadCalls,
            long seed) throws IOException {

        Random rng = new Random(seed);
        long totalFrames = source.totalFrames();

        for (int call = 0; call < numReadCalls; call++) {
            // Pick a random length of frames to request. Zero is allowed
            // and is a legal no-op (returns 0 when not at EOS, -1 at EOS).
            int requestedFrames;
            if (rng.nextInt(8) == 0) {
                requestedFrames = 0;
            } else {
                long remaining = Math.max(1L, totalFrames - source.currentFrame());
                // Range [1, ~1.5x remaining] so we exercise both
                // partial-reads and read-past-end.
                int cap = (int) Math.min(
                        Integer.MAX_VALUE / Math.max(1, channelCount),
                        Math.max(1L, remaining * 3L / 2L));
                requestedFrames = 1 + rng.nextInt(Math.max(1, cap));
            }

            // Allocate a buffer big enough to hold the requested frames
            // plus a random offset in front and random slack after
            // (exercising non-zero offsets and trailing-slot invariance).
            int offset = rng.nextInt(4);
            long requiredLong = (long) offset
                    + (long) requestedFrames * (long) channelCount
                    + rng.nextInt(4);
            int bufSize = requiredLong > Integer.MAX_VALUE
                    ? Integer.MAX_VALUE
                    : Math.max((int) requiredLong, 1);
            double[] buf = new double[bufSize];
            Arrays.fill(buf, PRE_FILL);

            long priorFrame = source.currentFrame();
            int n = source.read(buf, offset, requestedFrames);

            // Req 3.6: n == -1 (EOS) or n ∈ [0, length].
            {
                final int finalN = n;
                final int finalReq = requestedFrames;
                assertTrue(
                        n == -1 || (n >= 0 && n <= requestedFrames),
                        () -> "read(...) returned " + finalN
                                + " which is outside {-1} ∪ [0, "
                                + finalReq + "]");
            }

            if (n == -1) {
                // At EOS, currentFrame() must not advance.
                assertEquals(
                        priorFrame, source.currentFrame(),
                        "currentFrame() must not advance after EOS");
                // For a finite-length source, EOS means we were already
                // at totalFrames (or just reached it); RawPcmAudioSource
                // only returns -1 when remaining == 0, i.e. cursor ==
                // totalFrames.
                assertEquals(
                        totalFrames, source.currentFrame(),
                        "at EOS, currentFrame() must equal totalFrames()"
                                + " for a finite source");
                break;
            }

            // Req 3.13: currentFrame() advanced by exactly n.
            {
                final int finalN = n;
                final long finalPrior = priorFrame;
                assertEquals(
                        priorFrame + n, source.currentFrame(),
                        () -> "currentFrame() must advance by exactly n="
                                + finalN + " after read(...); was "
                                + finalPrior + ", expected "
                                + (finalPrior + finalN) + ", got "
                                + source.currentFrame());
            }

            // Req 3.6 normalisation + Req 3.7 interleaving: every
            // sample written to buf[offset .. offset + n*channelCount)
            // must match the reference, channel-by-channel.
            for (int frame = 0; frame < n; frame++) {
                long sourceFrameIndex = priorFrame + frame;
                for (int ch = 0; ch < channelCount; ch++) {
                    int bufIndex = offset + frame * channelCount + ch;
                    double actual = buf[bufIndex];
                    double expected = referenceSamples[
                            (int) (sourceFrameIndex * channelCount + ch)];

                    // Req 3.6: samples are in [-1.0 - 1e-12, 1.0 + 1e-12].
                    {
                        final double finalActual = actual;
                        final int finalBufIdx = bufIndex;
                        assertTrue(
                                actual >= -1.0 - EPSILON
                                        && actual <= 1.0 + EPSILON,
                                () -> "sample at buf[" + finalBufIdx + "] = "
                                        + finalActual + " is outside"
                                        + " [-1.0 - 1e-12, 1.0 + 1e-12]");
                    }

                    // Interleaving/correctness: each slot holds the
                    // reference decode of that (frame, channel).
                    {
                        final int finalFrame = frame;
                        final int finalCh = ch;
                        final int finalBufIdx = bufIndex;
                        final double finalActual = actual;
                        final double finalExpected = expected;
                        assertTrue(
                                Math.abs(actual - expected) <= EPSILON,
                                () -> "interleaving mismatch at frame="
                                        + finalFrame + ", channel="
                                        + finalCh + ": buf[" + finalBufIdx
                                        + "]=" + finalActual + ", reference="
                                        + finalExpected);
                    }
                }

                // Req 3.7, stereo swap-detection: whenever the
                // reference left and right for this frame differ,
                // assert the buffer's odd slot does not hold the
                // reference left (which would be a silent L/R swap).
                if (channelCount == 2) {
                    double refL = referenceSamples[(int) (sourceFrameIndex * 2)];
                    double refR = referenceSamples[(int) (sourceFrameIndex * 2 + 1)];
                    if (Math.abs(refL - refR) > EPSILON) {
                        double actualR = buf[offset + frame * 2 + 1];
                        final int finalFrame = frame;
                        final double finalActualR = actualR;
                        final double finalRefL = refL;
                        final double finalRefR = refR;
                        assertNotEquals(
                                refL, actualR, EPSILON,
                                () -> "channels appear swapped at frame "
                                        + finalFrame + ": buf[offset + 2k + 1]="
                                        + finalActualR + " equals reference"
                                        + " left=" + finalRefL + " (expected"
                                        + " reference right=" + finalRefR + ")");
                    }
                }
            }

            // Slots outside [offset, offset + n*channelCount) must not
            // have been touched; they still hold the pre-fill sentinel.
            // This cross-checks that read(...) wrote exactly n frames
            // and did not scribble outside the caller-requested slice,
            // which would otherwise corrupt the caller's buffer.
            for (int i = 0; i < offset; i++) {
                final int idx = i;
                final double observed = buf[idx];
                assertEquals(
                        PRE_FILL, observed, 0.0,
                        () -> "read(...) wrote before offset: buf[" + idx
                                + "] changed from pre-fill to " + observed);
            }
            int lastWritten = offset + n * channelCount;
            for (int i = lastWritten; i < buf.length; i++) {
                final int idx = i;
                final double observed = buf[idx];
                assertEquals(
                        PRE_FILL, observed, 0.0,
                        () -> "read(...) wrote past n*channelCount: buf["
                                + idx + "] changed from pre-fill to "
                                + observed + " (lastWritten=" + lastWritten
                                + ")");
            }
        }
    }

    /**
     * Req 3.8: {@code s.read(buf)} behaves identically to
     * {@code s.read(buf, 0, buf.length)}. We verify this on a
     * freshly-positioned source (cursor at 0): capture the read count
     * and written slice of {@code read(buf)}, seek back to 0, call the
     * three-arg overload with the same {@code (0, buf.length)} pair,
     * and compare return values, written buffers, and resulting
     * {@code currentFrame()}.
     *
     * <p>Only called with a mono source (see the mono arm Javadoc for
     * why); the interface's default implementation passes
     * {@code buf.length} as a frame count, not a sample count, which
     * makes the equivalence vacuous for stereo buffers whose length
     * is not a multiple of {@code channelCount}.
     *
     * @param source       mono source positioned at frame 0
     * @param totalFrames  source's total frame count
     */
    private static void verifyNoOffsetOverloadEquivalence(
            AudioSource source, long totalFrames) throws IOException {

        int bufferFrames = (int) Math.min(8L, Math.max(1L, totalFrames));
        // Mono: one sample per frame.
        double[] bufA = new double[bufferFrames];
        Arrays.fill(bufA, PRE_FILL);

        long startFrame = source.currentFrame();
        int readA = source.read(bufA);
        long afterA = source.currentFrame();

        // Rewind and invoke the three-arg overload with the same
        // (offset=0, length=buf.length) pair the default impl passes.
        source.seek(startFrame);
        assertEquals(startFrame, source.currentFrame(),
                "seek(startFrame) must restore currentFrame");

        double[] bufB = new double[bufferFrames];
        Arrays.fill(bufB, PRE_FILL);
        int readB = source.read(bufB, 0, bufB.length);
        long afterB = source.currentFrame();

        assertEquals(readA, readB,
                "read(buf) must return the same count as"
                        + " read(buf, 0, buf.length)");
        assertEquals(afterA, afterB,
                "currentFrame() after read(buf) must equal currentFrame()"
                        + " after read(buf, 0, buf.length)");
        for (int i = 0; i < bufferFrames; i++) {
            final int idx = i;
            final double a = bufA[idx];
            final double b = bufB[idx];
            assertEquals(
                    a, b, 0.0,
                    () -> "read(buf) and read(buf, 0, buf.length) wrote"
                            + " different values at index " + idx
                            + ": read(buf)=" + a + ", read(buf, 0, n)=" + b);
        }
    }

    // ------------------------------------------------------------------
    // Arbitraries
    // ------------------------------------------------------------------

    /**
     * A byte array in the range {@code [2, 1024]} bytes; the driver
     * trims it to a multiple of {@code bytesPerFrame} for the
     * source's channel configuration. A minimum of two bytes
     * guarantees at least one PCM16 mono frame even in the shortest
     * draw (stereo gets padded to one 4-byte frame when a 2-byte
     * draw arrives).
     */
    @Provide
    Arbitrary<byte[]> pcm16Bytes() {
        return Arbitraries.bytes()
                .array(byte[].class)
                .ofMinSize(2)
                .ofMaxSize(1024);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Reference decode of {@code data} as PCM16 little-endian signed
     * integers divided by {@code 2^15 = 32768}. Assembles each 2-byte
     * window by plain bit shifts and sign extension; this is a
     * different code path from the production {@code SampleConversion}
     * dispatcher and so catches drift between the implementation and
     * the contract.
     *
     * <p>The interleaved layout of the output follows the raw byte
     * order directly: for stereo PCM16 LE the bytes are
     * {@code [L_lo, L_hi, R_lo, R_hi, L_lo, L_hi, R_lo, R_hi, ...]},
     * so decoding successive 2-byte windows into successive
     * {@code double} slots produces the same interleaved
     * {@code [L, R, L, R, ...]} layout the {@code AudioSource}
     * contract specifies (Req 3.7). That lets the caller index into
     * the returned array as {@code out[k * channelCount + ch]}.
     *
     * @param data raw PCM16 LE bytes; length must be a multiple of
     *             two (and of {@code 2 * channelCount} at the call
     *             site)
     * @return array of interleaved decoded samples, length
     *         {@code data.length / 2}
     */
    private static double[] decodePcm16LeSignedAsDoubles(byte[] data) {
        int numSamples = data.length / 2;
        double[] out = new double[numSamples];
        for (int s = 0; s < numSamples; s++) {
            int lo = data[s * 2] & 0xFF;
            int hi = data[s * 2 + 1]; // keep signed for sign extension
            int signed16 = (hi << 8) | lo;
            out[s] = signed16 / 32768.0;
        }
        return out;
    }

    /**
     * Return a byte array whose length is a positive multiple of
     * {@code bytesPerFrame}. If {@code raw} carries at least one
     * frame, trim to the largest whole-frame prefix; otherwise return
     * a single zero-filled frame so the read driver always has at
     * least one frame to consume.
     */
    private static byte[] trimOrPadToFrameBoundary(byte[] raw, int bytesPerFrame) {
        int frames = raw.length / bytesPerFrame;
        if (frames == 0) {
            return new byte[bytesPerFrame];
        }
        int len = frames * bytesPerFrame;
        byte[] trimmed = new byte[len];
        System.arraycopy(raw, 0, trimmed, 0, len);
        return trimmed;
    }
}
