package com.tino1b2be.dtmf;

// Feature: dtmf-v2-foundation, Property 11: Generator segment durations match config

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

/**
 * Property-based test for generator segment durations.
 *
 * <p><strong>Property 11: Generator segment durations match config.</strong>
 * <strong>Validates: Requirements 11.4, 11.5.</strong>
 *
 * <p>For a random valid DTMF sequence {@code s} (possibly empty, up to 10
 * characters) and a random valid {@link DtmfConfig} (tone duration
 * &ge;&nbsp;40&nbsp;ms, gap duration &ge;&nbsp;0&nbsp;ms, sample rate drawn
 * from Supported_Sample_Rate), the generator output satisfies:
 *
 * <ul>
 *   <li>{@code |output| = |s| * N + max(0, |s| - 1) * M} where
 *       {@code N = round(tone * Fs)} and {@code M = round(gap * Fs)};</li>
 *   <li>the {@code |s| - 1} gap segments between consecutive tones are
 *       exactly zero-valued and each has length {@code M};</li>
 *   <li>empty sequences produce a zero-length array;</li>
 *   <li>single-character sequences produce no trailing gap.</li>
 * </ul>
 */
class DtmfGeneratorDurationsPropertyTest {

    @Property(tries = 100)
    void outputLengthAndGapsAreExact(
            @ForAll("dtmfSequences") String sequence,
            @ForAll("supportedRates") int sampleRate,
            @ForAll @LongRange(min = 40L, max = 200L) long toneMillis,
            @ForAll @IntRange(min = 0, max = 100) int gapMillis) {

        DtmfConfig cfg = DtmfConfig.advanced()
                .sampleRate(sampleRate)
                .minimumToneDuration(Duration.ofMillis(toneMillis))
                .minimumGapDuration(Duration.ofMillis(gapMillis))
                .build();

        int n = (int) Math.round(toneMillis / 1000.0 * sampleRate);
        int m = (int) Math.round(gapMillis / 1000.0 * sampleRate);

        double[] out = DtmfGenerator.generate(sequence, cfg);
        int len = sequence.length();
        int expectedLength = len == 0 ? 0 : len * n + (len - 1) * m;
        assertEquals(expectedLength, out.length,
                "unexpected length for sequence \"" + sequence + "\" at "
                        + sampleRate + " Hz, tone=" + toneMillis + "ms, gap="
                        + gapMillis + "ms");

        // Verify every gap region is zero-valued. A gap starts after the
        // i-th tone (i < len - 1), at index i*(N+M) + N, and spans M samples.
        for (int i = 0; i < len - 1; i++) {
            int gapStart = i * (n + m) + n;
            for (int k = 0; k < m; k++) {
                assertEquals(0.0, out[gapStart + k], 0.0,
                        "gap " + i + " sample " + k
                                + " must be zero; sequence=\"" + sequence + "\"");
            }
        }
    }

    @Provide
    Arbitrary<String> dtmfSequences() {
        return Arbitraries.of('0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                        'A', 'B', 'C', 'D', '*', '#')
                .list().ofMinSize(0).ofMaxSize(10)
                .map(chars -> {
                    StringBuilder sb = new StringBuilder(chars.size());
                    for (Character c : chars) {
                        sb.append(c);
                    }
                    return sb.toString();
                });
    }

    @Provide
    Arbitrary<Integer> supportedRates() {
        return Arbitraries.of(8000, 16000, 44100, 48000);
    }
}
