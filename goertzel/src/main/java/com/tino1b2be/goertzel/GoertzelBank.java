package com.tino1b2be.goertzel;

import java.util.Objects;

/**
 * A bank of {@link GoertzelFilter} instances that all share a single sample
 * rate and are each tuned to one entry in a fixed array of target frequencies.
 *
 * <p>The bank exposes two complementary usage modes:
 * <ul>
 *   <li><strong>Streaming.</strong> {@link #accept(double)} and
 *       {@link #acceptAll(double[])} feed samples through every filter; callers
 *       then read magnitudes via {@link #magnitudesSquaredInto(double[])} or
 *       {@link #magnitudesSquared()}. {@link #reset()} zeros every filter so
 *       the bank can be reused across analysis blocks without allocation.</li>
 *   <li><strong>Batch.</strong>
 *       {@link #computeMagnitudesSquaredInto(double[], double[])} performs one
 *       reset–feed–read–reset cycle, leaving the bank clean for the next
 *       batch.</li>
 * </ul>
 *
 * <p>The bank is <strong>mutable</strong> (the underlying filters accumulate
 * state) and therefore not thread-safe. Each analysing thread should own its
 * own {@code GoertzelBank}.
 *
 * <p>The constructor makes a defensive copy of the supplied frequency array,
 * so the caller may mutate or discard the original afterwards without
 * affecting the bank.
 *
 * @since 2.0.0
 */
public final class GoertzelBank {

    private final int sampleRate;
    private final double[] targetFrequencies;
    private final GoertzelFilter[] filters;

    /**
     * Create a bank of Goertzel filters, one per entry of
     * {@code targetFrequencies}, all evaluated at the given {@code sampleRate}.
     *
     * <p>{@code targetFrequencies} is defensively copied; subsequent mutations
     * of the caller's array are not observed by this bank.
     *
     * @param sampleRate        sample rate of signals the bank will analyse, in Hz; must be positive
     * @param targetFrequencies frequencies to evaluate, in Hz; must be non-null,
     *                          non-empty, and each entry must lie in
     *                          {@code [0, sampleRate / 2)}
     * @throws NullPointerException     if {@code targetFrequencies} is {@code null}
     * @throws IllegalArgumentException if {@code sampleRate <= 0},
     *                                  {@code targetFrequencies} is empty, or any
     *                                  frequency is outside {@code [0, sampleRate / 2)}
     */
    public GoertzelBank(int sampleRate, double[] targetFrequencies) {
        Objects.requireNonNull(targetFrequencies, "targetFrequencies");
        if (sampleRate <= 0) {
            throw new IllegalArgumentException(
                    "sampleRate must be > 0, was " + sampleRate);
        }
        if (targetFrequencies.length == 0) {
            throw new IllegalArgumentException(
                    "targetFrequencies must be non-empty");
        }
        double nyquist = sampleRate / 2.0;
        double[] copy = targetFrequencies.clone();
        GoertzelFilter[] built = new GoertzelFilter[copy.length];
        for (int i = 0; i < copy.length; i++) {
            double f = copy[i];
            if (!(f >= 0.0) || f >= nyquist) {
                throw new IllegalArgumentException(
                        "targetFrequencies[" + i + "] must be in [0, sampleRate / 2), was "
                                + f + " for sampleRate " + sampleRate);
            }
            built[i] = new GoertzelFilter(sampleRate, f);
        }
        this.sampleRate = sampleRate;
        this.targetFrequencies = copy;
        this.filters = built;
    }

    /**
     * {@return the sample rate this bank was constructed with, in Hz}
     */
    public int sampleRate() {
        return sampleRate;
    }

    /**
     * {@return the number of filters in this bank, equal to the length of the
     *         frequency array supplied at construction}
     */
    public int size() {
        return filters.length;
    }

    /**
     * Return the target frequency, in Hz, of the filter at the given index.
     *
     * @param index position in the frequency array supplied at construction
     * @return the target frequency at {@code index}
     * @throws ArrayIndexOutOfBoundsException if {@code index} is outside
     *                                        {@code [0, size())}
     */
    public double targetFrequency(int index) {
        return targetFrequencies[index];
    }

    /* ---------- Streaming API ---------- */

    /**
     * Feed one sample to every filter in the bank. {@code O(size())}, no
     * allocation.
     *
     * @param sample the next sample in the signal
     */
    public void accept(double sample) {
        for (GoertzelFilter f : filters) {
            f.accept(sample);
        }
    }

    /**
     * Feed a range of samples to every filter. Equivalent to calling
     * {@link #accept(double)} in order for each element in
     * {@code samples[offset .. offset + length)}.
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
            double s = samples[i];
            for (GoertzelFilter f : filters) {
                f.accept(s);
            }
        }
    }

    /**
     * Feed every sample in {@code samples} to every filter. Equivalent to
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
     * Write magnitude-squared values from each filter, in construction order,
     * into {@code out}. The bank is <strong>not</strong> reset; callers that
     * want batch semantics should use
     * {@link #computeMagnitudesSquaredInto(double[], double[])} or call
     * {@link #reset()} explicitly.
     *
     * @param out destination array; must be non-null and have length equal to
     *            {@link #size()}
     * @throws NullPointerException     if {@code out} is {@code null}
     * @throws IllegalArgumentException if {@code out.length != size()}
     */
    public void magnitudesSquaredInto(double[] out) {
        Objects.requireNonNull(out, "out");
        if (out.length != filters.length) {
            throw new IllegalArgumentException(
                    "out.length must equal size(), expected " + filters.length
                            + " but was " + out.length);
        }
        for (int i = 0; i < filters.length; i++) {
            out[i] = filters[i].magnitudeSquared();
        }
    }

    /**
     * Allocate a fresh array and fill it with the current magnitude-squared
     * values for every filter. Prefer {@link #magnitudesSquaredInto(double[])}
     * on hot paths to avoid allocation.
     *
     * @return a newly allocated {@code double[]} of length {@link #size()}
     *         containing each filter's magnitude squared
     */
    public double[] magnitudesSquared() {
        double[] out = new double[filters.length];
        magnitudesSquaredInto(out);
        return out;
    }

    /**
     * Zero the internal accumulators of every filter so the bank is ready to
     * analyse a new block of samples from a clean state.
     */
    public void reset() {
        for (GoertzelFilter f : filters) {
            f.reset();
        }
    }

    /* ---------- Batch API ---------- */

    /**
     * One-shot batch evaluation: reset the bank, feed every sample in
     * {@code samples} to every filter, write magnitude-squared values into
     * {@code out}, and reset the bank again so it is ready for the next batch.
     *
     * @param samples input samples; must be non-null
     * @param out     destination array; must be non-null and have length equal
     *                to {@link #size()}
     * @throws NullPointerException     if {@code samples} or {@code out} is {@code null}
     * @throws IllegalArgumentException if {@code out.length != size()}
     */
    public void computeMagnitudesSquaredInto(double[] samples, double[] out) {
        Objects.requireNonNull(samples, "samples");
        Objects.requireNonNull(out, "out");
        if (out.length != filters.length) {
            throw new IllegalArgumentException(
                    "out.length must equal size(), expected " + filters.length
                            + " but was " + out.length);
        }
        reset();
        acceptAll(samples, 0, samples.length);
        magnitudesSquaredInto(out);
        reset();
    }
}
