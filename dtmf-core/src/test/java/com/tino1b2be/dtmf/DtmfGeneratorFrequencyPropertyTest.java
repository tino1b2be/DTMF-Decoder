package com.tino1b2be.dtmf;

// Feature: dtmf-v2-foundation, Property 10: Generator produces the correct frequency pair per key

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import com.tino1b2be.dtmf.internal.FrequencyBins;
import com.tino1b2be.goertzel.GoertzelBank;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for generator frequency correctness.
 *
 * <p><strong>Property 10: Generator produces the correct frequency pair per
 * key.</strong> <strong>Validates: Requirement 11.2.</strong>
 *
 * <p>For every DTMF key in {@code {0-9, A-D, *, #}} and every
 * Supported_Sample_Rate in {@code {8000, 16000, 44100, 48000}}, at least
 * 40&nbsp;ms of that key is generated, passed through a
 * {@link GoertzelBank} evaluating the eight DTMF frequencies, and the two
 * highest-energy bins must be exactly the {@code (lowHz, highHz)} pair
 * canonically assigned to that key by ITU-T Q.23.
 *
 * <p>The property is enumerated (16 keys &times; 4 rates) rather than purely
 * random so every key/rate combination is exercised on every run; jqwik
 * samples uniformly from the small enum and the default 100 tries comfortably
 * covers the 64-element product space.
 */
class DtmfGeneratorFrequencyPropertyTest {

    @Property(tries = 200)
    void twoHighestBinsAreTheCanonicalPair(
            @ForAll("dtmfKeys") Character keyBox,
            @ForAll("supportedRates") int sampleRate) {

        char key = keyBox;
        DtmfConfig cfg = DtmfConfig.advanced()
                .sampleRate(sampleRate)
                .minimumToneDuration(Duration.ofMillis(60))
                .minimumGapDuration(Duration.ofMillis(20))
                .build();

        double[] audio = DtmfGenerator.generate(String.valueOf(key), cfg);
        GoertzelBank bank = new GoertzelBank(sampleRate, FrequencyBins.ALL_EIGHT);
        double[] mags = new double[8];
        bank.computeMagnitudesSquaredInto(audio, mags);

        double[] expected = FrequencyBins.frequenciesFor(key);

        // Find the two highest-energy bins.
        int topIndex = argMax(mags, -1);
        int secondIndex = argMax(mags, topIndex);

        double topFrequency = FrequencyBins.ALL_EIGHT[topIndex];
        double secondFrequency = FrequencyBins.ALL_EIGHT[secondIndex];

        boolean match =
                (approxEquals(topFrequency, expected[0])
                        && approxEquals(secondFrequency, expected[1]))
                        || (approxEquals(topFrequency, expected[1])
                        && approxEquals(secondFrequency, expected[0]));

        assertTrue(match,
                "for key '" + key + "' at " + sampleRate
                        + " Hz expected top-2 bins to be {"
                        + expected[0] + ", " + expected[1] + "}, was {"
                        + topFrequency + ", " + secondFrequency + "}");
    }

    private static int argMax(double[] mags, int skipIndex) {
        int best = -1;
        double bestVal = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < mags.length; i++) {
            if (i == skipIndex) {
                continue;
            }
            if (mags[i] > bestVal) {
                bestVal = mags[i];
                best = i;
            }
        }
        return best;
    }

    private static boolean approxEquals(double a, double b) {
        return Math.abs(a - b) < 1e-6;
    }

    @Provide
    Arbitrary<Character> dtmfKeys() {
        return Arbitraries.of('0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                'A', 'B', 'C', 'D', '*', '#');
    }

    @Provide
    Arbitrary<Integer> supportedRates() {
        return Arbitraries.of(8000, 16000, 44100, 48000);
    }
}
