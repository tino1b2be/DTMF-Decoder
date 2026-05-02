package com.tino1b2be.dtmf.io;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration test verifying that {@link AudioSources#open(Path)} throws
 * {@link UnsupportedAudioFormatException} with populated diagnostics when
 * no provider can identify the input (Requirements 16.4, 5.7, 6.4, 6.5).
 *
 * <p>The input is a deterministic 4 KB pseudorandom blob (fixed seed) with
 * no valid WAV or MP3 header. Both registered providers must score it
 * {@code -1}, and the resulting exception must carry both provider names
 * in {@link UnsupportedAudioFormatException#providersConsulted()} and
 * both scores of {@code -1} in
 * {@link UnsupportedAudioFormatException#providerScores()}.
 *
 * <p>The RNG seed ({@code 42}) is fixed so the test is reproducible; a
 * flaky failure on one run would be reproducible on the next. With a
 * 4096-byte sample the probability of a stray {@code "RIFF"} or
 * {@code 0xFF 0xFB} sync word landing in a valid WAV/MP3 header position
 * is negligible but non-zero for truly random bytes, so pinning the seed
 * is strictly more robust than calling {@code new Random()} here.
 *
 * @since 2.1.0
 */
final class UnrecognizedInputIT {

    /** Deterministic RNG seed used to generate the non-audio blob. */
    private static final long SEED = 42L;

    /** Size of the non-audio blob in bytes. */
    private static final int BLOB_SIZE = 4096;

    @Test
    @DisplayName("AudioSources.open(nonAudioPath) throws UnsupportedAudioFormatException "
            + "with both WAV and MP3 in providersConsulted and both scored -1")
    void nonAudioInputThrowsWithPopulatedDiagnostics(@TempDir Path tempDir) throws IOException {
        // Deterministic 4 KB non-audio blob.
        byte[] blob = new byte[BLOB_SIZE];
        new Random(SEED).nextBytes(blob);
        Path blobPath = tempDir.resolve("not-audio.bin");
        Files.write(blobPath, blob);

        UnsupportedAudioFormatException ex = assertThrows(
                UnsupportedAudioFormatException.class,
                () -> AudioSources.open(blobPath));

        Map<String, Integer> scores = ex.providerScores();
        assertAll(
                () -> assertTrue(
                        ex.providersConsulted().contains("WAV"),
                        () -> "Expected providersConsulted() to contain \"WAV\", "
                                + "got " + ex.providersConsulted()),
                () -> assertTrue(
                        ex.providersConsulted().contains("MP3"),
                        () -> "Expected providersConsulted() to contain \"MP3\", "
                                + "got " + ex.providersConsulted()),
                () -> assertEquals(
                        -1,
                        scores.get("WAV"),
                        () -> "Expected providerScores()[\"WAV\"] == -1, "
                                + "got " + scores),
                () -> assertEquals(
                        -1,
                        scores.get("MP3"),
                        () -> "Expected providerScores()[\"MP3\"] == -1, "
                                + "got " + scores));
    }
}
