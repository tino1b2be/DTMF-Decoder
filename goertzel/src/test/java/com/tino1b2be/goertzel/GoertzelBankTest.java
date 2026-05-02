package com.tino1b2be.goertzel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GoertzelBank}.
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>An 8-filter bank at the eight DTMF frequencies driven by a pure
 *       {@code 697 + 1336 Hz} sum produces peaks at indices 0 (697 Hz) and 5
 *       (1336 Hz), with every other bin at least 20 dB lower than the
 *       smaller of the two peaks.</li>
 *   <li>{@link GoertzelBank#computeMagnitudesSquaredInto(double[], double[])}
 *       leaves the bank reset: a second call on an all-zero buffer returns
 *       all zeros.</li>
 *   <li>{@link GoertzelBank#magnitudesSquaredInto(double[])} throws
 *       {@link IllegalArgumentException} when {@code out.length != size()}.</li>
 * </ul>
 *
 * <p>These tests validate Requirement 10.4.
 */
class GoertzelBankTest {

    /**
     * The eight DTMF frequencies in the order used by the DTMF v2 detector:
     * the four low-group tones first (indices 0..3), then the four high-group
     * tones (indices 4..7). Index 0 = 697 Hz, index 5 = 1336 Hz as referenced
     * by the task.
     */
    private static final double[] DTMF_FREQUENCIES = {
            697.0, 770.0, 852.0, 941.0,     // low group
            1209.0, 1336.0, 1477.0, 1633.0  // high group
    };

    private static final int SAMPLE_RATE = 8000;

    /**
     * 200 ms analysis block at 8 kHz = 1600 samples. Longer than the spec's
     * canonical 20 ms block so that spectral leakage from 697 Hz into the
     * neighbouring 770 Hz bin stays below −20 dB even without a window
     * function. A 20 ms block has 50 Hz bin width, which is wider than the
     * 73 Hz spacing between 697 Hz and 770 Hz divided by the leakage falloff
     * of a rectangular window, so the two bins would not separate by 20 dB.
     */
    private static final int BLOCK_SIZE = 1600;

    @Test
    void dtmfBankSeparatesSeven697Plus1336ComponentsFromOtherBins() {
        // Build 160 samples of a pure 0.5*(sin(2π·697·t) + sin(2π·1336·t))
        // signal. The 0.5 scaling keeps the peak sample well within [-1, 1]
        // but does not matter for the relative-dB comparison.
        double[] signal = new double[BLOCK_SIZE];
        for (int n = 0; n < BLOCK_SIZE; n++) {
            double t = (double) n / SAMPLE_RATE;
            signal[n] = 0.5 * Math.sin(2.0 * Math.PI * 697.0 * t)
                     + 0.5 * Math.sin(2.0 * Math.PI * 1336.0 * t);
        }

        GoertzelBank bank = new GoertzelBank(SAMPLE_RATE, DTMF_FREQUENCIES);
        double[] magnitudesSquared = new double[DTMF_FREQUENCIES.length];
        bank.computeMagnitudesSquaredInto(signal, magnitudesSquared);

        double peak697 = magnitudesSquared[0];
        double peak1336 = magnitudesSquared[5];
        double smallerPeak = Math.min(peak697, peak1336);

        // Sanity: the two target bins are the largest.
        for (int i = 0; i < magnitudesSquared.length; i++) {
            if (i == 0 || i == 5) continue;
            assertTrue(
                    magnitudesSquared[i] < smallerPeak,
                    "Non-target bin " + i + " (" + DTMF_FREQUENCIES[i]
                            + " Hz) should be below the smaller peak; got "
                            + magnitudesSquared[i] + " vs smaller peak " + smallerPeak);
        }

        // At least 20 dB separation: smallerPeak / otherBin >= 100
        // (since 10·log10(100) = 20 dB on a magnitude² ratio).
        double minRatio = 100.0;
        for (int i = 0; i < magnitudesSquared.length; i++) {
            if (i == 0 || i == 5) continue;
            double otherBin = magnitudesSquared[i];
            // Guard against a zero bin, which trivially satisfies >= 20 dB.
            if (otherBin == 0.0) continue;
            double ratio = smallerPeak / otherBin;
            assertTrue(
                    ratio >= minRatio,
                    "Peak-to-bin ratio at non-target bin " + i + " ("
                            + DTMF_FREQUENCIES[i] + " Hz) should be >= 20 dB (ratio >= 100), got "
                            + ratio + " (smaller peak " + smallerPeak + " vs bin " + otherBin + ")");
        }
    }

    @Test
    void computeMagnitudesSquaredIntoLeavesBankResetSoSilenceAfterwardsReturnsZeros() {
        GoertzelBank bank = new GoertzelBank(SAMPLE_RATE, DTMF_FREQUENCIES);

        // First call with a real signal. This should reset-feed-read-reset;
        // the bank must end up clean.
        double[] signal = new double[BLOCK_SIZE];
        for (int n = 0; n < BLOCK_SIZE; n++) {
            double t = (double) n / SAMPLE_RATE;
            signal[n] = Math.sin(2.0 * Math.PI * 697.0 * t);
        }
        double[] firstMags = new double[DTMF_FREQUENCIES.length];
        bank.computeMagnitudesSquaredInto(signal, firstMags);

        // Sanity: we actually got energy from the first call.
        assertTrue(firstMags[0] > 0.0,
                "Pre-condition: bank should report energy at 697 Hz for a 697 Hz input");

        // Second call on silence: every output bin must be exactly zero,
        // which is only possible if the bank's internal state was zeroed.
        double[] silence = new double[BLOCK_SIZE]; // all zeros
        double[] secondMags = new double[DTMF_FREQUENCIES.length];
        bank.computeMagnitudesSquaredInto(silence, secondMags);

        for (int i = 0; i < secondMags.length; i++) {
            assertEquals(
                    0.0,
                    secondMags[i],
                    0.0,
                    "After computeMagnitudesSquaredInto on real signal then silence, "
                            + "bin " + i + " (" + DTMF_FREQUENCIES[i] + " Hz) must be exactly zero");
        }
    }

    @Test
    void magnitudesSquaredIntoThrowsWhenOutLengthMismatchesBankSize() {
        GoertzelBank bank = new GoertzelBank(SAMPLE_RATE, DTMF_FREQUENCIES);

        double[] tooShort = new double[DTMF_FREQUENCIES.length - 1];
        IllegalArgumentException shortEx = assertThrows(
                IllegalArgumentException.class,
                () -> bank.magnitudesSquaredInto(tooShort),
                "Expected IllegalArgumentException when out.length < size()");
        assertTrue(
                shortEx.getMessage() != null
                        && shortEx.getMessage().contains(String.valueOf(DTMF_FREQUENCIES.length)),
                "Exception message should mention the expected size " + DTMF_FREQUENCIES.length
                        + ", got: " + shortEx.getMessage());

        double[] tooLong = new double[DTMF_FREQUENCIES.length + 3];
        assertThrows(
                IllegalArgumentException.class,
                () -> bank.magnitudesSquaredInto(tooLong),
                "Expected IllegalArgumentException when out.length > size()");
    }
}
