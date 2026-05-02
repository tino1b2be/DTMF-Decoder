package com.tino1b2be.goertzel;

// Feature: dtmf-v2-foundation, Property 9: GoertzelBank matches reference DFT magnitudes

import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

import org.junit.jupiter.api.Assertions;

/**
 * Property-based tests for {@link GoertzelBank}.
 *
 * <p><strong>Property 9: GoertzelBank matches reference DFT magnitudes.</strong>
 * <strong>Validates: Requirement 10.4.</strong>
 *
 * <p>For random real-valued signals of length 16–1024, at random sample
 * rates in {@code [4000, 48000]} Hz, with 1–8 random target frequencies in
 * {@code (0, Fs/2)}, the magnitude-squared vector produced by
 * {@link GoertzelBank#computeMagnitudesSquaredInto(double[], double[])}
 * agrees with a naive {@code O(N·K)} reference DFT computed from
 * {@link Math#cos(double)} and {@link Math#sin(double)} within an absolute
 * tolerance of {@code 1e-6 * Σ x²}. The tolerance scales with total signal
 * energy because the DFT magnitude squared scales the same way; a relative
 * tolerance keyed to signal energy prevents the test from failing on
 * perfectly legitimate accumulation error on large-amplitude inputs.
 *
 * <p>The property fuzzes three independent dimensions: signal length and
 * content, sample rate, and frequency set. That catches
 * sample-rate-dependent coefficient mistakes, bin-indexing issues, and state
 * leakage in {@code GoertzelBank} — all of which would surface as an
 * off-by-a-lot DFT discrepancy rather than off-by-round-off.
 */
class GoertzelBankPropertyTest {

    /** Minimum sample rate for the test domain. */
    private static final int MIN_FS = 4000;

    /** Maximum sample rate for the test domain. */
    private static final int MAX_FS = 48000;

    /** Minimum signal length for the test domain. */
    private static final int MIN_N = 16;

    /** Maximum signal length for the test domain. */
    private static final int MAX_N = 1024;

    /** Maximum number of target frequencies per case. */
    private static final int MAX_K = 8;

    /**
     * Absolute tolerance scale. The property spec pins {@code 1e-6 * Σ x²} as
     * the allowable absolute difference per bin.
     */
    private static final double TOLERANCE_SCALE = 1e-6;

    @Property(tries = 100)
    void goertzelBankMatchesReferenceDft(
            @ForAll @IntRange(min = MIN_FS, max = MAX_FS) int sampleRate,
            @ForAll @Size(min = MIN_N, max = MAX_N)
            @DoubleRange(min = -1.0, max = 1.0) List<Double> signalList,
            @ForAll("frequencySets") FrequencySet frequencies) {

        // Scale the user-requested target frequencies into the valid range
        // for THIS sample rate: each f must be in (0, Fs/2). The
        // FrequencySet's `ratios` are in (0, 1) so we map them to (0, Fs/2)
        // by multiplying by (Fs/2) and pulling inward a hair to avoid hitting
        // the Nyquist boundary (GoertzelFilter requires f < Fs/2 strictly).
        double nyquist = sampleRate / 2.0;
        double[] targetFrequencies = new double[frequencies.ratios.length];
        for (int i = 0; i < frequencies.ratios.length; i++) {
            double ratio = frequencies.ratios[i];
            // ratio ∈ (0, 1). Multiply by nyquist, then nudge away from both
            // endpoints by a small epsilon so neither 0 nor Nyquist is hit.
            double f = ratio * nyquist;
            double eps = Math.max(1.0, nyquist * 1e-6);
            if (f <= 0.0) f = eps;
            if (f >= nyquist) f = nyquist - eps;
            targetFrequencies[i] = f;
        }

        double[] signal = toPrimitiveArray(signalList);

        // Reference DFT magnitudes² at the target frequencies.
        double[] referenceMagSquared = referenceDftMagnitudeSquared(signal, sampleRate, targetFrequencies);

        // Goertzel bank magnitudes².
        GoertzelBank bank = new GoertzelBank(sampleRate, targetFrequencies);
        double[] bankMagSquared = new double[targetFrequencies.length];
        bank.computeMagnitudesSquaredInto(signal, bankMagSquared);

        // Absolute tolerance = TOLERANCE_SCALE * signal energy. The empty
        // signal case is excluded by @Size(min = 16), so signalEnergy == 0
        // only when every sample is exactly 0.0 — in which case both the
        // reference DFT and the Goertzel bank must produce exact zeros.
        double signalEnergy = 0.0;
        for (double x : signal) {
            signalEnergy += x * x;
        }
        double tolerance = TOLERANCE_SCALE * signalEnergy;

        for (int i = 0; i < targetFrequencies.length; i++) {
            double diff = Math.abs(referenceMagSquared[i] - bankMagSquared[i]);
            if (diff > tolerance) {
                Assertions.fail(
                        "GoertzelBank magnitude² at frequency " + targetFrequencies[i]
                                + " Hz (index " + i + ") differs from reference DFT by "
                                + diff + " which exceeds tolerance "
                                + tolerance + " (scale " + TOLERANCE_SCALE
                                + " × signal energy " + signalEnergy + "). "
                                + "Fs=" + sampleRate + ", N=" + signal.length
                                + ", reference=" + referenceMagSquared[i]
                                + ", bank=" + bankMagSquared[i]);
            }
        }
    }

