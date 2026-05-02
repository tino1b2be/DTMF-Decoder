package com.tino1b2be.dtmf;

import java.util.Objects;

/**
 * Window function applied to each analysis block before the Goertzel bank
 * computes magnitudes.
 *
 * <p>DTMF detection at a bin width of 40&ndash;60&nbsp;Hz already separates
 * the eight DTMF frequencies by at least 100&nbsp;Hz, so windowing is a
 * precision knob rather than a necessity. The production defaults
 * ({@code defaults}, {@code forTelephony}, {@code forVoip},
 * {@code forNoisyAudio}) all use {@link #RECTANGULAR}; the advanced builder
 * can override to {@link #HAMMING} or {@link #HANN} when sidelobe leakage
 * from a nearby non-DTMF tone is a concern.
 *
 * <p>Formulas (for a window of length {@code N}, {@code n = 0..N-1}):
 *
 * <ul>
 *   <li>{@link #RECTANGULAR} &mdash; identity; leaves samples unchanged.</li>
 *   <li>{@link #HAMMING} &mdash; {@code 0.54 - 0.46 · cos(2π · n / (N - 1))}.</li>
 *   <li>{@link #HANN} &mdash; {@code 0.5 · (1 - cos(2π · n / (N - 1)))}. At the
 *       endpoints the coefficient is exactly {@code 0}.</li>
 * </ul>
 *
 * <p>The single-sample window ({@code N == 1}) is treated as a pass-through
 * for every shape: the textbook formulas have a zero-width denominator in
 * that case, and the only sensible coefficient is {@code 1.0}.
 *
 * @since 2.0.0
 */
public enum WindowFunction {

    /** No window; samples pass through unchanged. */
    RECTANGULAR {
        @Override
        public void applyInPlace(double[] samples, int offset, int length) {
            Objects.requireNonNull(samples, "samples");
            Objects.checkFromIndexSize(offset, length, samples.length);
            // Identity: nothing to do.
        }
    },

    /** Hamming window: {@code 0.54 - 0.46 · cos(2π · n / (N - 1))}. */
    HAMMING {
        @Override
        public void applyInPlace(double[] samples, int offset, int length) {
            Objects.requireNonNull(samples, "samples");
            Objects.checkFromIndexSize(offset, length, samples.length);
            if (length <= 1) {
                return; // Single-sample window is pass-through.
            }
            double denom = length - 1.0;
            for (int n = 0; n < length; n++) {
                double w = 0.54 - 0.46 * Math.cos(2.0 * Math.PI * n / denom);
                samples[offset + n] *= w;
            }
        }
    },

    /** Hann window: {@code 0.5 · (1 - cos(2π · n / (N - 1)))}. */
    HANN {
        @Override
        public void applyInPlace(double[] samples, int offset, int length) {
            Objects.requireNonNull(samples, "samples");
            Objects.checkFromIndexSize(offset, length, samples.length);
            if (length <= 1) {
                return; // Single-sample window is pass-through.
            }
            double denom = length - 1.0;
            for (int n = 0; n < length; n++) {
                double w = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * n / denom));
                samples[offset + n] *= w;
            }
        }
    };

    /**
     * Multiply each sample in {@code samples[offset .. offset + length)} by
     * the corresponding window coefficient, in place.
     *
     * @param samples destination buffer; must be non-null
     * @param offset  index of the first sample to window; must be non-negative
     * @param length  number of samples to window; the window length
     * @throws NullPointerException      if {@code samples} is {@code null}
     * @throws IndexOutOfBoundsException if {@code offset} or {@code length}
     *                                   describes a range outside {@code samples}
     */
    public abstract void applyInPlace(double[] samples, int offset, int length);
}
