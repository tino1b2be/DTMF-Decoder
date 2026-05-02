package com.tino1b2be.dtmf;

// Feature: dtmf-v2-foundation, Property 20: Standard factories reject unsupported rates

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based test for the standard-factory sample-rate validator
 * exposed on {@link DtmfConfig} as
 * {@link DtmfConfig#validateStandardFactorySampleRate(int)}.
 *
 * <p><strong>Property 20: Standard factories reject unsupported rates.</strong>
 * <strong>Validates: Requirement 3.3.</strong>
 *
 * <p>For any integer {@code r} not in {@code {8000, 16000, 44100, 48000}},
 * the validator throws {@link IllegalArgumentException} whose message
 * enumerates the supported set. Using the validator directly (rather than
 * routing through a specific factory like {@code forTelephony()}) is
 * intentional: the validator is the one chokepoint the standard factories
 * share, so exercising it with random inputs covers every factory at once
 * and isolates the test from factory-specific knob defaults.
 *
 * <p>Generator domain chosen well beyond the advanced range
 * {@code [4000, 192000]} so the property also exercises negatives, zero,
 * and values above the advanced ceiling. {@link Assume#that(boolean)}
 * discards the four supported values that would not belong in the rejection
 * sample.
 */
class DtmfConfigStandardFactorySampleRatePropertyTest {

    private static final Set<Integer> SUPPORTED_RATES =
            Set.of(8000, 16000, 44100, 48000);

    @Property(tries = 200)
    void standardFactoryValidatorRejectsEveryUnsupportedRate(
            @ForAll @IntRange(min = -10_000, max = 250_000) int candidate) {

        Assume.that(!SUPPORTED_RATES.contains(candidate));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DtmfConfig.validateStandardFactorySampleRate(candidate));

        String message = ex.getMessage();
        assertTrue(message != null, "exception must carry a message");
        // Message must enumerate every element of the supported set so
        // callers can see exactly what's allowed.
        for (int rate : SUPPORTED_RATES) {
            assertTrue(message.contains(Integer.toString(rate)),
                    "message must mention supported rate " + rate
                            + ", was: " + message);
        }
        // And must identify the offending value so callers can see what they
        // tried to set.
        assertTrue(message.contains(Integer.toString(candidate)),
                "message must mention offending rate " + candidate
                        + ", was: " + message);
    }
}
