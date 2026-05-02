package com.tino1b2be.goertzel;

import java.util.Objects;

/**
 * Single-frequency, reusable Goertzel-algorithm filter.
 *
 * <p>Each instance targets one frequency bin {@code targetFrequency} evaluated
 * at a fixed {@code sampleRate}. The IIR coefficient
 * {@code 2 * cos(2π * targetFrequency / sampleRate)} is precomputed once in
 * the constructor; after that, feeding samples is {@code O(1)} per sample and
 * does not allocate.
 *
 * <p>The filter is <strong>mutable</strong> and holds two {@code double}
 * accumulators ({@code q1}, {@code q2}) that capture the second-order state.
 * Callers may feed any number of samples and then read the current
 * {@link #magnitudeSquared() magnitude squared} at the target frequency. The
 * accumulators can be zeroed via {@link #reset()} so the same instance is
 * reusable across analysis blocks without allocation.
 *
 * <p><strong>Thread-safety.</strong> Instances are not thread-safe. One
 * filter per analysing thread.
 *
 * <p><strong>Why magnitude squared?</strong> DTMF detection only compares
 * ratios of squared magnitudes, so this API deliberately omits the per-block
 * square root. Callers that want magnitude take {@code Math.sqrt} themselves.
 *
 * @since 2.0.0
 */
public final class GoertzelFilter {

    private final int sampleRate;
    private final double targetFrequency;
    private final double coefficient;

    private double q1;
    private double q2;

    /**
     * Create a Goertzel filter tuned to {@code targetFrequency} at the given
     * {@code sampleRate}.
     *
     * <p>The coefficient is precomputed as
     * {@code 2 * cos(2π * targetFrequency / sampleRate)}. Accumulators start
     * at zero; call {@link #reset()} to return to this state after use.
     *
     * @param sampleRate      sample rate of the signal that will be analysed, in Hz; must be positive
     * @param targetFrequency frequency to evaluate, in Hz; must be in {@code [0, sampleRate / 2)}
     * @throws IllegalArgumentException if {@code sampleRate <= 0} or
     *                                  {@code targetFrequency < 0} or
     *                                  {@code targetFrequency >= sampleRate / 2.0}
     */
    public GoertzelFilter(int sampleRate, double targetFrequency) {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException(
                    "sampleRate must be > 0, was " + sampleRate);
        }
        if (targetFrequency < 0.0 || targetFrequency >= sampleRate / 2.0) {
            throw new IllegalArgumentException(
                    "targetFrequency must be in [0, sampleRate / 2), was "
                            + targetFrequency + " for sampleRate " + sampleRate);
        }
        this.sampleRate = sampleRate;
        this.targetFrequency = targetFrequency;
        this.coefficient = 2.0 * Math.cos(2.0 * Math.PI * targetFrequency / sampleRate);
        this.q1 = 0.0;
        this.q2 = 0.0;
    }

    /**
     * {@return the sample rate this filter was constructed with, in Hz}
     */
    public int sampleRate() {
        return sampleRate;
    }

    /**
     * {@return the target frequency this filter evaluates, in Hz}
     */
    public double targetFrequency() {
        return targetFrequency;
    }

    /**
     * {@return the precomputed Goertzel coefficient
     *         {@code 2 * cos(2π * targetFrequency / sampleRate)}}
     */
    public double coefficient() {
        return coefficient;
    }

    /**
     * Feed one sample to the filter. O(1), no allocation.
     *
     * @param sample the next sample in the signal
     */
    public void accept(double sample) {
        double q0 = coefficient * q1 - q2 + sample;
        q2 = q1;
        q1 = q0;
    }

    /**
     * Feed a range of samples. Equivalent to calling {@link #accept(double)}
     * in order for each element in {@code samples[offset .. offset + length)}.
     *
     * @param samples source array; must be non-null
     * @param offset  starting index into {@code samples}; must be non-negative
     * @param length  number of samples to consume; must be non-negative and
     *                {@code offset + length <= samples.length}
     * @throws NullPointerException      if {@code samples} is {@code null}
     * @throws IndexOutOfBoundsException if {@code offset} or {@code length}
     *                                   describes a range outside {@code samples}
     */
    public void acceptAll(double[] samples, int offset, int length) {
        Objects.requireNonNull(samples, "samples");
        Objects.checkFromIndexSize(offset, length, samples.length);
        int end = offset + length;
        for (int i = offset; i < end; i++) {
            accept(samples[i]);
        }
    }

    /**
     * Feed every sample in {@code samples} to the filter. Equivalent to
     * {@code acceptAll(samples, 0, samples.length)}.
     *
     * @param samples source array; must be non-null
     * @throws NullPointerException if {@code samples} is {@code null}
     */
    public void acceptAll(double[] samples) {
        Objects.requireNonNull(samples, "samples");
        acceptAll(samples, 0, samples.length);
    }

    /**
     * Magnitude squared at the target frequency, given the currently
     * accumulated state.
     *
     * <p>Computed as {@code q1² + q2² − q1 · q2 · coefficient}. This method
     * does <strong>not</strong> reset the filter; call {@link #reset()} before
     * starting a new analysis block.
     *
     * @return magnitude squared at {@link #targetFrequency()}
     */
    public double magnitudeSquared() {
        return q1 * q1 + q2 * q2 - q1 * q2 * coefficient;
    }

    /**
     * Zero the internal accumulators so this filter can analyse a new block
     * of samples from a clean state.
     */
    public void reset() {
        q1 = 0.0;
        q2 = 0.0;
    }
}
