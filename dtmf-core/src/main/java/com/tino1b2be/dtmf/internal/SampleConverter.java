package com.tino1b2be.dtmf.internal;

import java.util.Objects;

/**
 * Sample-format normalization for the DTMF detection pipeline.
 *
 * <p>Every public entry point of {@link com.tino1b2be.dtmf.DtmfDecoder} and
 * {@link com.tino1b2be.dtmf.DtmfDetector} that accepts non-{@code double}
 * PCM — {@code short[]} PCM16, {@code float[]} normalized float, {@code int[]}
 * PCM32, or {@code int[]} packed PCM24 — funnels through this class before
 * the shared analysis pipeline sees a sample. Concentrating the conversion
 * formulas in one place is how we guarantee Requirements 4.5, 4.6, and 4.7
 * hold identically on both the batch path ({@code DtmfDecoder}) and the push
 * path ({@code DtmfDetector}).
 *
 * <p>Two flavours are provided for every input type:
 *
 * <ul>
 *   <li>An <em>allocating</em> variant — {@link #fromShort(short[])},
 *       {@link #fromFloat(float[])}, {@link #fromInt(int[])},
 *       {@link #fromPcm24(int[])} — which returns a freshly allocated
 *       {@code double[]}. Used by the batch {@code DtmfDecoder.decode}
 *       overloads, which run exactly once per call and for which the
 *       allocation is proportional to input size — standard cost of format
 *       conversion.</li>
 *   <li>A <em>streaming</em> {@code *Into} variant — {@link #fromShortInto},
 *       {@link #fromFloatInto}, {@link #fromIntInto} — which writes into a
 *       caller-supplied destination array. Used by the push-style
 *       {@code DtmfDetector.process(short[])} / {@code process(float[])} /
 *       {@code process(int[])} overloads, which own a reusable
 *       {@code double[] scratch} buffer so no allocation happens per chunk
 *       on the hot path.</li>
 * </ul>
 *
 * <p><strong>Conversion formulas</strong> (Requirements 4.5, 4.6, 4.7):
 *
 * <ul>
 *   <li>{@code short} &rarr; {@code double}: divide by {@code 32768.0} (that
 *       is {@code 2^15}). Chosen so {@code Short.MIN_VALUE} maps exactly to
 *       {@code -1.0} and {@code Short.MAX_VALUE} maps to
 *       {@code 32767/32768 ≈ 0.99996948}. The alternative divisor
 *       {@code 32767.0} would shift the zero-crossing and break the symmetry
 *       of the negative and positive extremes.</li>
 *   <li>{@code float} &rarr; {@code double}: direct widening cast, no
 *       scaling. Callers supply values already normalized to
 *       {@code [-1.0, 1.0]}. Special values ({@code NaN},
 *       {@code ±Infinity}) are preserved bit-exactly by Java's widening
 *       conversion.</li>
 *   <li>{@code int} &rarr; {@code double}: divide by {@code 2147483648.0}
 *       (that is {@code 2^31}). Chosen so {@code Integer.MIN_VALUE} maps
 *       exactly to {@code -1.0}; {@code Integer.MAX_VALUE} maps to
 *       {@code 2147483647/2147483648}.</li>
 *   <li>PCM24 packed in {@code int}: the low 24 bits of each input carry a
 *       signed 24-bit value. Sign-extend from bit 23 (shift left 8, then
 *       arithmetic-shift right 8) so values in {@code [0x800000, 0xFFFFFF]}
 *       become negative, then divide by {@code 8388608.0}
 *       (that is {@code 2^23}). After sign extension {@code 0x800000} is
 *       {@code -8388608} and maps exactly to {@code -1.0}; {@code 0x7FFFFF}
 *       is {@code 8388607} and maps to {@code 8388607/8388608}.</li>
 * </ul>
 *
 * <p><strong>Null and size handling:</strong> Every allocating variant rejects
 * {@code null} input with {@link NullPointerException} via
 * {@link Objects#requireNonNull(Object, String)} so the parameter name appears
 * in the message (Requirement 17.1). The {@code *Into} variants additionally
 * reject a destination shorter than the source with
 * {@link IllegalArgumentException} naming both lengths (Requirement 17.2).
 *
 * <p>Although the type is {@code public} so
 * {@code com.tino1b2be.dtmf.DtmfDecoder} in the sibling package can call it,
 * the convention is that {@code com.tino1b2be.dtmf.internal.*} is not part of
 * the published API. Callers go through {@link com.tino1b2be.dtmf.DtmfDecoder}
 * or {@link com.tino1b2be.dtmf.DtmfDetector} instead.
 */
public final class SampleConverter {

    /** Divisor for PCM16 &rarr; normalized double ({@code 2^15}). */
    private static final double PCM16_DIVISOR = 32768.0;

    /** Divisor for PCM32 &rarr; normalized double ({@code 2^31}). */
    private static final double PCM32_DIVISOR = 2147483648.0;

    /** Divisor for PCM24 &rarr; normalized double ({@code 2^23}). */
    private static final double PCM24_DIVISOR = 8388608.0;

