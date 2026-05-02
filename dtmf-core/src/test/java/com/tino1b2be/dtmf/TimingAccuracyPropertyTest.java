package com.tino1b2be.dtmf;

// Feature: dtmf-v2-foundation, Property 5: Timing accuracy within one analysis block

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import com.tino1b2be.dtmf.internal.FrequencyBins;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

/**
 * Property-based test: decoded tone timestamps are within one analysis block
 * of the true boundaries.
 *
 * <p><strong>Property 5: Timing accuracy within one analysis block.</strong>
 * <strong>Validates: Requirement 12.4.</strong>
 *
 * <p>For each DTMF key {@code k in {0-9, A-D, *, #}} and each
 * Supported_Sample_Rate {@code Fs in {8000, 16000, 44100, 48000}}, we build
 * a signal of the form
 * {@code zeros(lenSilenceBefore) ++ tone(k, lenTone) ++ zeros(lenSilenceAfter)}
 * with {@code lenTone} corresponding to a random duration {@code >= 40 ms}
 * and padding silences of random durations. Padding is held at
 * {@code >= 40 ms} (= two analysis blocks at every Supported_Sample_Rate) so
 * at least one fully-silent block is guaranteed to be processed after the
 * tone ends &mdash; that full silent block is what drives the
 * {@code ACTIVE -> ENDING} transition in the analysis-pipeline state
 * machine. The tone is synthesised directly from the Q.23 frequency pair
 * for {@code k} so we can pick {@code lenTone} to an exact sample count
 * rather than going through the generator (which emits
 * {@code round(minimumToneDuration * Fs)} samples).
 *
 * <p>Let {@code S_true = lenSilenceBefore} and {@code E_true = S_true +
 * lenTone}. Decoding the buffer must emit exactly one tone with
 * {@code key = k}, and that tone's sample indices must satisfy
 * {@code |startSample - S_true| <= analysisBlockSize} and
 * {@code |endSample - E_true| <= analysisBlockSize}.
 *
 * <p>The config uses generous margins (60&nbsp;ms tone duration, 40&nbsp;ms
 * gap) so the confirmation-frame logic never truncates or misses the
 * signal &mdash; the property is about timing precision, not threshold
 * tuning.
 */
class TimingAccuracyPropertyTest {

    @Property(tries = 40)
    void singleToneTimingWithinOneAnalysisBlock(
            @ForAll("dtmfKeys") Character keyBox,
            @ForAll("supportedRates") int sampleRate,
            @ForAll @LongRange(min = 80L, max = 300L) long toneMillis,
            @ForAll @IntRange(min = 40, max = 200) int silenceBeforeMillis,
            @ForAll @IntRange(min = 40, max = 200) int silenceAfterMillis) {

        char key = keyBox;
        DtmfConfig cfg = DtmfConfig.advanced()
                .sampleRate(sampleRate)
                .minimumToneDuration(Duration.ofMillis(60))
                .minimumGapDuration(Duration.ofMillis(40))
                .build();

        int lenSilenceBefore =
                (int) Math.round(silenceBeforeMillis / 1000.0 * sampleRate);
        int lenTone = (int) Math.round(toneMillis / 1000.0 * sampleRate);
        int lenSilenceAfter =
                (int) Math.round(silenceAfterMillis / 1000.0 * sampleRate);

        long sTrue = lenSilenceBefore;
        long eTrue = sTrue + lenTone;

        double[] audio =
                new double[lenSilenceBefore + lenTone + lenSilenceAfter];
        double[] pair = FrequencyBins.frequenciesFor(key);
        double lowHz = pair[0];
        double highHz = pair[1];
        double omegaLow = 2.0 * Math.PI * lowHz / sampleRate;
        double omegaHigh = 2.0 * Math.PI * highHz / sampleRate;
        for (int i = 0; i < lenTone; i++) {
            audio[lenSilenceBefore + i] =
                    0.5 * (Math.sin(omegaLow * i) + Math.sin(omegaHigh * i));
        }

        List<DtmfTone> tones = DtmfDecoder.decode(audio, cfg);

        assertEquals(1, tones.size(),
                "expected exactly one tone for key '" + key + "' at "
                        + sampleRate + " Hz, got " + tones.size());
        DtmfTone t = tones.get(0);
        assertEquals(key, t.key(),
                "decoded key must match generated key");

        int blockSize = cfg.analysisBlockSize();
        long startDelta = Math.abs(t.startSample() - sTrue);
        long endDelta = Math.abs(t.endSample() - eTrue);

        assertTrue(startDelta <= blockSize,
                "startSample " + t.startSample()
                        + " differs from S_true " + sTrue + " by " + startDelta
                        + " > analysisBlockSize " + blockSize);
        assertTrue(endDelta <= blockSize,
                "endSample " + t.endSample()
                        + " differs from E_true " + eTrue + " by " + endDelta
                        + " > analysisBlockSize " + blockSize);
    }

    @Provide
    Arbitrary<Character> dtmfKeys() {
        return Arbitraries.of('0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                'A', 'B', 'C', 'D', '*', '#');
    }

    @Provide
    Arbitrary<Integer> supportedRates() {
        return Arbitraries.of(8000, 16000, 44100, 48000);
    }
}
