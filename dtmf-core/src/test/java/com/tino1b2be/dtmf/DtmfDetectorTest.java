package com.tino1b2be.dtmf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DtmfDetector}. Covers Task 6.2:
 *
 * <ul>
 *   <li>callback fires exactly once per confirmed tone;</li>
 *   <li>re-registering via {@link DtmfDetector#onTone(java.util.function.Consumer)}
 *       replaces the previous callback;</li>
 *   <li>{@link DtmfDetector#flush()} on an empty detector emits nothing and
 *       does not throw;</li>
 *   <li>{@link DtmfDetector#samplesProcessed()} equals the cumulative input
 *       sample count across {@code process} calls;</li>
 *   <li>stereo modes reject odd-length input with
 *       {@link IllegalArgumentException}.</li>
 * </ul>
 */
class DtmfDetectorTest {

    @Test
    void nullConfigThrowsNpeNamingConfig() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new DtmfDetector(null));
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("config"),
                "expected message to mention 'config', was: " + ex.getMessage());
    }

    @Test
    void callbackFiresExactlyOncePerTone() {
        DtmfConfig cfg = forTestingConfig();
        double[] audio = DtmfGenerator.generate("123", cfg);

        List<DtmfTone> received = new ArrayList<>();
        DtmfDetector detector = new DtmfDetector(cfg);
        detector.onTone(received::add);
        detector.process(audio);
        detector.flush();

        assertEquals(3, received.size(),
                "expected one emission per tone in the sequence");
        assertEquals("123",
                received.stream()
                        .map(t -> String.valueOf(t.key()))
                        .reduce("", String::concat));
    }

    @Test
    void onToneReplacesPreviousCallback() {
        DtmfConfig cfg = forTestingConfig();
        double[] audio = DtmfGenerator.generate("5", cfg);

        AtomicInteger firstHits = new AtomicInteger();
        AtomicInteger secondHits = new AtomicInteger();

        DtmfDetector detector = new DtmfDetector(cfg);
        detector.onTone(t -> firstHits.incrementAndGet());
        // Replace before feeding any samples; the pipeline dispatches
        // through a forwarder, so the replacement must take effect.
        detector.onTone(t -> secondHits.incrementAndGet());

        detector.process(audio);
        detector.flush();

        assertEquals(0, firstHits.get(),
                "first callback must not be invoked after replacement");
        assertEquals(1, secondHits.get(),
                "second (current) callback must receive the emission");
    }

    @Test
    void flushOnEmptyDetectorEmitsNothingAndDoesNotThrow() {
        DtmfConfig cfg = forTestingConfig();
        List<DtmfTone> received = new ArrayList<>();
        DtmfDetector detector = new DtmfDetector(cfg);
        detector.onTone(received::add);

        detector.flush();

        assertEquals(0, received.size());
        assertEquals(0L, detector.samplesProcessed());
    }

    @Test
    void samplesProcessedEqualsCumulativeChunkLength() {
        DtmfConfig cfg = forTestingConfig();
        DtmfDetector detector = new DtmfDetector(cfg);

        detector.process(new double[100]);
        assertEquals(100L, detector.samplesProcessed());

        detector.process(new double[250]);
        assertEquals(350L, detector.samplesProcessed());

        detector.process(new double[0]);
        assertEquals(350L, detector.samplesProcessed());

        detector.process(new double[1], 0, 1);
        assertEquals(351L, detector.samplesProcessed());
    }

    @Test
    void stereoIndependentRejectsOddLengthDoubleInput() {
        DtmfConfig cfg = stereoConfig(ChannelMode.STEREO_INDEPENDENT);
        DtmfDetector detector = new DtmfDetector(cfg);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> detector.process(new double[3]));
        assertTrue(ex.getMessage().toLowerCase().contains("even"),
                "expected message to mention even length, was: " + ex.getMessage());
    }

    @Test
    void stereoDownmixRejectsOddLengthDoubleInput() {
        DtmfConfig cfg = stereoConfig(ChannelMode.STEREO_DOWNMIX);
        DtmfDetector detector = new DtmfDetector(cfg);

        assertThrows(IllegalArgumentException.class,
                () -> detector.process(new double[5]));
    }

    @Test
    void stereoIndependentRejectsOddLengthShortInput() {
        DtmfConfig cfg = stereoConfig(ChannelMode.STEREO_INDEPENDENT);
        DtmfDetector detector = new DtmfDetector(cfg);

        assertThrows(IllegalArgumentException.class,
                () -> detector.process(new short[3]));
    }

    @Test
    void processRejectsNullDoubleChunk() {
        DtmfDetector detector = new DtmfDetector(forTestingConfig());
        assertThrows(NullPointerException.class,
                () -> detector.process((double[]) null));
    }

    @Test
    void processRejectsNullShortChunk() {
        DtmfDetector detector = new DtmfDetector(forTestingConfig());
        assertThrows(NullPointerException.class,
                () -> detector.process((short[]) null));
    }

    @Test
    void processRejectsNullFloatChunk() {
        DtmfDetector detector = new DtmfDetector(forTestingConfig());
        assertThrows(NullPointerException.class,
                () -> detector.process((float[]) null));
    }

    @Test
    void processRejectsNullIntChunk() {
        DtmfDetector detector = new DtmfDetector(forTestingConfig());
        assertThrows(NullPointerException.class,
                () -> detector.process((int[]) null));
    }

    @Test
    void shortInputRoundTrips() {
        DtmfConfig cfg = forTestingConfig();
        double[] audio = DtmfGenerator.generate("7", cfg);
        short[] pcm16 = new short[audio.length];
        for (int i = 0; i < audio.length; i++) {
            pcm16[i] = (short) Math.round(audio[i] * 32767.0);
        }

        List<DtmfTone> received = new ArrayList<>();
        DtmfDetector detector = new DtmfDetector(cfg);
        detector.onTone(received::add);
        detector.process(pcm16);
        detector.flush();

        assertEquals(1, received.size());
        assertEquals('7', received.get(0).key());
    }

    // ----- helpers -----

    /**
     * Slightly more generous than {@code forTelephony()} so the timing-edge
     * tests pass deterministically: 60&nbsp;ms tone, 40&nbsp;ms gap.
     */
    private static DtmfConfig forTestingConfig() {
        return DtmfConfig.advanced()
                .sampleRate(8000)
                .minimumToneDuration(Duration.ofMillis(60))
                .minimumGapDuration(Duration.ofMillis(40))
                .build();
    }

    private static DtmfConfig stereoConfig(ChannelMode mode) {
        return DtmfConfig.advanced()
                .sampleRate(8000)
                .minimumToneDuration(Duration.ofMillis(60))
                .minimumGapDuration(Duration.ofMillis(40))
                .channelMode(mode)
                .build();
    }
}
