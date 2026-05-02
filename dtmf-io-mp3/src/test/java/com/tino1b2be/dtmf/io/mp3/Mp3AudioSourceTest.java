package com.tino1b2be.dtmf.io.mp3;

import com.tino1b2be.dtmf.io.AudioSource;
import com.tino1b2be.dtmf.io.UnsupportedAudioFormatException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link Mp3AudioSource} (package-private, exercised
 * through the public {@link Mp3AudioSourceProvider} surface) covering
 * Requirements 10.7, 10.9, 10.10, 10.11, and 10.13 &mdash; plus the
 * {@link UnsupportedAudioFormatException} translation path from
 * Requirement 10.8 / 12.4.
 *
 * <p>The tests run against the three MP3 fixtures aliased onto this
 * module's test classpath by the {@code processTestResources} wiring
 * in {@code build.gradle.kts} (Task 1.5, Task 7.5, Requirement 15.4):
 *
 * <ul>
 *   <li>{@code /shared-samples/12345678.mp3} &mdash; mono MPEG-1
 *       Layer III at 44.1 kHz, a generated DTMF "12345678" sequence</li>
 *   <li>{@code /shared-samples/jazz.mp3} &mdash; stereo MPEG-1
 *       Layer III at 44.1 kHz, non-DTMF audio (negative anchor for
 *       the stereo decode path)</li>
 *   <li>{@code /shared-samples/stereo.mp3} &mdash; stereo MPEG-1
 *       Layer III at 44.1 kHz, exercises the two-channel decode
 *       path</li>
 * </ul>
 *
 * <p>Fixtures load via {@link Class#getResourceAsStream(String)} and
 * are copied to a temp file when a {@link Path}-based entry point is
 * needed (the MP3 provider's primary surface). The temp-file dance
 * keeps the tests honest about the file-based API &mdash; in-memory
 * stream tests are a separate concern covered by other tasks.
 */
class Mp3AudioSourceTest {

    /**
     * Names of the three aliased MP3 fixtures. Parameterized tests
     * iterate this list so every fixture is exercised through every
     * universal assertion (open, read-to-completion, metadata, close).
     *
     * <p>Keeping the names as {@code String[]} rather than
     * {@code Path[]} lets {@code @ValueSource} drive the parameterized
     * tests without a custom {@code ArgumentsProvider}; the resource
     * lookup in each test method does the {@code String} → resource
     * URL resolution at invocation time.
     */
    private static final String[] MP3_FIXTURES = {
            "/shared-samples/12345678.mp3",
            "/shared-samples/jazz.mp3",
            "/shared-samples/stereo.mp3"
    };

    // =====================================================================
    // Fixture-driven reads: open, read-to-completion, metadata invariants
    // =====================================================================

    @ParameterizedTest(name = "Fixture {0}: open → read to completion → metadata invariants")
    @ValueSource(strings = {
            "/shared-samples/12345678.mp3",
            "/shared-samples/jazz.mp3",
            "/shared-samples/stereo.mp3"
    })
    @DisplayName("Open each MP3 fixture, read to completion, and assert metadata invariants (Req 10.7, 10.9, 10.10)")
    void openAndReadFixtureToCompletion(String resourcePath, @TempDir Path dir) throws IOException {
        Path file = materializeFixture(resourcePath, dir);

        Mp3AudioSourceProvider provider = new Mp3AudioSourceProvider();
        try (AudioSource source = provider.open(file)) {
            // Req 10.9: bit depth is 16 (JLayer + mp3spi conversion target).
            assertEquals(16, source.bitDepth(),
                    "MP3 sources always report 16-bit PCM output (Req 10.9), "
                            + "got " + source.bitDepth() + " for " + resourcePath);

            // Req 10.7: channel count is 1 (mono) or 2 (stereo or joint
            // stereo). The MP3 provider does not support higher channel
            // counts because MPEG Layer III itself does not define more
            // than two channels.
            int channels = source.channelCount();
            assertTrue(channels == 1 || channels == 2,
                    "channelCount() must be 1 or 2 (Req 10.7), got "
                            + channels + " for " + resourcePath);

            // Req 10.10: sample rate is the MP3 frame header's advertised
            // rate; positive is the universal invariant all three fixtures
            // satisfy (they are all 44.1 kHz in practice).
            int sampleRate = source.sampleRate();
            assertTrue(sampleRate > 0,
                    "sampleRate() must be > 0 (Req 10.10), got "
                            + sampleRate + " for " + resourcePath);

            // Drain the source to completion. Each read returns a frame
            // count in [0, 1024] (per the AudioSource contract); -1
            // signals end of stream (Req 3.6). The total number of
            // frames consumed must equal source.totalFrames() when it is
            // non-negative, and must be > 0 regardless (every fixture
            // has audio in it).
            double[] buffer = new double[1024 * channels];
            long framesConsumed = 0L;
            int iterations = 0;
            // Cap the iteration count so a decoder bug that returns 0
            // forever doesn't hang the test. 1_000_000 iterations at
            // 1024 frames each is ~1 billion frames, far beyond any
            // realistic MP3 fixture under 200 KiB.
            final int iterationCap = 1_000_000;
            while (iterations++ < iterationCap) {
                int n = source.read(buffer, 0, 1024);
                if (n < 0) {
                    break;
                }
                framesConsumed += n;
            }
            assertTrue(iterations < iterationCap,
                    "Read loop did not terminate within " + iterationCap
                            + " iterations; suspect a decode-layer bug");
            assertTrue(framesConsumed > 0L,
                    "Expected to consume at least one frame from "
                            + resourcePath + ", got " + framesConsumed);

            // currentFrame() must reflect the cumulative frames read
            // (Req 3.13).
            assertEquals(framesConsumed, source.currentFrame(),
                    "currentFrame() must equal total frames consumed; "
                            + "fixture=" + resourcePath);

            // totalFrames() invariant: either -1L (VBR or mp3spi couldn't
            // cheaply determine total — Req 10.13) or >= framesConsumed
            // because we drained the source. We allow equality plus a
            // small tolerance: mp3spi's frame-length estimate is sometimes
            // off by a frame or two at the tail, so permit the range
            // [framesConsumed - 2, framesConsumed + 2] as well as -1L.
            long reportedTotal = source.totalFrames();
            assertTrue(reportedTotal == -1L || Math.abs(reportedTotal - framesConsumed) <= 2,
                    "totalFrames() must be -1L (Req 10.13) or match "
                            + "frames consumed within a 2-frame tolerance; "
                            + "reported=" + reportedTotal + ", consumed="
                            + framesConsumed);
        }
    }

    // =====================================================================
    // Seek: canSeek() == false, seek throws UnsupportedOperationException
    // =====================================================================

    @Test
    @DisplayName("canSeek() is false and seek(0) throws UnsupportedOperationException naming Mp3AudioSource (Req 10.11)")
    void mp3SourceIsForwardOnly(@TempDir Path dir) throws IOException {
        Path file = materializeFixture("/shared-samples/12345678.mp3", dir);

        Mp3AudioSourceProvider provider = new Mp3AudioSourceProvider();
        try (AudioSource source = provider.open(file)) {
            assertFalse(source.canSeek(),
                    "MP3 sources are forward-only (Req 10.11); canSeek() must be false");

            UnsupportedOperationException ex = assertThrows(
                    UnsupportedOperationException.class,
                    () -> source.seek(0L),
                    "seek(...) on a forward-only source must throw "
                            + "UnsupportedOperationException (Req 10.11)");

            // The exception's message must identify the class so callers
            // who catch UnsupportedOperationException generically can
            // tell which source refused. "Mp3AudioSource" is the exact
            // class name to look for — the task wording pins it.
            assertNotNull(ex.getMessage(),
                    "UnsupportedOperationException message must not be null");
            assertTrue(ex.getMessage().contains("Mp3AudioSource"),
                    "Expected the exception message to identify "
                            + "Mp3AudioSource, got: " + ex.getMessage());
        }
    }

    // =====================================================================
    // Close: idempotence
    // =====================================================================

    @Test
    @DisplayName("close() is idempotent: two and three calls in a row are all no-ops after the first")
    void closeIsIdempotent(@TempDir Path dir) throws IOException {
        Path file = materializeFixture("/shared-samples/12345678.mp3", dir);

        Mp3AudioSourceProvider provider = new Mp3AudioSourceProvider();
        AudioSource source = provider.open(file);
        // First close: should succeed without throwing.
        source.close();
        // Second close: must be a no-op per the AudioSource contract
        // (Req 3.14) and the Mp3AudioSource Javadoc (close() is
        // idempotent).
        source.close();
        // Third close for good measure.
        source.close();

        // After close, read(...) and seek(...) must throw IOException
        // identifying the source as closed (Req 3.14). Verify this
        // behaviour holds too, since it is the close-state guard that
        // makes idempotent close meaningful.
        double[] buffer = new double[16];
        IOException readEx = assertThrows(IOException.class,
                () -> source.read(buffer, 0, 8),
                "read(...) after close must throw IOException (Req 3.14)");
        assertTrue(readEx.getMessage() != null
                        && readEx.getMessage().toLowerCase().contains("closed"),
                "Post-close IOException should mention the source is closed; "
                        + "got: " + readEx.getMessage());
    }

    // =====================================================================
    // UnsupportedAudioFormatException translation (Req 10.8, 12.4)
    // =====================================================================

    @Test
    @DisplayName("Passing a WAV fixture (MS-ADPCM) to open(Path) → UnsupportedAudioFormatException wrapping AudioSystem's UnsupportedAudioFileException")
    void wavFixtureTranslatesToUnsupportedAudioFormatException(@TempDir Path dir) throws IOException {
        // Build a minimal RIFF/WAVE file whose fmt chunk advertises the
        // Microsoft ADPCM encoding (wFormatTag = 0x0002). The JDK's
        // built-in WAV AudioFileReader recognises the RIFF/WAVE
        // container but rejects this format tag with
        // UnsupportedAudioFileException; mp3spi doesn't pick up WAV
        // files at all. AudioSystem.getAudioInputStream therefore
        // throws UnsupportedAudioFileException, which
        // Mp3AudioSourceProvider.open(Path) catches and translates to
        // UnsupportedAudioFormatException with the cause preserved
        // (Req 10.8, 12.4).
        //
        // This path is the exact translation contract the task anchors:
        // format-level rejection surfaces as UAFE, not as raw
        // IOException, so callers can distinguish "bytes aren't MP3"
        // from "disk is broken."
        byte[] wav = buildWavWithFormatTag(0x0002 /* WAVE_FORMAT_ADPCM */);
        Path file = Files.createTempFile(dir, "adpcm", ".wav");
        Files.write(file, wav);

        Mp3AudioSourceProvider provider = new Mp3AudioSourceProvider();
        UnsupportedAudioFormatException ex = assertThrows(
                UnsupportedAudioFormatException.class,
                () -> provider.open(file),
                "A non-MP3 RIFF/WAVE file must surface as "
                        + "UnsupportedAudioFormatException (Req 10.8, 12.4)");

        // The cause chain must be preserved so callers inspecting the
        // chain can see the mp3spi/AudioSystem rejection reason
        // (Req 12.5).
        assertNotNull(ex.getCause(),
                "UnsupportedAudioFormatException must preserve the "
                        + "underlying cause (Req 12.4, 12.5)");
        // AudioSystem throws javax.sound.sampled.UnsupportedAudioFileException;
        // assert the cause is that exact type (or a subtype) so the
        // translation layer is provably in place.
        assertTrue(
                ex.getCause() instanceof javax.sound.sampled.UnsupportedAudioFileException,
                "Cause must be javax.sound.sampled.UnsupportedAudioFileException, "
                        + "got " + ex.getCause().getClass().getName());
    }

    // =====================================================================
    // Fixture helpers
    // =====================================================================

    /**
     * Copy a classpath-aliased MP3 fixture to a temp file so it can be
     * opened through the {@link Path}-based MP3 provider surface.
     *
     * <p>The shared-samples aliasing wires the three MP3 fixtures at
     * {@code /shared-samples/...} on the test classpath; the
     * {@code getResourceAsStream} lookup uses that path verbatim. The
     * returned temp file carries a {@code .mp3} extension so diagnostic
     * tooling (e.g., a failing-test console that prints the path) makes
     * the fixture's origin obvious.
     *
     * @param resourcePath classpath-relative path starting with
     *                     {@code "/shared-samples/"}
     * @param dir          JUnit-supplied temp directory; materialised
     *                     file is unique per test method
     * @return absolute {@link Path} to the copied fixture
     * @throws IOException if the resource is missing or the copy fails
     */
    private static Path materializeFixture(String resourcePath, Path dir) throws IOException {
        String baseName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
        Path file = Files.createTempFile(dir, "fixture-" + baseName, ".mp3");
        try (InputStream in = Mp3AudioSourceTest.class.getResourceAsStream(resourcePath)) {
            assertNotNull(in, "Fixture " + resourcePath
                    + " must be on the test classpath (Task 1.5 aliasing)");
            Files.copy(in, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return file;
    }

    /**
     * Build a minimal RIFF/WAVE file with a non-PCM {@code wFormatTag}.
     *
     * <p>Structure: 12-byte outer header ({@code RIFF | size | WAVE}) +
     * 24-byte {@code fmt } chunk (8-byte header + 16-byte classic
     * PCMWAVEFORMAT payload with the caller-supplied format tag) +
     * 12-byte {@code data} chunk (8-byte header + 4 payload bytes).
     * All multi-byte fields are little-endian per the RIFF
     * specification.
     *
     * <p>This is a deliberately self-contained WAV builder &mdash; the
     * MP3 test module cannot depend on the WAV test module's
     * {@code WavEncoder}, and pulling in {@code javax.sound.sampled}
     * encoders would defeat the purpose of a translation-path test.
     *
     * @param formatTag the 16-bit {@code wFormatTag} value to embed;
     *                  {@code 0x0001} would produce a valid PCM WAV,
     *                  {@code 0x0002} is MS ADPCM (what the
     *                  UAFE-translation test wants)
     * @return complete WAV file as a fresh {@code byte[]}
     */
    private static byte[] buildWavWithFormatTag(int formatTag) {
        final int channels = 1;
        final int sampleRate = 8000;
        final int bitsPerSample = 16;
        final int bytesPerSample = bitsPerSample / 8;
        final int blockAlign = channels * bytesPerSample;
        final int avgBytesPerSec = sampleRate * blockAlign;
        final byte[] samples = {0x01, 0x00, 0x02, 0x00};

        // Outer form size = 4 (WAVE) + 8 + 16 (fmt) + 8 + samples.length (data).
        int fmtPayloadSize = 16;
        int dataSize = samples.length;
        int total = 12 + (8 + fmtPayloadSize) + (8 + dataSize);

        ByteBuffer buf = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);

        // RIFF/WAVE outer header.
        buf.put((byte) 'R').put((byte) 'I').put((byte) 'F').put((byte) 'F');
        buf.putInt(total - 8);
        buf.put((byte) 'W').put((byte) 'A').put((byte) 'V').put((byte) 'E');

        // fmt chunk.
        buf.put((byte) 'f').put((byte) 'm').put((byte) 't').put((byte) ' ');
        buf.putInt(fmtPayloadSize);
        buf.putShort((short) formatTag);
        buf.putShort((short) channels);
        buf.putInt(sampleRate);
        buf.putInt(avgBytesPerSec);
        buf.putShort((short) blockAlign);
        buf.putShort((short) bitsPerSample);

        // data chunk.
        buf.put((byte) 'd').put((byte) 'a').put((byte) 't').put((byte) 'a');
        buf.putInt(dataSize);
        buf.put(samples);

        return buf.array();
    }
}
