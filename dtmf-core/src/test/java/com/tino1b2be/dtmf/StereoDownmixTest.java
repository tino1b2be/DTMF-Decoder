package com.tino1b2be.dtmf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ChannelMode#STEREO_DOWNMIX} decoding.
 *
 * <p>Covers Task 11.2 from {@code tasks.md}. Validates Requirement 13.4:
 * when channel mode is {@code STEREO_DOWNMIX}, the decoder averages adjacent
 * left/right pairs into a single mono stream before detection, and tags
 * every emitted tone with {@code channel = 0}.
 *
 * <p>Two scenarios are exercised:
 *
 * <ol>
 *   <li>Identical left and right channels &mdash; the downmix reproduces the
 *       mono signal bit-for-bit, so detection should behave exactly like
 *       mono;</li>
 *   <li>Signal on left, silence on right &mdash; the downmix halves the
 *       amplitude (from 0.5 peak to 0.25 peak). The detector's confidence
 *       formula is amplitude-ratio based, not amplitude-absolute, so the
 *       tone is still detected under the default telephony config.</li>
 * </ol>
 */
class StereoDownmixTest {

    private static final DtmfConfig MONO_CFG = DtmfConfig.advanced()
            .sampleRate(8000)
            .minimumToneDuration(Duration.ofMillis(60))
            .minimumGapDuration(Duration.ofMillis(40))
            .build();

    private static final DtmfConfig DOWNMIX_CFG = DtmfConfig.advanced()
            .sampleRate(8000)
            .minimumToneDuration(Duration.ofMillis(60))
            .minimumGapDuration(Duration.ofMillis(40))
            .channelMode(ChannelMode.STEREO_DOWNMIX)
            .build();

    @Test
    void identicalChannelsDecodeAsOneTone() {
        double[] mono = DtmfGenerator.generate("5", MONO_CFG);
        double[] interleaved = new double[mono.length * 2];
        for (int i = 0; i < mono.length; i++) {
            interleaved[2 * i] = mono[i];
            interleaved[2 * i + 1] = mono[i];
        }

        List<DtmfTone> tones = DtmfDecoder.decode(interleaved, DOWNMIX_CFG);

        assertEquals(1, tones.size(),
                "downmix of identical channels must produce exactly one tone");
        assertEquals('5', tones.get(0).key());
        assertEquals(0, tones.get(0).channel(),
                "downmix emissions must tag channel=0");
    }

    @Test
    void leftOnlyHalfAmplitudeStillDecodes() {
        double[] mono = DtmfGenerator.generate("5", MONO_CFG);
        double[] interleaved = new double[mono.length * 2];
        for (int i = 0; i < mono.length; i++) {
            interleaved[2 * i] = mono[i];       // left has the signal
            interleaved[2 * i + 1] = 0.0;       // right is silent
        }
        // Downmix averages adjacent samples: (mono[i] + 0) / 2 = mono[i]/2,
        // i.e. the downmixed signal is the mono signal at half amplitude.

        List<DtmfTone> tones = DtmfDecoder.decode(interleaved, DOWNMIX_CFG);

        assertEquals(1, tones.size(),
                "half-amplitude downmix must still emit one tone under the "
                        + "default telephony config");
        assertEquals('5', tones.get(0).key());
        assertEquals(0, tones.get(0).channel(),
                "downmix emissions must tag channel=0");
    }
}
