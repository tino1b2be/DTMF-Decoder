package com.tino1b2be.dtmf.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FrequencyBins}.
 *
 * <p>The primary check is a cell-by-cell comparison of {@link FrequencyBins#KEY_MATRIX}
 * against the ITU-T Q.23 table reproduced in {@code design.md} and in the
 * Javadoc on {@code FrequencyBins}. A transposed or index-swapped matrix
 * would silently misdecode every key; anchoring it with exhaustive cell
 * equality catches that class of mistake at its source.
 *
 * <p>Also verifies the two frequency arrays use the Q.23 nominal values and
 * that {@link FrequencyBins#ALL_EIGHT} is the concatenation
 * {@code LOW_GROUP ∥ HIGH_GROUP} so a {@code GoertzelBank} built from
 * {@code ALL_EIGHT} has low-group peaks at indices {@code 0..3} and
 * high-group peaks at indices {@code 4..7}.
 */
class FrequencyBinsTest {

    @Test
    void lowGroupMatchesItuQ23() {
        assertArrayEquals(
                new double[] {697.0, 770.0, 852.0, 941.0},
                FrequencyBins.LOW_GROUP);
    }

    @Test
    void highGroupMatchesItuQ23() {
        assertArrayEquals(
                new double[] {1209.0, 1336.0, 1477.0, 1633.0},
                FrequencyBins.HIGH_GROUP);
    }

    @Test
    void allEightIsLowGroupConcatHighGroup() {
        assertArrayEquals(
                new double[] {697.0, 770.0, 852.0, 941.0,
                              1209.0, 1336.0, 1477.0, 1633.0},
                FrequencyBins.ALL_EIGHT);
    }

    @Test
    void keyMatrixRow697() {
        // 697 Hz row: 1, 2, 3, A against 1209, 1336, 1477, 1633.
        assertEquals('1', FrequencyBins.KEY_MATRIX[0][0]);
        assertEquals('2', FrequencyBins.KEY_MATRIX[0][1]);
        assertEquals('3', FrequencyBins.KEY_MATRIX[0][2]);
        assertEquals('A', FrequencyBins.KEY_MATRIX[0][3]);
    }

    @Test
    void keyMatrixRow770() {
        // 770 Hz row: 4, 5, 6, B.
        assertEquals('4', FrequencyBins.KEY_MATRIX[1][0]);
        assertEquals('5', FrequencyBins.KEY_MATRIX[1][1]);
        assertEquals('6', FrequencyBins.KEY_MATRIX[1][2]);
        assertEquals('B', FrequencyBins.KEY_MATRIX[1][3]);
    }

    @Test
    void keyMatrixRow852() {
        // 852 Hz row: 7, 8, 9, C.
        assertEquals('7', FrequencyBins.KEY_MATRIX[2][0]);
        assertEquals('8', FrequencyBins.KEY_MATRIX[2][1]);
        assertEquals('9', FrequencyBins.KEY_MATRIX[2][2]);
        assertEquals('C', FrequencyBins.KEY_MATRIX[2][3]);
    }

    @Test
    void keyMatrixRow941() {
        // 941 Hz row: *, 0, #, D.
        assertEquals('*', FrequencyBins.KEY_MATRIX[3][0]);
        assertEquals('0', FrequencyBins.KEY_MATRIX[3][1]);
        assertEquals('#', FrequencyBins.KEY_MATRIX[3][2]);
        assertEquals('D', FrequencyBins.KEY_MATRIX[3][3]);
    }

    @Test
    void keyMatrixIsFourByFour() {
        assertEquals(4, FrequencyBins.KEY_MATRIX.length);
        for (int i = 0; i < 4; i++) {
            assertEquals(4, FrequencyBins.KEY_MATRIX[i].length,
                    "row " + i + " should have 4 columns");
        }
    }

    @Test
    void keyForDelegatesToMatrix() {
        // Spot-check every cell via keyFor(low, high). If the matrix is
        // correctly indexed and keyFor doesn't swap its arguments, this
        // rebuilds the Q.23 table one call at a time.
        char[] expectedByRowCol = {
                '1', '2', '3', 'A',
                '4', '5', '6', 'B',
                '7', '8', '9', 'C',
                '*', '0', '#', 'D'
        };
        int i = 0;
        for (int low = 0; low < 4; low++) {
            for (int high = 0; high < 4; high++) {
                assertEquals(expectedByRowCol[i++],
                        FrequencyBins.keyFor(low, high),
                        "keyFor(" + low + ", " + high + ")");
            }
        }
    }
}
