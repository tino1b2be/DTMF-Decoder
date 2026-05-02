package com.tino1b2be.dtmf.internal;

// Feature: dtmf-v2-foundation, Property 13: Twist formula identity

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for {@link TwistEvaluator#twistDb(double, double)}.
 *
 * <p><strong>Property 13: Twist formula identity.</strong>
 * <strong>Validates: Requirement 9.1.</strong>
 *
 * <p>For any positive {@code lowEnergy} and {@code highEnergy},
 * {@link TwistEvaluator#twistDb(double, double)} equals
 * {@code 10 * log10(highEnergy / lowEnergy)} within a floating-point
 * tolerance of {@code 1e-12}. The implementation is literally this formula,
 * so the property is a compile-time/runtime guard against an accidental
 * divisor swap (e.g. computing {@code log10(low / high)} and negating) or a
 * units error (e.g. using {@code 20 * log10} as if the inputs were amplitudes
 * rather than energies).
 *
 * <p>Values are drawn from {@code [1e-9, 1e9]} via a custom arbitrary with
 * scale 9 so the boundaries are representable; jqwik's default
 * {@code DoubleArbitrary} uses scale 2 which cannot express {@code 1e-9}.
 * Zero-low-energy behaviour is covered separately by a unit test.
 */
class TwistFormulaPropertyTest {

    @Property(tries = 200)
    void twistDbMatchesDirectLogFormula(
            @ForAll("positiveEnergies") double lowEnergy,
            @ForAll("positiveEnergies") double highEnergy) {

        double expected = 10.0 * Math.log10(highEnergy / lowEnergy);
        double actual = TwistEvaluator.twistDb(lowEnergy, highEnergy);

        assertEquals(expected, actual, 1e-12,
                "twistDb(" + lowEnergy + ", " + highEnergy + ")");
    }

    @Provide
    Arbitrary<Double> positiveEnergies() {
        // Scale 9 so the 1e-9 lower bound is representable. Without this
        // jqwik's default scale-2 arbitrary rejects the range with a
        // JqwikException at generation time.
        return Arbitraries.doubles().between(1e-9, 1e9).ofScale(9);
    }
}
