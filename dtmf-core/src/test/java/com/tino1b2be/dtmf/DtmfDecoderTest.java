package com.tino1b2be.dtmf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DtmfDecoder}. Covers Task 8.2:
 *
 * <ul>
 *   <li>empty input returns an empty list;</li>
 *   <li>pure silence returns an empty list;</li>
 *   <li>a single generated tone round-trips through
 *       {@link DtmfDecoder#decode(double[], DtmfConfig)};</li>
 *   <li>{@code decode(null, cfg)} raises {@link NullPointerException}
 *       naming {@code samples};</li>
 *   <li>{@code decode(samples, null)} raises {@link NullPointerException}
 *       naming {@code config};</li>
 *   <li>all overloads return lists in non-decreasing {@code startSample}
 *       order.</li>
 * </ul>
 */
class DtmfDecoderTest {

    private static final DtmfConfig CFG = DtmfConfig.advanced()
            .sampleRate(8000)
            .minimumToneDuration(Duration.ofMillis(60))
            .minimumGapDuration(Duration.ofMillis(40))
            .build();

    @Test
    void emptyInputReturnsEmptyList() {
        assertTrue(DtmfDecoder.decode(new double[0], CFG).isEmpty());
        assertTrue(DtmfDecoder.decode(new short[0], CFG).isEmpty());
        assertTrue(DtmfDecoder.decode(new float[0], CFG).isEmpty());
        assertTrue(DtmfDecoder.decode(new int[0], CFG).isEmpty());
        assertTrue(DtmfDecoder.decodePcm24(new int[0], CFG).isEmpty());
    }

    @Test
    void pureSilenceReturnsEmptyList() {
        double[] silence = new double[CFG.sampleRate() * 2]; // 2 s of zeros
        assertTrue(DtmfDecoder.decode(silence, CFG).isEmpty());
    }

    @Test
    void singleToneRoundTrips() {
        double[] audio = DtmfGenerator.generate("5", CFG);
        List<DtmfTone> tones = DtmfDecoder.decode(audio, CFG);
        assertEquals(1, tones.size());
        assertEquals('5', tones.get(0).key());
    }

    @Test
    void threeToneSequenceRoundTrips() {
        double[] audio = DtmfGenerator.generate("AB*", CFG);
        List<DtmfTone> tones = DtmfDecoder.decode(audio, CFG);
        assertEquals(3, tones.size());
        assertEquals('A', tones.get(0).key());
        assertEquals('B', tones.get(1).key());
        assertEquals('*', tones.get(2).key());
    }

    @Test
    void doubleDecodeNullSamplesThrowsNpeNamingSamples() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> DtmfDecoder.decode((double[]) null, CFG));
        assertMessageMentions(ex, "samples");
    }

    @Test
    void doubleDecodeNullConfigThrowsNpeNamingConfig() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> DtmfDecoder.decode(new double[0], null));
        assertMessageMentions(ex, "config");
    }

    @Test
    void shortDecodeNullSamplesThrowsNpeNamingSamples() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> DtmfDecoder.decode((short[]) null, CFG));
        assertMessageMentions(ex, "samples");
    }

    @Test
    void floatDecodeNullSamplesThrowsNpeNamingSamples() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> DtmfDecoder.decode((float[]) null, CFG));
        assertMessageMentions(ex, "samples");
    }

    @Test
    void intDecodeNullSamplesThrowsNpeNamingSamples() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> DtmfDecoder.decode((int[]) null, CFG));
        assertMessageMentions(ex, "samples");
    }

    @Test
    void pcm24DecodeNullSamplesThrowsNpeNamingSamples() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> DtmfDecoder.decodePcm24(null, CFG));
        assertMessageMentions(ex, "samples");
    }

    @Test
    void everyOverloadReturnsListNonDecreasingByStartSample() {
        double[] audio = DtmfGenerator.generate("123456", CFG);
        int len = audio.length;

        short[] pcm16 = new short[len];
        float[] pcmF = new float[len];
        int[] pcm32 = new int[len];
        int[] pcm24 = new int[len];
        for (int i = 0; i < len; i++) {
            pcm16[i] = (short) Math.round(audio[i] * 32767.0);
            pcmF[i] = (float) audio[i];
            pcm32[i] = (int) Math.round(audio[i] * (double) Integer.MAX_VALUE);
            // PCM24 packed: scale to 23-bit range, mask to 24 bits.
            int v24 = (int) Math.round(audio[i] * 8388607.0);
            pcm24[i] = v24 & 0xFFFFFF;
        }

        assertNonDecreasing(DtmfDecoder.decode(audio, CFG), "double[]");
        assertNonDecreasing(DtmfDecoder.decode(pcm16, CFG), "short[]");
        assertNonDecreasing(DtmfDecoder.decode(pcmF, CFG), "float[]");
        assertNonDecreasing(DtmfDecoder.decode(pcm32, CFG), "int[]");
        assertNonDecreasing(DtmfDecoder.decodePcm24(pcm24, CFG), "pcm24");
    }

    private static void assertNonDecreasing(List<DtmfTone> tones, String label) {
        for (int i = 1; i < tones.size(); i++) {
            assertTrue(tones.get(i).startSample() >= tones.get(i - 1).startSample(),
                    label + ": emissions must be non-decreasing in startSample at "
                            + i);
        }
    }

    private static void assertMessageMentions(RuntimeException ex, String needle) {
        assertNotNull(ex.getMessage(), "exception must carry a message");
        assertTrue(ex.getMessage().contains(needle),
                "expected message to mention '" + needle + "', was: " + ex.getMessage());
    }
}
