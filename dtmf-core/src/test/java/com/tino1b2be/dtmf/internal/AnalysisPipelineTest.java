package com.tino1b2be.dtmf.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.tino1b2be.dtmf.DtmfConfig;
import com.tino1b2be.dtmf.DtmfTone;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AnalysisPipeline} covering the five state-machine
 * scenarios required by Task 5.7:
 *
 * <ul>
 *   <li>empty input + {@code flush()} emits nothing;</li>
 *   <li>exactly one block of silence stays in the {@code Idle} state and
 *       emits nothing;</li>
 *   <li>a 100&nbsp;ms DTMF '5' tone followed by silence emits exactly one
 *       tone with the correct key and sample timing within
 *       {@code ±1} analysis block;</li>
 *   <li>a 20&nbsp;ms tone &mdash; below the 40&nbsp;ms
 *       {@code forTelephony} minimum &mdash; emits nothing;</li>
 *   <li>a tone whose confirmation is interrupted by noise before it can be
 *       promoted to {@code Active} emits nothing.</li>
 * </ul>
 *
 * <p>The helper {@link #dtmfTone(double, double, int)} generates a clean
 * two-tone sum at the nominal amplitude the confidence scorer and twist
 * evaluator are tuned for. The ITU-T Q.23 frequency pair for key '5' is
 * 770 Hz + 1336 Hz.
 */
class AnalysisPipelineTest {

    /** Sample rate used by every test (matches {@code forTelephony}). */
    private static final int FS = 8000;

    /** Low-group frequency for key '5' per ITU-T Q.23. */
    private static final double KEY5_LOW_HZ = 770.0;

    /** High-group frequency for key '5' per ITU-T Q.23. */
    private static final double KEY5_HIGH_HZ = 1336.0;

    @Test
    void emptyInputThenFlushEmitsNothing() {
        DtmfConfig cfg = DtmfConfig.forTelephony();
        List<DtmfTone> emissions = new ArrayList<>();
        AnalysisPipeline pipeline = new AnalysisPipeline(cfg, 0, emissions::add);

        pipeline.flush();

        assertEquals(0, emissions.size(),
                "flush on empty pipeline must emit nothing");
        assertEquals(0L, pipeline.samplesProcessed());
    }

    @Test
    void singleBlockOfSilenceEmitsNothing() {
        DtmfConfig cfg = DtmfConfig.forTelephony();
        List<DtmfTone> emissions = new ArrayList<>();
        AnalysisPipeline pipeline = new AnalysisPipeline(cfg, 0, emissions::add);

        // Exactly one analysis block of zeros. That triggers one
        // block evaluation whose confidence is 0, so no candidate is
        // produced and the state machine stays in Idle.
        double[] silence = new double[cfg.analysisBlockSize()];
        pipeline.acceptAll(silence, 0, silence.length);
        pipeline.flush();

        assertEquals(0, emissions.size(),
                "pure silence must not produce a candidate");
        assertEquals(cfg.analysisBlockSize(), pipeline.samplesProcessed());
    }

    @Test
    void longerSilenceStillEmitsNothing() {
        // A batch of silence longer than one block must still emit nothing
        // and leave samplesProcessed correct.
        DtmfConfig cfg = DtmfConfig.forTelephony();
        List<DtmfTone> emissions = new ArrayList<>();
        AnalysisPipeline pipeline = new AnalysisPipeline(cfg, 0, emissions::add);

        int samples = cfg.analysisBlockSize() * 10;
        pipeline.acceptAll(new double[samples], 0, samples);
        pipeline.flush();

        assertEquals(0, emissions.size());
        assertEquals(samples, pipeline.samplesProcessed());
    }

    @Test
    void hundredMsKeyFiveEmitsOneToneWithCorrectTiming() {
        DtmfConfig cfg = DtmfConfig.forTelephony();
        int blockSize = cfg.analysisBlockSize();
        int toneSamples = 100 * FS / 1000; // 100 ms = 800 samples at 8 kHz
        int silenceSamples = 100 * FS / 1000;

        double[] tone = dtmfTone(KEY5_LOW_HZ, KEY5_HIGH_HZ, toneSamples);
        double[] silence = new double[silenceSamples];

        List<DtmfTone> emissions = new ArrayList<>();
        AnalysisPipeline pipeline = new AnalysisPipeline(cfg, 0, emissions::add);
        pipeline.acceptAll(tone, 0, tone.length);
        pipeline.acceptAll(silence, 0, silence.length);
        pipeline.flush();

        assertEquals(1, emissions.size(),
                "expected exactly one emission for a single clean tone");
        DtmfTone emitted = emissions.get(0);
        assertEquals('5', emitted.key());
        assertEquals(FS, emitted.sampleRate());
        assertEquals(0, emitted.channel());
        assertTrue(emitted.confidence() >= cfg.detectionThreshold(),
                "confidence must clear the detection threshold, was "
                        + emitted.confidence());

        // Timing must land within +/- one block of the true edges.
        long expectedStart = 0L;
        long expectedEnd = toneSamples;
        assertTrue(Math.abs(emitted.startSample() - expectedStart) <= blockSize,
                "startSample " + emitted.startSample()
                        + " must be within +/-" + blockSize
                        + " of true start " + expectedStart);
        assertTrue(Math.abs(emitted.endSample() - expectedEnd) <= blockSize,
                "endSample " + emitted.endSample()
                        + " must be within +/-" + blockSize
                        + " of true end " + expectedEnd);
    }

    @Test
    void twentyMsToneBelowMinimumDurationEmitsNothing() {
        DtmfConfig cfg = DtmfConfig.forTelephony();
        // 20 ms at 8 kHz = 160 samples = exactly one analysis block. The
        // tone never survives long enough to clear the 2-frame
        // confirmation count plus the 40 ms minimum duration.
        int toneSamples = 20 * FS / 1000;
        int silenceSamples = 100 * FS / 1000;

        double[] tone = dtmfTone(KEY5_LOW_HZ, KEY5_HIGH_HZ, toneSamples);
        double[] silence = new double[silenceSamples];

        List<DtmfTone> emissions = new ArrayList<>();
        AnalysisPipeline pipeline = new AnalysisPipeline(cfg, 0, emissions::add);
        pipeline.acceptAll(tone, 0, tone.length);
        pipeline.acceptAll(silence, 0, silence.length);
        pipeline.flush();

        assertEquals(0, emissions.size(),
                "tone shorter than minimumToneDuration must not emit");
    }

    @Test
    void toneInterruptedMidConfirmationEmitsNothing() {
        DtmfConfig cfg = DtmfConfig.forTelephony();
        // One block of clean '5' (starts Confirming), then one block of
        // white-ish noise that will not confirm the same key. Confirmation
        // is dropped and no tone is emitted.
        int blockSize = cfg.analysisBlockSize();
        double[] toneBlock = dtmfTone(KEY5_LOW_HZ, KEY5_HIGH_HZ, blockSize);
        double[] noiseBlock = pseudoRandomNoise(blockSize, 0xDEADBEEFL);
        double[] trailingSilence = new double[blockSize * 5];

        List<DtmfTone> emissions = new ArrayList<>();
        AnalysisPipeline pipeline = new AnalysisPipeline(cfg, 0, emissions::add);
        pipeline.acceptAll(toneBlock, 0, toneBlock.length);
        pipeline.acceptAll(noiseBlock, 0, noiseBlock.length);
        pipeline.acceptAll(trailingSilence, 0, trailingSilence.length);
        pipeline.flush();

        assertEquals(0, emissions.size(),
                "interruption before confirmation must drop the candidate");
    }

    @Test
    void acceptSampleByLoopMatchesAcceptAll() {
        // Sanity check that the single-sample entry point and the bulk one
        // behave identically for the same input.
        DtmfConfig cfg = DtmfConfig.forTelephony();
        int toneSamples = 80 * FS / 1000;
        double[] tone = dtmfTone(KEY5_LOW_HZ, KEY5_HIGH_HZ, toneSamples);
        double[] audio = new double[tone.length + cfg.analysisBlockSize() * 3];
        System.arraycopy(tone, 0, audio, 0, tone.length);

        List<DtmfTone> loopEmissions = new ArrayList<>();
        AnalysisPipeline loopPipeline =
                new AnalysisPipeline(cfg, 0, loopEmissions::add);
        for (double s : audio) {
            loopPipeline.accept(s);
        }
        loopPipeline.flush();

        List<DtmfTone> bulkEmissions = new ArrayList<>();
        AnalysisPipeline bulkPipeline =
                new AnalysisPipeline(cfg, 0, bulkEmissions::add);
        bulkPipeline.acceptAll(audio, 0, audio.length);
        bulkPipeline.flush();

        assertEquals(bulkEmissions, loopEmissions,
                "accept(double) loop and acceptAll must produce the same emissions");
    }

    @Test
    void channelTagIsPropagatedToEmissions() {
        DtmfConfig cfg = DtmfConfig.forTelephony();
        int toneSamples = 80 * FS / 1000;
        double[] tone = dtmfTone(KEY5_LOW_HZ, KEY5_HIGH_HZ, toneSamples);
        double[] silence = new double[cfg.analysisBlockSize() * 3];

        List<DtmfTone> emissions = new ArrayList<>();
        AnalysisPipeline pipeline = new AnalysisPipeline(cfg, 1, emissions::add);
        pipeline.acceptAll(tone, 0, tone.length);
        pipeline.acceptAll(silence, 0, silence.length);
        pipeline.flush();

        assertEquals(1, emissions.size());
        assertEquals(1, emissions.get(0).channel(),
                "channel tag set at construction must appear on every emission");
    }

    @Test
    void flushForceEmitsInProgressActiveTone() {
        // If the stream ends while a tone is still Active, flush() should
        // force-emit using the cumulative sample count as the tentative
        // end, provided the duration so far meets the minimum.
        DtmfConfig cfg = DtmfConfig.forTelephony();
        int toneSamples = 80 * FS / 1000; // 80 ms, well above the 40 ms min.
        double[] tone = dtmfTone(KEY5_LOW_HZ, KEY5_HIGH_HZ, toneSamples);

        List<DtmfTone> emissions = new ArrayList<>();
        AnalysisPipeline pipeline = new AnalysisPipeline(cfg, 0, emissions::add);
        pipeline.acceptAll(tone, 0, tone.length);
        // No trailing silence; flush must finalise the in-flight tone.
        pipeline.flush();

        assertEquals(1, emissions.size(),
                "flush must emit a tone still in Active when duration is long enough");
        assertEquals('5', emissions.get(0).key());
    }

    // --- Helpers ---

    /**
     * Generate {@code samples} samples of a clean DTMF tone at the given
     * low and high frequencies, at the amplitude the library's generator
     * uses (0.5 * sin + 0.5 * sin, combined peak 0.5).
     */
    private static double[] dtmfTone(double lowHz, double highHz, int samples) {
        double[] out = new double[samples];
        for (int i = 0; i < samples; i++) {
            double t = (double) i / (double) FS;
            out[i] = 0.5 * (Math.sin(2.0 * Math.PI * lowHz * t)
                    + Math.sin(2.0 * Math.PI * highHz * t));
        }
        return out;
    }

    /**
     * Deterministic pseudo-random noise in {@code [-1, 1]} for tests that
     * need a reproducible "not-a-DTMF-pair" signal.
     */
    private static double[] pseudoRandomNoise(int samples, long seed) {
        java.util.Random rng = new java.util.Random(seed);
        double[] out = new double[samples];
        for (int i = 0; i < samples; i++) {
            out[i] = 2.0 * rng.nextDouble() - 1.0;
        }
        return out;
    }
}
