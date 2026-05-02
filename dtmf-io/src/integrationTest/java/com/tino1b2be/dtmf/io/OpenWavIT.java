package com.tino1b2be.dtmf.io;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tino1b2be.dtmf.DtmfConfig;
import com.tino1b2be.dtmf.DtmfGenerator;
import com.tino1b2be.dtmf.DtmfTone;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration test verifying that {@link AudioSources#open(Path)} dispatches
 * to the WAV provider for a freshly generated PCM16 WAV fixture, and that
 * {@link DtmfFileDecoder#decode(Path, DtmfConfig)} recovers the original
 * DTMF key sequence end-to-end (Requirements 16.3, 16.5).
 *
 * <p>The round-trip here is deliberately simple: generate audio for
 * {@code "123"} via {@link DtmfGenerator} at telephony rate (8 kHz mono),
 * encode to PCM16 with the minimal local {@link IntegrationWavEncoder}
 * helper, write to a temp file, re-open through the SPI dispatch path,
 * assert the source comes from the {@code com.tino1b2be.dtmf.io.wav}
 * package, and feed the same file through {@code DtmfFileDecoder} to
 * recover the original keys.
 *
 * @since 2.1.0
 */
final class OpenWavIT {

    /** Package root required for the WAV provider's {@link AudioSource}. */
    private static final String WAV_PACKAGE = "com.tino1b2be.dtmf.io.wav";

    @Test
    @DisplayName("AudioSources.open(wavPath) returns an AudioSource in the wav package "
            + "and DtmfFileDecoder recovers the key sequence")
    void wavRoundTripDispatchesToWavProvider(@TempDir Path tempDir) throws IOException {
        // Generate a deterministic DTMF sequence via dtmf-core, encode to
        // PCM16 mono WAV, write to a temp file.
        String sequence = "123";
        DtmfConfig cfg = DtmfConfig.forTelephony();
        double[] samples = DtmfGenerator.generate(sequence, cfg);
        byte[] wavBytes = IntegrationWavEncoder.encodePcm16Mono(samples, cfg.sampleRate());

        Path wavPath = tempDir.resolve("round-trip.wav");
        Files.write(wavPath, wavBytes);

        // Open through the SPI facade and verify the winning provider is
        // the WAV one (its AudioSource lives under com.tino1b2be.dtmf.io.wav).
        String sourceClassName;
        try (AudioSource source = AudioSources.open(wavPath)) {
            sourceClassName = source.getClass().getName();
        }

        // Decode via DtmfFileDecoder and verify the recovered key sequence
        // matches the original input.
        List<DtmfTone> tones = DtmfFileDecoder.decode(wavPath, DtmfConfig.forTelephony());
        String recovered = tones.stream()
                .map(t -> String.valueOf(t.key()))
                .collect(Collectors.joining());

        assertAll(
                () -> assertTrue(
                        sourceClassName.startsWith(WAV_PACKAGE + "."),
                        () -> "Expected AudioSource class under " + WAV_PACKAGE
                                + ", got " + sourceClassName),
                () -> assertEquals(
                        sequence,
                        recovered,
                        () -> "Expected decoded key sequence \"" + sequence
                                + "\", got \"" + recovered + "\" (tones=" + tones + ")"));
    }
}
