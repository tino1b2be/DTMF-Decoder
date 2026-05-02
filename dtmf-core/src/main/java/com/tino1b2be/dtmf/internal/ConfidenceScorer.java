package com.tino1b2be.dtmf.internal;

/**
 * Confidence scoring for a candidate DTMF tone pair.
 *
 * <p>The confidence score reported on every emitted
 * {@link com.tino1b2be.dtmf.DtmfTone} is the fraction of the in-band
 * (all-eight-DTMF) energy captured by the two peak bins:
 *
 * <pre>
 *   confidence = clamp(
 *       (peakLowEnergy + peakHighEnergy) / (ε + sumAllEight),
 *       0.0, 1.0)
 * </pre>
 *
 * <p>where {@code ε = 1e-12} guards against a division by zero on pure
 * silence. A clean DTMF pair with no energy in the other six DTMF bins
 * scores ≈ {@code 1.0}; white noise spread roughly equally across all eight
 * bins scores ≈ {@code 0.25}; pure silence scores {@code 0.0}.
 *
 * <p>The detection threshold ({@link
 * com.tino1b2be.dtmf.DtmfConfig#detectionThreshold()}) is compared against
 * this same ratio before a candidate is promoted to a confirmed tone, so
 * the interpretation of the score is stable across reporting and gating.
 *
 * <p>Although the type is {@code public} so {@code AnalysisPipeline} in the
 * same package and the {@code dtmf-core} tests in
 * {@code com.tino1b2be.dtmf.internal} can call it, the convention is that
 * {@code com.tino1b2be.dtmf.internal.*} is not part of the published API.
 *
 * @since 2.0.0
 */
public final class ConfidenceScorer {

    /**
     * Epsilon added to the denominator so pure silence scores {@code 0.0}
     * cleanly instead of {@code NaN}. The value is small enough that any
     * realistic in-band energy dominates it.
     */
    static final double EPSILON = 1e-12;

    private ConfidenceScorer() { }

    /**
     * Compute the confidence score for a candidate pair.
     *
     * @param peakLowEnergy      magnitude-squared of the picked low-group peak;
     *                           non-negative
     * @param peakHighEnergy     magnitude-squared of the picked high-group peak;
     *                           non-negative
     * @param sumAllEightEnergies sum of magnitude-squared over all eight DTMF
     *                           bins; non-negative
     * @return a value in {@code [0.0, 1.0]} reporting how much of the in-band
     *         energy is concentrated in the two peak bins
     */
    public static double compute(double peakLowEnergy,
                                 double peakHighEnergy,
                                 double sumAllEightEnergies) {
        double raw = (peakLowEnergy + peakHighEnergy) / (EPSILON + sumAllEightEnergies);
        if (raw < 0.0) {
            return 0.0;
        }
        if (raw > 1.0) {
            return 1.0;
        }
        return raw;
    }
}
