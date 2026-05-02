package com.tino1b2be.dtmf;

// Feature: dtmf-v2-foundation, Property 15: Stereo downmix equals mono decode of the average

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.Size;

/**
 * Property-based test: {@link ChannelMode#STEREO_DOWNMIX STEREO_DOWNMIX}
 * decodes any interleaved stereo buffer identically to a mono decode of its
 * per-frame average.
 *
 * <p><strong>Property 15: Stereo downmix equals mono decode of the
 * average.</strong> <strong>Validates: Requirement 13.4.</strong>
 *
 * <p>For any random interleaved stereo buffer {@code x} (even length) and
 * two configs identical in everything but channel mode (one
 * {@link ChannelMode#STEREO_DOWNMIX}, the other {@link ChannelMode#MONO}),
 * {@code DtmfDecoder.decode(x, downmixCfg)} must produce exactly the same
 * sequence of {@link DtmfTone} records (field-by-field equal, in order) as
 * {@code DtmfDecoder.decode(downmix(x), monoCfg)}, where
 * {@code downmix(x)[i] = (x[2i] + x[2i+1]) / 2}.
 *
 * <p>The inputs are random PCM samples rather than DTMF-shaped signals
 * because the property is structural: it holds regardless of whether the
 * input happens to contain DTMF tones or not. Equality relies on
 * {@link DtmfTone} being an immutable record with canonical {@code equals}.
 */
class StereoDownmixEqualsMonoPropertyTest {

    private static final DtmfConfig DOWNMIX_CFG = DtmfConfig.advanced()
            .sampleRate(8000)
            .minimumToneDuration(Duration.ofMillis(60))
            .minimumGapDuration(Duration.ofMillis(40))
            .channelMode(ChannelMode.STEREO_DOWNMIX)
            .build();

    private static final DtmfConfig MONO_CFG = DtmfConfig.advanced()
            .sampleRate(8000)
            .minimumToneDuration(Duration.ofMillis(60))
            .minimumGapDuration(Duration.ofMillis(40))
            .channelMode(ChannelMode.MONO)
            .build();

    @Property(tries = 50)
    void downmixDecodeEqualsMonoDecodeOfAverage(
            @ForAll("interleavedStereo") @Size(max = 16_000) double[] stereo) {

        double[] mono = downmix(stereo);

        List<DtmfTone> viaDownmix = DtmfDecoder.decode(stereo, DOWNMIX_CFG);
        List<DtmfTone> viaMono = DtmfDecoder.decode(mono, MONO_CFG);

        assertEquals(viaMono, viaDownmix,
                "STEREO_DOWNMIX decode must equal MONO decode of "
                        + "the per-frame average");
    }

    /**
     * Per-frame average of an interleaved stereo buffer.
     *
     * @param x interleaved stereo PCM with {@code x.length} a multiple of 2
     * @return mono buffer of length {@code x.length / 2} where
     *         {@code out[i] = (x[2i] + x[2i+1]) / 2}
     */
    private static double[] downmix(double[] x) {
        double[] out = new double[x.length / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (x[2 * i] + x[2 * i + 1]) / 2.0;
        }
        return out;
    }

    /**
     * Interleaved stereo buffer: always even length so the downmix is
     * well-defined and {@code STEREO_DOWNMIX} accepts the input (odd-length
     * stereo input is rejected per Requirement 13.5).
     */
    @Provide
    Arbitrary<double[]> interleavedStereo() {
        Arbitrary<Double> samples = Arbitraries.doubles()
                .between(-1.0, 1.0).ofScale(9);
        return samples.array(double[].class)
                .filter(arr -> (arr.length & 1) == 0);
    }
}
