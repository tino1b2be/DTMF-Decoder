package com.tino1b2be.dtmf;

// Feature: dtmf-v2-foundation, Property 8: Pull API matches push API

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.Size;

/**
 * Property-based test for pull-vs-push equivalence.
 *
 * <p><strong>Property 8: Pull API matches push API.</strong>
 * <strong>Validates: Requirement 7.5.</strong>
 *
 * <p>For any random {@code double[]} samples and any valid
 * {@link DtmfConfig}, iterating
 * {@link DtmfStream#fromSamples(double[], DtmfConfig)} to exhaustion
 * produces the same {@link DtmfTone} sequence as a fresh
 * {@link DtmfDetector} fed the samples followed by
 * {@link DtmfDetector#flush()}. Equality is by record value.
 */
class DtmfStreamEquivalencePropertyTest {

    @Property(tries = 100)
    void pullAndPushProduceEquivalentEmissions(
            @ForAll("pcmSamples") @Size(max = 16_000) double[] samples) {

        DtmfConfig cfg = DtmfConfig.advanced()
                .sampleRate(8000)
                .minimumToneDuration(Duration.ofMillis(60))
                .minimumGapDuration(Duration.ofMillis(40))
                .build();

        // Pull side.
        List<DtmfTone> pull = new ArrayList<>();
        try (DtmfStream stream = DtmfStream.fromSamples(samples, cfg)) {
            while (stream.hasNext()) {
                pull.add(stream.next());
            }
        }

        // Push side.
        List<DtmfTone> push = new ArrayList<>();
        DtmfDetector detector = new DtmfDetector(cfg);
        detector.onTone(push::add);
        detector.process(samples);
        detector.flush();

        assertEquals(push, pull,
                "DtmfStream emissions must equal fresh DtmfDetector emissions");
    }

    @Provide
    Arbitrary<double[]> pcmSamples() {
        Arbitrary<Double> samples = Arbitraries.doubles()
                .between(-1.0, 1.0).ofScale(9);
        return samples.array(double[].class);
    }
}
