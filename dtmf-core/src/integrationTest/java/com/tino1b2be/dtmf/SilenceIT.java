package com.tino1b2be.dtmf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 60-second all-zeros silence integration test (Requirement&nbsp;12.2).
 *
 * <p>For every Supported_Sample_Rate, a 60-second {@code double[]} of zeros
 * must decode to an empty emission list. The shorter-silence equivalent is
 * covered by Property 4 in the regular unit suite (5-second silence at the
 * same four rates); this integration variant pushes the length out to the
 * full 60 seconds that the requirement calls out, which is too long to be
 * comfortable in the default {@code test} task.
 *
 * <p>Buffers here are large (2.88&nbsp;million samples at 48&nbsp;kHz), so
 * this test lives in the {@code integrationTest} source set and is tagged
 * {@code slow}.
 *
 * @since 2.0.0
 */
@Tag("slow")
final class SilenceIT {

    @ParameterizedTest(name = "sampleRate = {0} Hz, 60 s of zeros")
    @ValueSource(ints = {8000, 16000, 44100, 48000})
    void sixtySecondsOfSilenceProducesNoTones(int sampleRate) {
        DtmfConfig cfg = DtmfConfig.advanced()
                .sampleRate(sampleRate)
                .build();

        int samples = sampleRate * 60;
        double[] silence = new double[samples]; // Java zero-initialises.

        List<DtmfTone> detected = DtmfDecoder.decode(silence, cfg);

        assertTrue(
                detected.isEmpty(),
                () -> String.format(
                        "Expected zero tones from 60 s of silence at %d Hz, "
                                + "but decoder emitted %d: %s",
                        sampleRate, detected.size(), detected));
    }
}
