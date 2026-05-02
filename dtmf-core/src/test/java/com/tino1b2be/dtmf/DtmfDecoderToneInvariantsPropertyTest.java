package com.tino1b2be.dtmf;

// Feature: dtmf-v2-foundation, Property 2: Tone emission invariants

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for DTMF tone emission invariants.
 *
 * <p><strong>Property 2: Tone emission invariants.</strong>
 * <strong>Validates: Requirements 5.2, 5.3, 5.4, 5.5, 5.6, 13.2, 13.4.</strong>
 *
 * <p>For any random DTMF sequence rendered to {@code double[]} via
 * {@link DtmfGenerator} and decoded back via
 * {@link DtmfDecoder#decode(double[], DtmfConfig)}, every emitted
 * {@link DtmfTone} satisfies the eight invariants:
 *
 * <ol>
 *   <li>{@code key} is one of {@code 0-9}, {@code A-D}, {@code *}, {@code #};</li>
 *   <li>{@code startSample >= 0};</li>
 *   <li>{@code endSample > startSample};</li>
 *   <li>{@code endSample <= totalSamples};</li>
 *   <li>{@code confidence} is in {@code [0.0, 1.0]};</li>
 *   <li>{@code sampleRate} matches the config;</li>
 *   <li>{@code channel >= 0};</li>
 *   <li>the emission list is non-decreasing by {@code startSample}.</li>
 * </ol>
 *
 * <p>Under {@link ChannelMode#MONO MONO} and
 * {@link ChannelMode#STEREO_DOWNMIX STEREO_DOWNMIX}, the additional invariant
 * {@code channel == 0} is asserted.
 */
class DtmfDecoderToneInvariantsPropertyTest {

    private static final String KEY_ALPHABET = "0123456789ABCD*#";

    @Property(tries = 50)
    void monoEmissionsSatisfyAllInvariants(
            @ForAll("dtmfSequences") String sequence,
            @ForAll("supportedRates") int sampleRate) {

        DtmfConfig cfg = DtmfConfig.advanced()
                .sampleRate(sampleRate)
                .minimumToneDuration(Duration.ofMillis(60))
                .minimumGapDuration(Duration.ofMillis(40))
                .channelMode(ChannelMode.MONO)
                .build();

        double[] audio = DtmfGenerator.generate(sequence, cfg);
        List<DtmfTone> tones = DtmfDecoder.decode(audio, cfg);

        assertInvariants(tones, audio.length, sampleRate, /* requireChannelZero = */ true);
    }

    @Property(tries = 30)
    void stereoDownmixEmissionsTagChannelZero(
            @ForAll("dtmfSequences") String sequence) {

        int sampleRate = 8000;
        DtmfConfig cfg = DtmfConfig.advanced()
                .sampleRate(sampleRate)
                .minimumToneDuration(Duration.ofMillis(60))
                .minimumGapDuration(Duration.ofMillis(40))
                .channelMode(ChannelMode.STEREO_DOWNMIX)
                .build();

        double[] mono = DtmfGenerator.generate(sequence, cfg);
        // Interleave the mono signal into both channels so the downmix
        // (average) reproduces the mono signal bit-for-bit.
        double[] interleaved = new double[mono.length * 2];
        for (int i = 0; i < mono.length; i++) {
            interleaved[2 * i] = mono[i];
            interleaved[2 * i + 1] = mono[i];
        }

        List<DtmfTone> tones = DtmfDecoder.decode(interleaved, cfg);
        assertInvariants(tones, mono.length, sampleRate, /* requireChannelZero = */ true);
    }

    private static void assertInvariants(
            List<DtmfTone> tones, long totalSamples, int sampleRate,
            boolean requireChannelZero) {

        long previousStart = Long.MIN_VALUE;
        for (DtmfTone t : tones) {
            assertTrue(KEY_ALPHABET.indexOf(t.key()) >= 0,
                    "key '" + t.key() + "' must be a valid DTMF key");
            assertTrue(t.startSample() >= 0,
                    "startSample must be >= 0, was " + t.startSample());
            assertTrue(t.endSample() > t.startSample(),
                    "endSample (" + t.endSample() + ") must be > startSample ("
                            + t.startSample() + ")");
            assertTrue(t.endSample() <= totalSamples,
                    "endSample (" + t.endSample()
                            + ") must be <= totalSamples (" + totalSamples + ")");
            assertTrue(t.confidence() >= 0.0 && t.confidence() <= 1.0,
                    "confidence " + t.confidence() + " must be in [0, 1]");
            assertEquals(sampleRate, t.sampleRate(),
                    "sampleRate must match config");
            assertTrue(t.channel() >= 0,
                    "channel must be >= 0, was " + t.channel());
            if (requireChannelZero) {
                assertEquals(0, t.channel(),
                        "mono/downmix emissions must tag channel=0");
            }
            assertTrue(t.startSample() >= previousStart,
                    "emissions must be non-decreasing by startSample");
            previousStart = t.startSample();
        }
    }

    @Provide
    Arbitrary<String> dtmfSequences() {
        return Arbitraries.of('0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                        'A', 'B', 'C', 'D', '*', '#')
                .list().ofMinSize(1).ofMaxSize(6)
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
