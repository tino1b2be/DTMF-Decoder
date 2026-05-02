package com.tino1b2be.dtmf;

// Feature: dtmf-v2-foundation, Property 16: DtmfTone time helpers are consistent with sample indices

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

/**
 * Property-based tests for {@link DtmfTone}'s time helpers.
 *
 * <p><strong>Property 16: DtmfTone time helpers are consistent with sample indices.</strong>
 * <strong>Validates: Requirements 14.1, 14.2, 14.3.</strong>
 *
 * <p>For any valid {@code DtmfTone t}, we assert:
 *
 * <ul>
 *   <li>{@code t.startTime().toNanos() == round(t.startSample() / t.sampleRate() * 1e9)}
 *       (Requirement 14.1)</li>
 *   <li>{@code t.endTime().toNanos() == round(t.endSample() / t.sampleRate() * 1e9)}
 *       (Requirement 14.2)</li>
 *   <li>{@code t.duration().equals(t.endTime().minus(t.startTime()))}
 *       (Requirement 14.3)</li>
 * </ul>
 *
 * <p>The generator constrains sample indices to a range that cannot overflow
 * {@code long} nanoseconds when multiplied by {@code 1e9}: with
 * {@code startSample, endSample &le; 10^12} and {@code sampleRate &ge; 1}, the
 * computed nanosecond value is at most {@code 10^21}, well within
 * {@code long} range. Confidence and channel values are kept trivially valid
 * because Property 16 is about time arithmetic, not field validation.
 */
class DtmfTonePropertyTest {

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    @Property(tries = 100)
    void timeHelpersAreConsistentWithSampleIndices(
            @ForAll @LongRange(min = 0L, max = 1_000_000_000_000L) long startSample,
            @ForAll @LongRange(min = 1L, max = 1_000_000_000_000L) long gapSamples,
            @ForAll @IntRange(min = 1, max = 192_000) int sampleRate,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double confidence,
            @ForAll @IntRange(min = 0, max = 1) int channel) {

        // Keep endSample within the generator's upper bound so we never
        // violate the record's field invariants or overflow long nanoseconds.
        long maxEnd = 1_000_000_000_000L;
        long endSample;
        if (startSample >= maxEnd) {
            // startSample is at the ceiling; swap so endSample can exceed it.
            endSample = startSample;
            startSample = Math.max(0L, endSample - gapSamples);
            if (startSample == endSample) {
                startSample = endSample - 1;
            }
        } else {
            long proposedEnd = startSample + gapSamples;
            endSample = Math.min(maxEnd, proposedEnd);
            if (endSample <= startSample) {
                endSample = startSample + 1;
            }
        }

        DtmfTone tone = new DtmfTone('5', startSample, endSample, sampleRate, confidence, channel);

        long expectedStartNanos = Math.round(startSample / (double) sampleRate * NANOS_PER_SECOND);
        long expectedEndNanos = Math.round(endSample / (double) sampleRate * NANOS_PER_SECOND);

        assertEquals(expectedStartNanos, tone.startTime().toNanos(),
                "startTime().toNanos() must equal round(startSample / sampleRate * 1e9)");
        assertEquals(expectedEndNanos, tone.endTime().toNanos(),
                "endTime().toNanos() must equal round(endSample / sampleRate * 1e9)");

        Duration expectedDuration = tone.endTime().minus(tone.startTime());
        assertEquals(expectedDuration, tone.duration(),
                "duration() must equal endTime().minus(startTime())");
    }
}
