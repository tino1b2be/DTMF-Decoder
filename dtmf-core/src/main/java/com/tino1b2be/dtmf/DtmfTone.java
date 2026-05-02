package com.tino1b2be.dtmf;

import java.time.Duration;

/**
 * Immutable value object describing one detected or generated DTMF tone.
 *
 * <p>The record is populated by {@code DtmfDecoder}, {@code DtmfDetector},
 * and {@code DtmfStream} for every confirmed tone, and by test fixtures and
 * the generator side when building expected vectors. All fields are public
 * via record accessors; see the Glossary in {@code requirements.md} for the
 * authoritative semantics.
 *
 * <p>Compact-constructor validation (Requirement 17.2) rejects illegal
 * inputs with {@link IllegalArgumentException}:
 *
 * <ul>
 *   <li>{@code startSample &ge; 0}</li>
 *   <li>{@code endSample &gt; startSample}</li>
 *   <li>{@code sampleRate &gt; 0}</li>
 *   <li>{@code confidence} in the closed range {@code [0.0, 1.0]}</li>
 *   <li>{@code channel &ge; 0}</li>
 * </ul>
 *
 * <p>Time helpers ({@link #startTime()}, {@link #endTime()},
 * {@link #duration()}) derive {@link Duration} values from the sample
 * indices and the sample rate per Requirement 14. The arithmetic uses
 * {@link Math#round(double)} on nanoseconds so a whole-second boundary
 * reports exactly {@code PT1S} rather than {@code PT0.999999999S}.
 *
 * @param key         one of {@code '0'..'9', 'A'..'D', '*', '#'}
 * @param startSample first sample index (inclusive) of the tone; must be {@code >= 0}
 * @param endSample   last sample index (exclusive) of the tone; must be {@code > startSample}
 * @param sampleRate  sample rate the indices are expressed in, in Hz; must be {@code > 0}
 * @param confidence  detection confidence in {@code [0.0, 1.0]}
 * @param channel     channel tag: {@code 0} for mono or left, {@code 1} for right
 *
 * @since 2.0.0
 */
public record DtmfTone(
        char key,
        long startSample,
        long endSample,
        int sampleRate,
        double confidence,
        int channel) {

    /** Nanoseconds per second, used by the time helpers. */
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    /**
     * Compact constructor validating every field. Messages include the
     * offending value so callers can diagnose misuse without reading source.
     */
    public DtmfTone {
        if (startSample < 0) {
            throw new IllegalArgumentException(
                    "startSample must be >= 0, was " + startSample);
        }
        if (endSample <= startSample) {
            throw new IllegalArgumentException(
                    "endSample must be > startSample, was endSample=" + endSample
                            + ", startSample=" + startSample);
        }
        if (sampleRate <= 0) {
            throw new IllegalArgumentException(
                    "sampleRate must be > 0, was " + sampleRate);
        }
        if (Double.isNaN(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException(
                    "confidence must be in [0, 1], was " + confidence);
        }
        if (channel < 0) {
            throw new IllegalArgumentException(
                    "channel must be >= 0, was " + channel);
        }
    }

    /**
     * {@return the start position of this tone as a {@link Duration}}
     * computed from {@link #startSample()} and {@link #sampleRate()}
     * (Requirement 14.1).
     */
    public Duration startTime() {
        return durationOfSamples(startSample, sampleRate);
    }

    /**
     * {@return the end position of this tone as a {@link Duration}}
     * computed from {@link #endSample()} and {@link #sampleRate()}
     * (Requirement 14.2).
     */
    public Duration endTime() {
        return durationOfSamples(endSample, sampleRate);
    }

    /**
     * {@return the duration of this tone, equal to
     *         {@code endTime().minus(startTime())}} (Requirement 14.3).
     */
    public Duration duration() {
        return endTime().minus(startTime());
    }

    /**
     * Convert a sample count at the given sample rate to a {@link Duration}.
     * Uses {@link Math#round(double)} on nanoseconds so exact-second
     * boundaries report exactly {@code PT<seconds>S}.
     */
    private static Duration durationOfSamples(long samples, int sampleRate) {
        long nanos = Math.round((samples / (double) sampleRate) * NANOS_PER_SECOND);
        return Duration.ofNanos(nanos);
    }
}
