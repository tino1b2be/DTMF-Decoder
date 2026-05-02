package com.tino1b2be.goertzel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Hand-computed unit tests for {@link GoertzelFilter}.
 *
 * <p>Each test picks inputs whose exact Goertzel output can be derived from
 * first principles, so we are comparing the implementation against closed-form
 * reference values rather than against another implementation. The cases:
 *
 * <ul>
 *   <li>Pure sinusoid at the exact bin center. For a real cosine of amplitude
 *       1.0 at frequency {@code f = k * Fs / N} with {@code k} integer and
 *       {@code 0 < k < N/2}, the DFT magnitude at that bin is exactly
 *       {@code N / 2}, so magnitude squared is {@code (N / 2)²}. The Goertzel
 *       output after feeding {@code N} samples matches this DFT bin.</li>
 *   <li>DC input. A constant {@code 1.0} signal has DFT magnitude
 *       {@code N} at frequency 0, and 0 at every other bin (for cosine-basis
 *       bins; Goertzel at any strictly positive off-bin frequency likewise
 *       converges to near zero).</li>
 *   <li>Silence. All zero samples produce zero magnitude squared at every
 *       target frequency.</li>
 *   <li>{@code reset()} zeros accumulators so a second analysis block starts
 *       from a clean state.</li>
 *   <li>{@code acceptAll(samples, offset, length)} is equivalent to calling
 *       {@link GoertzelFilter#accept(double)} in a loop over the same range.</li>
 * </ul>
 *
 * <p>These tests validate Requirement 10.4 (the {@code goertzel} module
 * exposes a usable public Goertzel implementation).
 */
class GoertzelFilterTest {

    /** A sample rate used by most tests; any value works since we pick bin-center frequencies. */
    private static final int SAMPLE_RATE = 8000;

    /**
     * Number of samples per analysis block. Chosen so that every integer
     * {@code k ∈ [1, N/2)} gives a valid bin-center frequency inside
     * {@code (0, Fs/2)} (which {@link GoertzelFilter} requires as
     * {@code [0, Fs/2)}).
     */
    private static final int N = 160;

    /**
     * Tolerance for DFT equalities that only fail through floating-point
     * round-off. {@code 1e-9 * signalEnergy} gives us margin proportional to
     * the magnitudes involved. The spec pins 1e-9 as the scaling factor.
     */
    private static double dftTolerance(double signalEnergy) {
        return 1e-9 * signalEnergy;
    }

    @Test
    void pureSinusoidAtBinCenterHasMagnitudeSquaredNOver2Squared() {
        // Pick k = 5 so the bin center is 5 * 8000 / 160 = 250 Hz, well inside
        // (0, Fs/2) and not at DC or Nyquist.
        int k = 5;
        double binCenterHz = (double) k * SAMPLE_RATE / N;

        double[] samples = new double[N];
        for (int n = 0; n < N; n++) {
            samples[n] = Math.cos(2.0 * Math.PI * k * n / N);
        }

        double signalEnergy = 0.0;
        for (double s : samples) {
            signalEnergy += s * s;
        }

        GoertzelFilter filter = new GoertzelFilter(SAMPLE_RATE, binCenterHz);
        filter.acceptAll(samples);

        double expected = ((double) N / 2.0) * ((double) N / 2.0);
        double actual = filter.magnitudeSquared();

        assertEquals(
                expected,
                actual,
                dftTolerance(signalEnergy),
                "Magnitude² of unit-amplitude cosine at bin center should equal (N/2)²");
    }

    @Test
    void dcInputAtZeroHzGivesMagnitudeSquaredNSquared() {
        // Constant 1.0 signal: |X(0)| = N, so magnitude² = N².
        double[] samples = new double[N];
        for (int n = 0; n < N; n++) {
            samples[n] = 1.0;
        }

        double signalEnergy = N; // Σ 1² = N

        GoertzelFilter filter = new GoertzelFilter(SAMPLE_RATE, 0.0);
        filter.acceptAll(samples);

        double expected = (double) N * (double) N;
        double actual = filter.magnitudeSquared();

        assertEquals(
                expected,
                actual,
                dftTolerance(signalEnergy),
                "Magnitude² of DC input at 0 Hz should equal N²");
    }

    @Test
    void dcInputAtPositiveBinCenterFrequencyIsNearZero() {
        // A constant signal has no energy at any positive bin center (for
        // Goertzel evaluated with integer cycles per block). We pick bin k=3
        // at Fs/N * 3 = 150 Hz so 3 complete cycles fit exactly in N samples.
        int k = 3;
        double binCenterHz = (double) k * SAMPLE_RATE / N;

        double[] samples = new double[N];
        for (int n = 0; n < N; n++) {
            samples[n] = 1.0;
        }

        double signalEnergy = N;

        GoertzelFilter filter = new GoertzelFilter(SAMPLE_RATE, binCenterHz);
        filter.acceptAll(samples);

        assertEquals(
                0.0,
                filter.magnitudeSquared(),
                dftTolerance(signalEnergy),
                "Magnitude² of DC input at any positive bin-center frequency should be ~0");
    }

    @Test
    void silenceGivesZeroMagnitudeSquaredAtAnyTargetFrequency() {
        double[] samples = new double[N]; // all zeros by default

        double[] testFrequencies = {0.0, 250.0, 697.0, 1336.0, 1000.0, 2000.0, 3999.0};
        for (double targetHz : testFrequencies) {
            GoertzelFilter filter = new GoertzelFilter(SAMPLE_RATE, targetHz);
            filter.acceptAll(samples);
            assertEquals(
                    0.0,
                    filter.magnitudeSquared(),
                    0.0,
                    "Silence at " + targetHz + " Hz should give exactly zero magnitude²");
        }
    }

    @Test
    void resetZerosInternalStateSoMagnitudeSquaredReturnsToZero() {
        // Feed a real signal, confirm non-zero energy, reset, confirm zero.
        int k = 7;
        double binCenterHz = (double) k * SAMPLE_RATE / N;

        double[] samples = new double[N];
        for (int n = 0; n < N; n++) {
            samples[n] = Math.cos(2.0 * Math.PI * k * n / N);
        }

        GoertzelFilter filter = new GoertzelFilter(SAMPLE_RATE, binCenterHz);
        filter.acceptAll(samples);

        assertTrue(
                filter.magnitudeSquared() > 0.0,
                "Pre-condition: magnitude² should be non-zero after feeding a real signal");

        filter.reset();

        assertEquals(
                0.0,
                filter.magnitudeSquared(),
                0.0,
                "After reset(), magnitude² should be exactly zero");
    }

    @Test
    void acceptAllWithOffsetAndLengthEqualsAcceptInLoop() {
        // Build an arbitrary signal with distinct per-sample values so any
        // state mismatch between acceptAll and the loop would show up. Then
        // compare on a non-trivial offset/length window.
        double[] samples = new double[N + 32];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = Math.sin(0.31 * i) + 0.5 * Math.cos(0.07 * i);
        }

        int offset = 17;
        int length = N;
        double targetHz = 1000.0;

        GoertzelFilter viaAcceptAll = new GoertzelFilter(SAMPLE_RATE, targetHz);
        viaAcceptAll.acceptAll(samples, offset, length);

        GoertzelFilter viaLoop = new GoertzelFilter(SAMPLE_RATE, targetHz);
        for (int i = offset; i < offset + length; i++) {
            viaLoop.accept(samples[i]);
        }

        // Both paths should produce bit-identical magnitude²: same operations,
        // same order, same inputs.
        assertEquals(
                viaLoop.magnitudeSquared(),
                viaAcceptAll.magnitudeSquared(),
                0.0,
                "acceptAll(samples, offset, length) must match calling accept() in a loop");
    }

    @Test
    void acceptAllWholeArrayEqualsAcceptInLoop() {
        double[] samples = new double[N];
        for (int i = 0; i < N; i++) {
            samples[i] = Math.sin(0.21 * i);
        }

        double targetHz = 500.0;

        GoertzelFilter viaAcceptAll = new GoertzelFilter(SAMPLE_RATE, targetHz);
        viaAcceptAll.acceptAll(samples);

        GoertzelFilter viaLoop = new GoertzelFilter(SAMPLE_RATE, targetHz);
        for (double s : samples) {
            viaLoop.accept(s);
        }

        assertEquals(
                viaLoop.magnitudeSquared(),
                viaAcceptAll.magnitudeSquared(),
                0.0,
                "acceptAll(samples) must match calling accept() in a loop over the whole array");
    }
}
