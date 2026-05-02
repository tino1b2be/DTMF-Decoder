package com.tino1b2be.dtmf.internal;

// Feature: dtmf-v2-foundation, Property 19: Analysis-block bin width is in [40, 60] Hz across the advanced domain

import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based test for {@link BlockSizer}.
 *
 * <p><strong>Property 19: Analysis-block bin width is in [40, 60] Hz across
 * the advanced domain.</strong> <strong>Validates: Requirements 3.4, 3.5.</strong>
 *
 * <p>For any integer sample rate {@code Fs} in the advanced domain
 * {@code [4000, 192000]} Hz, {@link BlockSizer#blockSizeFor(int)} returns a
 * positive integer {@code N} such that the effective bin width
 * {@code (double) Fs / N} lies in the closed interval {@code [40.0, 60.0]}.
 *
 * <p>Run with {@code @Property(tries = 200)} so that across the full 188k-wide
 * integer domain jqwik explores enough values — including the boundaries
 * {@code 4000} and {@code 192000} — to surface any off-by-one defect in the
 * clamp loop.
 */
class BlockSizerPropertyTest {

    @Property(tries = 200)
    void blockSizeProducesBinWidthInBand(
            @ForAll @IntRange(min = 4000, max = 192_000) int sampleRate) {

        int n = BlockSizer.blockSizeFor(sampleRate);

        assertTrue(n >= 1,
                "blockSizeFor(" + sampleRate + ") returned " + n + ", expected >= 1");

        double binWidth = (double) sampleRate / n;
        assertTrue(binWidth >= 40.0 && binWidth <= 60.0,
                "blockSizeFor(" + sampleRate + ") = " + n
                        + " produced bin width " + binWidth
                        + " Hz, expected [40.0, 60.0]");
    }
}
