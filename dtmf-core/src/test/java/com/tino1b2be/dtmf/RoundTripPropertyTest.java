package com.tino1b2be.dtmf;

// Feature: dtmf-v2-foundation, Property 3: Generator → decoder round-trip

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for generator &rarr; decoder round-trip.
 *
 * <p><strong>Property 3: Generator &rarr; decoder round-trip.</strong>
 * <strong>Validates: Requirement 11.6.</strong>
 *
 * <p>For any DTMF key sequence {@code s} with {@code |s| in [1, 32]} and
 * every Supported_Sample_Rate {@code Fs in {8000, 16000, 44100, 48000}},
 * decoding the audio produced by {@link DtmfGenerator#generate(String,
 * DtmfConfig)} with {@link DtmfDecoder#decode(double[], DtmfConfig)}
 * recovers the sequence of keys (joined in emission order) equal to
 * {@code s.toUpperCase()}.
 *
 * <p>The config uses generous margins &mdash; a 60&nbsp;ms minimum tone
 * duration and a 40&nbsp;ms minimum gap duration &mdash; so timing jitter
 * around the analysis-block boundary never collapses consecutive tones or
 * misses a short one.
 */
class RoundTripPropertyTest {

    /**
     * 30 tries per rate is enough to cover the 16-key alphabet generously
     * without blowing up test duration (each case synthesizes and decodes
     * up to ~3&nbsp;seconds of audio at 48&nbsp;kHz).
     */
    @Property(tries = 30)
    void generatorDecoderRoundTrip(
            @ForAll("dtmfSequences") String sequence,
            @ForAll("supportedRates") int sampleRate) {

        DtmfConfig cfg = DtmfConfig.advanced()
                .sampleRate(sampleRate)
                .minimumToneDuration(Duration.ofMillis(60))
                .minimumGapDuration(Duration.ofMillis(40))
                .build();

        double[] audio = DtmfGenerator.generate(sequence, cfg);
        List<DtmfTone> decoded = DtmfDecoder.decode(audio, cfg);

        String actual = decoded.stream()
                .map(t -> String.valueOf(t.key()))
                .collect(Collectors.joining());

        assertEquals(sequence.toUpperCase(), actual,
                "round-trip failed at " + sampleRate + " Hz for sequence \""
                        + sequence + "\"; decoded=\"" + actual + "\"");
    }

    @Provide
    Arbitrary<String> dtmfSequences() {
        return Arbitraries.of('0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                        'A', 'B', 'C', 'D', '*', '#')
                .list().ofMinSize(1).ofMaxSize(32)
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
