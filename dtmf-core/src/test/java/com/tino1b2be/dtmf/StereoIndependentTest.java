package com.tino1b2be.dtmf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ChannelMode#STEREO_INDEPENDENT} decoding.
 *
 * <p>Covers Task 11.1 from {@code tasks.md}. Validates Requirement 13.3:
 * when channel mode is {@code STEREO_INDEPENDENT}, the decoder treats the
 * input as interleaved left/right PCM, runs each channel through its own
 * analysis pipeline, and tags emissions with {@code channel = 0} (left,
 * even sample indices) or {@code channel = 1} (right, odd sample indices).
 *
 * <p>The test interleaves audio generated for different sequences on the
 * two channels and asserts the per-channel emission sequences match the
 * per-channel input sequences.
 */
class StereoIndependentTest {

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

    @Test
    void leftAndRightChannelsDecodeIndependently() {
        double[] left = DtmfGenerator.generate("123", MONO_CFG);
        double[] right = DtmfGenerator.generate("ABC", MONO_CFG);

        double[] interleaved = interleave(left, right);

        List<DtmfTone> tones = DtmfDecoder.decode(interleaved, STEREO_CFG);

        String leftKeys = tones.stream()
                .filter(t -> t.channel() == 0)
                .map(t -> String.valueOf(t.key()))
                .collect(Collectors.joining());
        String rightKeys = tones.stream()
                .filter(t -> t.channel() == 1)
                .map(t -> String.valueOf(t.key()))
                .collect(Collectors.joining());

        assertEquals("123", leftKeys,
                "left-channel (channel=0) tones must spell the left sequence");
        assertEquals("ABC", rightKeys,
                "right-channel (channel=1) tones must spell the right sequence");
    }

    /**
     * Interleave two mono signals into a single stereo PCM buffer. The
     * shorter signal is zero-padded so both channels span the full
     * interleaved length. Even indices carry {@code left}, odd indices carry
     * {@code right}.
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
}
