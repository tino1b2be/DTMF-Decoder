package com.tino1b2be.dtmf.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tino1b2be.dtmf.DtmfConfig;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TwistEvaluator}.
 *
 * <p>Pins down the three behaviours the detector relies on:
 *
 * <ul>
 *   <li>the twist formula matches ITU-T Q.24's {@code 10 * log10(H / L)},</li>
 *   <li>a zero low-group energy rejects under any finite tolerance, and</li>
 *   <li>the standard Q.24 bounds ({@code +4 dB} / {@code -8 dB}) loaded via
 *       {@link DtmfConfig#forTelephony()} accept an equal-energy pair and
 *       reject a 10 dB imbalance either direction.</li>
 * </ul>
 */
class TwistEvaluatorTest {

    private static final DtmfConfig STD = DtmfConfig.forTelephony();

    @Test
    void equalEnergiesProduceZeroDbAndAreAccepted() {
        double twist = TwistEvaluator.twistDb(1.0, 1.0);
        assertEquals(0.0, twist);
        assertTrue(TwistEvaluator.withinTolerance(twist, STD),
                "0 dB twist must lie within Standard_Twist bounds");
    }

    @Test
    void highTenTimesLowProducesPlusTenDbAndIsRejected() {
        // 10 * log10(10) == 10 dB, which exceeds the forward bound (+4 dB).
        double twist = TwistEvaluator.twistDb(1.0, 10.0);
        assertEquals(10.0, twist, 1e-12);
        assertFalse(TwistEvaluator.withinTolerance(twist, STD),
                "+10 dB twist must be rejected under +4/-8 Standard_Twist");
    }

    @Test
    void highOneTenthOfLowProducesMinusTenDbAndIsRejected() {
        // 10 * log10(0.1) == -10 dB, which is below the reverse bound (-8 dB).
        double twist = TwistEvaluator.twistDb(1.0, 0.1);
        assertEquals(-10.0, twist, 1e-12);
        assertFalse(TwistEvaluator.withinTolerance(twist, STD),
                "-10 dB twist must be rejected under +4/-8 Standard_Twist");
    }

    @Test
    void zeroLowEnergyReturnsPositiveInfinityAndIsRejected() {
        double twist = TwistEvaluator.twistDb(0.0, 1.0);
        assertEquals(Double.POSITIVE_INFINITY, twist);
        assertFalse(TwistEvaluator.withinTolerance(twist, STD),
                "+Infinity twist must be rejected under any finite tolerance");
    }

    @Test
    void zeroLowEnergyIsRejectedEvenWhenHighIsAlsoZero() {
        // Defensive check: if both peaks are zero the candidate is silence,
        // not a DTMF pair. The contract specifies +Infinity so the tolerance
        // branch always rejects regardless of the high energy.
        double twist = TwistEvaluator.twistDb(0.0, 0.0);
        assertEquals(Double.POSITIVE_INFINITY, twist);
        assertFalse(TwistEvaluator.withinTolerance(twist, STD));
    }
}