    /**
     * Provider for a set of 1..{@link #MAX_K} target-frequency ratios, each
     * in {@code (0, 1)}. Ratios rather than absolute frequencies keep the
     * property generator independent of the sample rate, which jqwik draws in
     * a separate parameter; the test body maps ratio → (0, Fs/2) per case.
     */
    @Provide
    Arbitrary<FrequencySet> frequencySets() {
        // ofScale(6) lets the random generator produce 6-decimal ratios, so
        // the lower bound 0.001 and upper bound 0.999 are representable.
        // jqwik's DefaultDoubleArbitrary defaults to scale 2, which can't
        // express the bounds requested here.
        Arbitrary<Double> ratioArb = Arbitraries.doubles().between(0.001, 0.999).ofScale(6);
        return ratioArb.list().ofMinSize(1).ofMaxSize(MAX_K)
                .map(list -> {
                    double[] ratios = new double[list.size()];
                    for (int i = 0; i < list.size(); i++) {
                        ratios[i] = list.get(i);
                    }
                    return new FrequencySet(ratios);
                });
    }

    /**
     * Reference naive DFT magnitude²: for each target frequency {@code f},
     * compute {@code real = Σ x[n]·cos(2π·f·n/Fs)} and
     * {@code imag = -Σ x[n]·sin(2π·f·n/Fs)}, then return {@code real² + imag²}.
     */
    private static double[] referenceDftMagnitudeSquared(double[] signal, int sampleRate, double[] frequencies) {
        double[] result = new double[frequencies.length];
        double twoPiOverFs = 2.0 * Math.PI / sampleRate;
        for (int k = 0; k < frequencies.length; k++) {
            double f = frequencies[k];
            double real = 0.0;
            double imag = 0.0;
            for (int n = 0; n < signal.length; n++) {
                double angle = twoPiOverFs * f * n;
                real += signal[n] * Math.cos(angle);
                imag -= signal[n] * Math.sin(angle);
            }
            result[k] = real * real + imag * imag;
        }
        return result;
    }

    private static double[] toPrimitiveArray(List<Double> list) {
        double[] out = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    /**
     * A set of target-frequency ratios in {@code (0, 1)}, to be scaled to
     * {@code (0, Fs/2)} by the test body. Bundled into a holder class so
     * jqwik shrinks the frequency set as a unit and the test's
     * counter-example printout stays readable.
     */
    static final class FrequencySet {
        final double[] ratios;

        FrequencySet(double[] ratios) {
            this.ratios = ratios;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("FrequencySet[ratios=[");
            for (int i = 0; i < ratios.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(ratios[i]);
            }
            sb.append("]]");
            return sb.toString();
        }
    }
}
