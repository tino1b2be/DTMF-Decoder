package com.tino1b2be.dtmf.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ConfidenceScorer}.
 *
 * <p>Three canonical scenarios pin down the confidence formula and the
 * clamp:
 *
 * <ul>
 *   <li><strong>Pure DTMF pair</strong>: all energy lives in the two picked
 *       bins, so the score is (approximately) {@code 1.0}.</li>
 *   <li><strong>Equal distribution over eight bins</strong>: the picked
 *       peaks capture exactly two of eight equal shares, so the score is
 *       (approximately) {@code 0.25}.</li>
 *   <li><strong>All zeros</strong>: epsilon rescues the division and the
 *       score is {@code 0.0}.</li>
 * </ul>
 *
 * <p>"Approximately" here just acknowledges the {@code 1e-12} epsilon in the
 * denominator; the score of a purely-in-band pair is
 * {@code S / (ε + S) < 1} by a margin far below any practical threshold.
 */
class ConfidenceScorerTest {

    @Test
    void pureDtmfPairScoresApproximatelyOne() {
        // All in-band energy lives in the two picked peaks. Sum is the sum
        // of the two peaks (the other six bins contributed nothing to the
        // denominator).
        double peakLow = 100.0;
        double peakHigh = 80.0;
        double sumAll = peakLow + peakHigh;

        double conf = ConfidenceScorer.compute(peakLow, peakHigh, sumAll);
        // With epsilon = 1e-12 the ratio is 180 / (1e-12 + 180), which rounds
        // to 1.0 in double precision but is strictly less than 1.0.
        assertEquals(1.0, conf, 1e-12);
    }

    @Test
    void equalEnergyAcrossEightBinsScoresOneQuarter() {
        // Each bin carries the same energy E. The two picked peaks together
        // carry 2E; the denominator is 8E. Ratio = 2/8 = 0.25.
        double perBin = 5.0;
        double sumAll = perBin * 8.0;

        double conf = ConfidenceScorer.compute(perBin, perBin, sumAll);
        // Epsilon shifts the denominator by 1e-12, which is far below any
        // float precision limit at this magnitude; compare with a generous
        // tolerance anyway.
        assertEquals(0.25, conf, 1e-9);
    }

    @Test
    void allZerosScoresZero() {
        // Pure silence: every bin is zero. The epsilon in the denominator
        // makes the ratio 0 / 1e-12 == 0 instead of NaN.
        double conf = ConfidenceScorer.compute(0.0, 0.0, 0.0);
        assertEquals(0.0, conf);
    }

    @Test
    void peaksDominatingSumProduceHighScore() {
        // 90% of the in-band energy lives in the two peaks. Ratio = 0.9.
        double peakLow = 45.0;
        double peakHigh = 45.0;
        double sumAll = 100.0;

        double conf = ConfidenceScorer.compute(peakLow, peakHigh, sumAll);
        assertEquals(0.9, conf, 1e-9);
    }

    @Test
    void resultIsAlwaysInTheUnitInterval() {
        // Sanity check the clamp on a pathological input where the two
        // peaks somehow sum to more than the reported total. The clamp
        // should pin the result at 1.0 rather than letting it escape.
        double conf = ConfidenceScorer.compute(10.0, 10.0, 1.0);
        assertTrue(conf >= 0.0 && conf <= 1.0,
                "confidence must stay in [0, 1], was " + conf);
        assertEquals(1.0, conf);
    }
}
