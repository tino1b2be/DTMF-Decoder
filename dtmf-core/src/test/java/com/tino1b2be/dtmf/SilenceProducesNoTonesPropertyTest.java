package com.tino1b2be.dtmf;

// Feature: dtmf-v2-foundation, Property 4: Silence produces no tones

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

/**
 * Property-based test: pure silence produces no detected tones.
 *
 * <p><strong>Property 4: Silence produces no tones.</strong>
 * <strong>Validates: Requirement 12.2.</strong>
 *
 * <p>For any input length {@code L} up to 5&nbsp;seconds of pure zeros at
 * every Supported_Sample_Rate {@code Fs in {8000, 16000, 44100, 48000}},
 * and for any {@link DtmfConfig} whose {@link DtmfConfig#detectionThreshold()
 * detectionThreshold} is strictly greater than {@code 0.0},
 * {@link DtmfDecoder#decode(double[], DtmfConfig)} returns an empty list.
 *
 * <p>The longer 60-second check required by Requirement 12.2 lives in the
 * integration-test source set; this property focuses on shorter buffers
 * that jqwik can exercise hundreds of times without spending tens of
 * seconds per iteration.
 */
class SilenceProducesNoTonesPropertyTest {

    /** Max buffer size corresponds to 5 seconds at the highest rate (48 kHz). */
    private static final int MAX_SILENCE_MILLIS = 5_000;

    @Property(tries = 40)
    void silenceAlwaysDecodesToEmptyList(
            @ForAll("supportedRates") int sampleRate,
            @ForAll @LongRange(min = 0L, max = MAX_SILENCE_MILLIS) long lengthMillis,
            @ForAll("detectionThresholds") double detectionThreshold,
            @ForAll @LongRange(min = 10L, max = 200L) long minToneMillis,
            @ForAll @IntRange(min = 0, max = 100) int minGapMillis) {

        DtmfConfig cfg = DtmfConfig.advanced()
                .sampleRate(sampleRate)
                .minimumToneDuration(Duration.ofMillis(minToneMillis))
                .minimumGapDuration(Duration.ofMillis(minGapMillis))
                .detectionThreshold(detectionThreshold)
                .build();

        int length = (int) Math.round(lengthMillis / 1000.0 * sampleRate);
        double[] silence = new double[length];

        List<DtmfTone> tones = DtmfDecoder.decode(silence, cfg);

        assertTrue(tones.isEmpty(),
                "silence of " + length + " samples at " + sampleRate
                        + " Hz (threshold=" + detectionThreshold
                        + ") must produce no tones; got " + tones.size());
    }

    @Provide
    Arbitrary<Integer> supportedRates() {
        return Arbitraries.of(8000, 16000, 44100, 48000);
    }

    @Provide
    Arbitrary<Double> detectionThresholds() {
        // Scale 6 lets us cover very small positive thresholds through 1.0.
        return Arbitraries.doubles().between(1e-6, 1.0).ofScale(6);
    }
}
