package com.tino1b2be.dtmf.internal;

import com.tino1b2be.dtmf.DtmfConfig;

/**
 * Twist computation and tolerance check for a candidate DTMF tone pair.
 *
 * <p>Per ITU-T Q.24, <em>twist</em> is the power ratio between the high-group
 * tone and the low-group tone of a DTMF pair, expressed in decibels:
 *
 * <pre>
 *   twistDb = 10 * log10(highEnergy / lowEnergy)
 * </pre>
 *
 * <p>A positive twist means the high group is louder (forward twist); a
 * negative twist means the low group is louder (reverse twist). Standard_Twist
 * bounds are {@code +4 dB} forward and {@code -8 dB} reverse; anything outside
 * those bounds is rejected by {@link com.tino1b2be.dtmf.DtmfConfig#forTelephony()}
 * and {@link com.tino1b2be.dtmf.DtmfConfig#defaults()} (Requirement 9.2).
 * The advanced builder exposes custom bounds (Requirement 9.3).
 *
 * <p><strong>Zero-energy handling.</strong> If {@code lowEnergy == 0.0} the
 * ratio is undefined and the low group contributed no signal at all; this
 * cannot be a valid DTMF candidate regardless of the twist configuration.
 * {@link #twistDb(double, double)} returns {@link Double#POSITIVE_INFINITY}
 * in that case so {@link #withinTolerance(double, DtmfConfig)} rejects under
 * any finite forward-twist bound (Requirement 9.4).
 *
 * <p>Although the type is {@code public} so
 * {@code com.tino1b2be.dtmf.internal.AnalysisPipeline} in the same package
 * and the {@code dtmf-core} tests in {@code com.tino1b2be.dtmf.internal} can
 * call it, the convention is that {@code com.tino1b2be.dtmf.internal.*} is
 * not part of the published API.
 *
 * @since 2.0.0
 */
public final class TwistEvaluator {

    private TwistEvaluator() { }

    /**
     * Compute the twist in decibels for a candidate DTMF pair.
     *
     * @param lowEnergy  magnitude-squared of the picked low-group peak;
     *                   non-negative
     * @param highEnergy magnitude-squared of the picked high-group peak;
     *                   non-negative
     * @return {@code 10 * log10(highEnergy / lowEnergy)} when
     *         {@code lowEnergy > 0}; {@link Double#POSITIVE_INFINITY} when
     *         {@code lowEnergy == 0} so any finite tolerance rejects
     */
    public static double twistDb(double lowEnergy, double highEnergy) {
        if (lowEnergy == 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return 10.0 * Math.log10(highEnergy / lowEnergy);
    }

    /**
     * {@return whether the given twist in dB lies within the tolerance band
     *          configured on {@code cfg}}.
     *
     * <p>Returns {@code true} when {@code reverseDb <= twistDb <= forwardDb}
     * where {@code reverseDb} and {@code forwardDb} come from
     * {@link DtmfConfig#reverseTwistDb()} and
     * {@link DtmfConfig#forwardTwistDb()} respectively. A
     * {@link Double#POSITIVE_INFINITY} or {@link Double#NaN} twist is always
     * rejected because neither comparison can evaluate to true.
     *
     * @param twistDb candidate twist in dB (may be infinite)
     * @param cfg     configuration supplying the twist bounds; non-null
     */
    public static boolean withinTolerance(double twistDb, DtmfConfig cfg) {
        return cfg.reverseTwistDb() <= twistDb && twistDb <= cfg.forwardTwistDb();
    }
}
