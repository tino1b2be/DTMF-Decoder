package com.tino1b2be.dtmf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link WindowFunction}.
 *
 * <p>Covers the three shapes defined in Requirement 8.2:
 * {@link WindowFunction#RECTANGULAR RECTANGULAR} is the identity;
 * {@link WindowFunction#HAMMING HAMMING} and
 * {@link WindowFunction#HANN HANN} match textbook endpoint values. The
 * specific endpoint identities — {@code HANN[0] == 0} and
 * {@code HANN[N-1] == 0}, and {@code HAMMING[0] == HAMMING[N-1] == 0.08}
 * (i.e. {@code 0.54 - 0.46}) — catch the two easy mistakes: using
 * {@code 2π·n/N} instead of {@code 2π·n/(N-1)}, and using a window of length
 * {@code N+1} or {@code N-1} by off-by-one.
 */
class WindowFunctionTest {

    private static final double EPSILON = 1e-12;

    @Test
    void rectangularLeavesBufferUnchanged() {
        double[] samples = {1.0, -2.0, 3.5, 0.25, -0.75};
        double[] expected = samples.clone();
        WindowFunction.RECTANGULAR.applyInPlace(samples, 0, samples.length);
        assertArrayEquals(expected, samples, EPSILON);
    }

    @Test
    void rectangularOverRangeLeavesOtherSamplesUnchanged() {
        // The window is applied to the middle three samples only; samples
        // outside the range must not be touched (even though RECTANGULAR
        // happens to be identity everywhere).
        double[] samples = {9.0, 1.0, 2.0, 3.0, 9.0};
        double[] expected = samples.clone();
        WindowFunction.RECTANGULAR.applyInPlace(samples, 1, 3);
        assertArrayEquals(expected, samples, EPSILON);
    }

    @Test
    void hammingEndpointsMatchTextbookFormula() {
        // HAMMING[0] = 0.54 - 0.46·cos(0) = 0.08
        // HAMMING[N-1] = 0.54 - 0.46·cos(2π) = 0.08
        int n = 8;
        double[] samples = new double[n];
        java.util.Arrays.fill(samples, 1.0);
        WindowFunction.HAMMING.applyInPlace(samples, 0, n);
        assertEquals(0.08, samples[0], EPSILON, "HAMMING[0]");
        assertEquals(0.08, samples[n - 1], EPSILON, "HAMMING[N-1]");
    }

    @Test
    void hammingMidpointMatchesTextbookFormula() {
        // HAMMING[(N-1)/2] ≈ 0.54 - 0.46·cos(π) = 0.54 + 0.46 = 1.00 for odd N
        // For N=9, midpoint is index 4: cos(2π·4/8) = cos(π) = -1.
        int n = 9;
        double[] samples = new double[n];
        java.util.Arrays.fill(samples, 1.0);
        WindowFunction.HAMMING.applyInPlace(samples, 0, n);
        assertEquals(1.00, samples[4], EPSILON, "HAMMING mid");
    }

    @Test
    void hannEndpointsAreExactlyZero() {
        // HANN[0]   = 0.5·(1 - cos(0))   = 0.0
        // HANN[N-1] = 0.5·(1 - cos(2π))  = 0.0
        int n = 8;
        double[] samples = new double[n];
        java.util.Arrays.fill(samples, 1.0);
        WindowFunction.HANN.applyInPlace(samples, 0, n);
        assertEquals(0.0, samples[0], EPSILON, "HANN[0]");
        assertEquals(0.0, samples[n - 1], EPSILON, "HANN[N-1]");
    }

    @Test
    void hannMidpointIsOne() {
        // HANN[(N-1)/2] = 0.5·(1 - cos(π)) = 1.0 for odd N.
        int n = 9;
        double[] samples = new double[n];
        java.util.Arrays.fill(samples, 1.0);
        WindowFunction.HANN.applyInPlace(samples, 0, n);
        assertEquals(1.0, samples[4], EPSILON, "HANN mid");
    }

    @Test
    void hammingScalesInputByWindow() {
        // Feed a non-unit signal so we observe the element-wise multiply.
        int n = 5;
        double[] samples = {2.0, 2.0, 2.0, 2.0, 2.0};
        WindowFunction.HAMMING.applyInPlace(samples, 0, n);
        // window[i] values (for N=5): 0.08, 0.54, 1.00, 0.54, 0.08
        double[] expected = {0.16, 1.08, 2.00, 1.08, 0.16};
        assertArrayEquals(expected, samples, EPSILON);
    }

    @Test
    void hannScalesInputByWindow() {
        int n = 5;
        double[] samples = {2.0, 2.0, 2.0, 2.0, 2.0};
        WindowFunction.HANN.applyInPlace(samples, 0, n);
        // window[i] values (for N=5): 0.0, 0.5, 1.0, 0.5, 0.0
        double[] expected = {0.0, 1.0, 2.0, 1.0, 0.0};
        assertArrayEquals(expected, samples, EPSILON);
    }

    @Test
    void hannOnOffsetRangeTouchesOnlyThatRange() {
        int total = 10;
        double[] samples = new double[total];
        java.util.Arrays.fill(samples, 1.0);
        // Window samples [3..7) i.e. length 4.
        WindowFunction.HANN.applyInPlace(samples, 3, 4);
        // Outside the range: unchanged (= 1.0).
        assertEquals(1.0, samples[0], EPSILON);
        assertEquals(1.0, samples[2], EPSILON);
        assertEquals(1.0, samples[7], EPSILON);
        assertEquals(1.0, samples[9], EPSILON);
        // Inside the range: endpoints zero.
        assertEquals(0.0, samples[3], EPSILON, "HANN[0] in range");
        assertEquals(0.0, samples[6], EPSILON, "HANN[N-1] in range");
    }

    @Test
    void singleSampleWindowIsPassThrough() {
        // The N=1 case has a zero-width denominator in the textbook formula;
        // all three shapes treat it as pass-through.
        double[] samplesR = {3.14};
        WindowFunction.RECTANGULAR.applyInPlace(samplesR, 0, 1);
        assertEquals(3.14, samplesR[0], EPSILON);

        double[] samplesHm = {3.14};
        WindowFunction.HAMMING.applyInPlace(samplesHm, 0, 1);
        assertEquals(3.14, samplesHm[0], EPSILON);

        double[] samplesHn = {3.14};
        WindowFunction.HANN.applyInPlace(samplesHn, 0, 1);
        assertEquals(3.14, samplesHn[0], EPSILON);
    }

    @Test
    void applyInPlaceRejectsNullSamples() {
        assertThrows(NullPointerException.class,
                () -> WindowFunction.RECTANGULAR.applyInPlace(null, 0, 0));
        assertThrows(NullPointerException.class,
                () -> WindowFunction.HAMMING.applyInPlace(null, 0, 0));
        assertThrows(NullPointerException.class,
                () -> WindowFunction.HANN.applyInPlace(null, 0, 0));
    }

    @Test
    void applyInPlaceRejectsOutOfBoundsRange() {
        double[] samples = new double[4];
        assertThrows(IndexOutOfBoundsException.class,
                () -> WindowFunction.HAMMING.applyInPlace(samples, 0, 5));
        assertThrows(IndexOutOfBoundsException.class,
                () -> WindowFunction.HANN.applyInPlace(samples, 3, 2));
        assertThrows(IndexOutOfBoundsException.class,
                () -> WindowFunction.RECTANGULAR.applyInPlace(samples, -1, 2));
    }
}
