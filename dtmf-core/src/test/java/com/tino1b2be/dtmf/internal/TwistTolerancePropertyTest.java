package com.tino1b2be.dtmf.internal;

// Feature: dtmf-v2-foundation, Property 12: Twist tolerance is applied exactly as configured

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tino1b2be.dtmf.DtmfConfig;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;

/**
 * Property-based test for
 * {@link TwistEvaluator#withinTolerance(double, DtmfConfig)}.
 *
 * <p><strong>Property 12: Twist tolerance is applied exactly as configured.</strong>
 * <strong>Validates: Requirements 9.3, 9.4.</strong>
 *
 * <p>For random {@code forwardDb > reverseDb} and a random candidate
 * {@code twistDb}, the evaluator accepts iff
 * {@code reverseDb <= twistDb <= forwardDb}. The property guards the custom
 * advanced-builder path (Requirement 9.3) — the detector must apply whatever
 * bounds the caller set, not the Standard_Twist defaults — and guards the
 * rejection contract (Requirement 9.4) for any twist outside the configured
 * band.
 *
 * <p>The test generates the two bounds independently and reconstructs them
 * so {@code forwardDb > reverseDb} holds, satisfying the precondition
 * baked into {@link DtmfConfig}'s canonical constructor.
 */
class TwistTolerancePropertyTest {

    @Property(tries = 200)
    void withinToleranceIsInclusiveBetweenReverseAndForward(
            @ForAll @DoubleRange(min = -20.0, max = 20.0) double boundA,
            @ForAll @DoubleRange(min = -20.0, max = 20.0) double boundB,
            @ForAll @DoubleRange(min = -40.0, max = 40.0) double twistDb,
            @ForAll @DoubleRange(min = 0.01, max = 10.0) double spread) {

        // Construct a valid (reverse, forward) pair with forward > reverse.
        // The `spread` is added to the larger of the two so strict inequality
        // holds even when the two raw draws land on the same value.
        double reverseDb = Math.min(boundA, boundB);
        double forwardDb = Math.max(boundA, boundB) + spread;

        DtmfConfig cfg = DtmfConfig.advanced()
                .forwardTwistDb(forwardDb)
                .reverseTwistDb(reverseDb)
                .build();

        boolean expected = reverseDb <= twistDb && twistDb <= forwardDb;
        boolean actual = TwistEvaluator.withinTolerance(twistDb, cfg);

        assertEquals(expected, actual,
                "withinTolerance(" + twistDb
                        + ", cfg{forward=" + forwardDb + ", reverse=" + reverseDb + "})");
    }
}
