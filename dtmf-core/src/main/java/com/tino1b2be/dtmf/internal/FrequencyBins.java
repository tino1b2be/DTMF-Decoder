package com.tino1b2be.dtmf.internal;

/**
 * ITU-T Q.23 DTMF frequency tables.
 *
 * <p>This internal helper holds the eight DTMF frequencies (four in the low
 * group, four in the high group) and the 4&times;4 key matrix mapping
 * {@code (lowIndex, highIndex)} to the symbol for that tone pair.
 *
 * <p>The detector uses {@link #ALL_EIGHT} to construct its
 * {@code GoertzelBank} and indexes peaks via {@link #keyFor(int, int)}. The
 * generator uses {@link #LOW_GROUP} and {@link #HIGH_GROUP} to look up the
 * {@code (lowHz, highHz)} pair for a given key symbol.
 *
 * <p>The numeric values are the Q.23 nominal frequencies and are deliberately
 * exposed as {@code double} so Goertzel coefficient computation is free of
 * implicit int-to-double widening.
 *
 * <p>Package-private by design: this is not part of the {@code dtmf-core}
 * public API.
 */
final class FrequencyBins {

    private FrequencyBins() { }

    /**
     * Low-group DTMF frequencies in Hz, indexed 0..3. Matches Q.23:
     * 697, 770, 852, 941 Hz.
     */
    static final double[] LOW_GROUP = {697.0, 770.0, 852.0, 941.0};

    /**
     * High-group DTMF frequencies in Hz, indexed 0..3. Matches Q.23:
     * 1209, 1336, 1477, 1633 Hz.
     */
    static final double[] HIGH_GROUP = {1209.0, 1336.0, 1477.0, 1633.0};

    /**
     * The eight DTMF frequencies in a single array, ordered as
     * {@code LOW_GROUP ∥ HIGH_GROUP}. Convenient for constructing a
     * {@code GoertzelBank} with one filter per DTMF frequency.
     */
    static final double[] ALL_EIGHT = {
            LOW_GROUP[0], LOW_GROUP[1], LOW_GROUP[2], LOW_GROUP[3],
            HIGH_GROUP[0], HIGH_GROUP[1], HIGH_GROUP[2], HIGH_GROUP[3]
    };

    /**
     * The 4&times;4 key matrix: {@code KEY_MATRIX[lowIndex][highIndex]} is the
     * DTMF symbol for the tone pair {@code (LOW_GROUP[lowIndex], HIGH_GROUP[highIndex])}.
     *
     * <pre>
     *          1209  1336  1477  1633
     *   697 Hz   1     2     3     A
     *   770 Hz   4     5     6     B
     *   852 Hz   7     8     9     C
     *   941 Hz   *     0     #     D
     * </pre>
     */
    static final char[][] KEY_MATRIX = {
            {'1', '2', '3', 'A'},   // 697 Hz
            {'4', '5', '6', 'B'},   // 770 Hz
            {'7', '8', '9', 'C'},   // 852 Hz
            {'*', '0', '#', 'D'}    // 941 Hz
    };

    /**
     * Look up the DTMF symbol for the given low-group / high-group peak
     * indices.
     *
     * @param lowIndex  index into {@link #LOW_GROUP}; must be in {@code [0, 4)}
     * @param highIndex index into {@link #HIGH_GROUP}; must be in {@code [0, 4)}
     * @return the DTMF symbol for {@code (LOW_GROUP[lowIndex], HIGH_GROUP[highIndex])}
     * @throws ArrayIndexOutOfBoundsException if either index is outside {@code [0, 4)}
     */
    static char keyFor(int lowIndex, int highIndex) {
        return KEY_MATRIX[lowIndex][highIndex];
    }
}