    private SampleConverter() { }

    // ---------- Allocating variants (batch path) ----------

    /**
     * Convert signed PCM16 samples to normalized {@code double} in a new
     * array.
     *
     * @param src PCM16 input; must be non-null
     * @return a new {@code double[]} of the same length with
     *         {@code dst[i] = src[i] / 32768.0}
     * @throws NullPointerException if {@code src} is null
     */
    public static double[] fromShort(short[] src) {
        Objects.requireNonNull(src, "src");
        double[] dst = new double[src.length];
        fromShortInto(src, dst);
        return dst;
    }

    /**
     * Convert normalized {@code float} samples to {@code double} in a new
     * array via direct widening.
     *
     * @param src float input; must be non-null
     * @return a new {@code double[]} of the same length with
     *         {@code dst[i] = (double) src[i]} (exact widening, no scaling)
     * @throws NullPointerException if {@code src} is null
     */
    public static double[] fromFloat(float[] src) {
        Objects.requireNonNull(src, "src");
        double[] dst = new double[src.length];
        fromFloatInto(src, dst);
        return dst;
    }

    /**
     * Convert signed PCM32 samples to normalized {@code double} in a new
     * array.
     *
     * @param src PCM32 input; must be non-null
     * @return a new {@code double[]} of the same length with
     *         {@code dst[i] = src[i] / 2147483648.0}
     * @throws NullPointerException if {@code src} is null
     */
    public static double[] fromInt(int[] src) {
        Objects.requireNonNull(src, "src");
        double[] dst = new double[src.length];
        fromIntInto(src, dst);
        return dst;
    }

    /**
     * Convert signed PCM24 samples packed into the low 24 bits of each
     * {@code int} to normalized {@code double} in a new array.
     *
     * <p>Sign-extends each input from bit 23 before scaling, so the full
     * two's-complement 24-bit range {@code [-8388608, 8388607]} is handled
     * and values whose bit-23 is set round-trip to negative output.
     *
     * @param src PCM24-packed input; must be non-null
     * @return a new {@code double[]} of the same length with
     *         {@code dst[i] = signExtend24(src[i]) / 8388608.0}
     * @throws NullPointerException if {@code src} is null
     */
    public static double[] fromPcm24(int[] src) {
        Objects.requireNonNull(src, "src");
        double[] dst = new double[src.length];
        for (int i = 0; i < src.length; i++) {
            // Sign-extend the low 24 bits: shift the sign bit (bit 23) up
            // to bit 31, then arithmetic-shift right so the sign fills the
            // high bits. `>>` is arithmetic on int in Java.
            int v = (src[i] << 8) >> 8;
            dst[i] = v / PCM24_DIVISOR;
        }
        return dst;
    }

    // ---------- Streaming variants (push path) ----------

    /**
     * Convert signed PCM16 samples into a caller-supplied destination
     * starting at index 0.
     *
     * @param src PCM16 input; must be non-null
     * @param dst destination buffer; must be non-null and have
     *            {@code dst.length >= src.length}
     * @throws NullPointerException     if either argument is null
     * @throws IllegalArgumentException if {@code dst.length < src.length}
     */
    public static void fromShortInto(short[] src, double[] dst) {
        Objects.requireNonNull(src, "src");
        Objects.requireNonNull(dst, "dst");
        checkCapacity(src.length, dst.length);
        for (int i = 0; i < src.length; i++) {
            dst[i] = src[i] / PCM16_DIVISOR;
        }
    }

    /**
     * Copy normalized {@code float} samples into a caller-supplied
     * {@code double[]} destination via direct widening, starting at index 0.
     *
     * @param src float input; must be non-null
     * @param dst destination buffer; must be non-null and have
     *            {@code dst.length >= src.length}
     * @throws NullPointerException     if either argument is null
     * @throws IllegalArgumentException if {@code dst.length < src.length}
     */
    public static void fromFloatInto(float[] src, double[] dst) {
        Objects.requireNonNull(src, "src");
        Objects.requireNonNull(dst, "dst");
        checkCapacity(src.length, dst.length);
        for (int i = 0; i < src.length; i++) {
            dst[i] = src[i];
        }
    }

    /**
     * Convert signed PCM32 samples into a caller-supplied destination
     * starting at index 0.
     *
     * @param src PCM32 input; must be non-null
     * @param dst destination buffer; must be non-null and have
     *            {@code dst.length >= src.length}
     * @throws NullPointerException     if either argument is null
     * @throws IllegalArgumentException if {@code dst.length < src.length}
     */
    public static void fromIntInto(int[] src, double[] dst) {
        Objects.requireNonNull(src, "src");
        Objects.requireNonNull(dst, "dst");
        checkCapacity(src.length, dst.length);
        for (int i = 0; i < src.length; i++) {
            dst[i] = src[i] / PCM32_DIVISOR;
        }
    }

    private static void checkCapacity(int srcLength, int dstLength) {
        if (dstLength < srcLength) {
            throw new IllegalArgumentException(
                    "dst.length (" + dstLength + ") < src.length (" + srcLength + ")");
        }
    }
}
