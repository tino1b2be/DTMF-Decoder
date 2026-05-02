package com.tino1b2be.dtmf;

// Feature: dtmf-v2-foundation, Property 14: Stereo independent channels produce per-channel emissions

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
 * Property-based test for stereo independent channel decoding.
 *
 * <p><strong>Property 14: Stereo independent channels produce per-channel
 * emissions.</strong> <strong>Validates: Requirement 13.3.</strong>
 *
 * <p>For random per-channel DTMF sequences {@code sL} and {@code sR} (each
 * with {@code |s| in [1, 6]}), each rendered to mono audio by
 * {@link DtmfGenerator#generate(String, DtmfConfig)} and then interleaved
 * sample-by-sample into a single stereo buffer (even indices carry left,
 * odd indices carry right), decoding under
 * {@link ChannelMode#STEREO_INDEPENDENT} must emit tones whose
 * {@code channel = 0} subset spells {@code sL} and whose {@code channel = 1}
 * subset spells {@code sR}.
 *
 * <p>The shorter of the two per-channel buffers is zero-padded so the
 * interleaved buffer spans the full length of the longer one &mdash;
 * padding does not introduce spurious tones because zeros are the cleanest
 * form of silence the pipeline can see.
 */
class StereoIndependentChannelsPropertyTest {

    private static final DtmfConfig MONO_CFG = DtmfConfig.advanced()
            .sampleRate(8000)
            .minimumToneDuration(Duration.ofMillis(60))
            .minimumGapDuration(Duration.ofMillis(40))
            .build();

    private static final DtmfConfig STEREO_CFG = DtmfConfig.advanced()
            .sampleRate(8000)
            .minimumToneDuration(Duration.ofMillis(60))
            .minimumGapDuration(Duration.ofMillis(40))
            .channelMode(ChannelMode.STEREO_INDEPENDENT)
            .build();

    @Property(tries = 30)
    void perChannelSequencesDecodeIndependently(
            @ForAll("dtmfSequences") String left,
            @ForAll("dtmfSequences") String right) {

        double[] leftAudio = DtmfGenerator.generate(left, MONO_CFG);
        double[] rightAudio = DtmfGenerator.generate(right, MONO_CFG);
        double[] interleaved = interleave(leftAudio, rightAudio);

        List<DtmfTone> tones = DtmfDecoder.decode(interleaved, STEREO_CFG);

        String leftKeys = tones.stream()
                .filter(t -> t.channel() == 0)
                .map(t -> String.valueOf(t.key()))
                .collect(Collectors.joining());
        String rightKeys = tones.stream()
                .filter(t -> t.channel() == 1)
                .map(t -> String.valueOf(t.key()))
                .collect(Collectors.joining());

        assertEquals(left, leftKeys,
                "left channel emissions must spell \"" + left + "\"");
        assertEquals(right, rightKeys,
                "right channel emissions must spell \"" + right + "\"");
    }

    /**
     * Interleave two mono signals into a single stereo PCM buffer. The
     * shorter signal is zero-padded so both channels span the full
     * interleaved length.
     */
    private static double[] interleave(double[] left, double[] right) {
        int frames = Math.max(left.length, right.length);
        double[] out = new double[frames * 2];
        for (int i = 0; i < frames; i++) {
            out[2 * i] = (i < left.length) ? left[i] : 0.0;
            out[2 * i + 1] = (i < right.length) ? right[i] : 0.0;
        }
        return out;
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
}
