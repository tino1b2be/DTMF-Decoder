package com.tino1b2be.dtmf.io;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.tino1b2be.dtmf.DtmfConfig;
import com.tino1b2be.dtmf.DtmfTone;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Integration test verifying that {@link AudioSources#open(Path)} dispatches
 * to the MP3 provider for committed MP3 fixtures, and that
 * {@link DtmfFileDecoder#decode(Path, DtmfConfig)} can drive a full decode
 * through the SPI end-to-end (Requirements 16.3, 16.5).
 *
 * <p>The MP3 fixtures live under
 * {@code dtmf-core/src/integrationTest/resources/samples/}. They are not
 * aliased onto this module's {@code integrationTest} classpath by default,
 * so this test reaches them via a path resolved relative to Gradle's
 * working directory for the {@code :dtmf-io:integrationTest} task, which
 * is the {@code dtmf-io} project directory. Each fixture is located at
 * {@code ../dtmf-core/src/integrationTest/resources/samples/<name>}.
 *
 * <h2>Non-emptiness assertions</h2>
 *
 * Only {@code 12345678.mp3} is known to contain DTMF tones (the eight
 * digits in the filename). {@code jazz.mp3} and {@code stereo.mp3} are
 * included to confirm the SPI dispatch and decode pipeline run cleanly on
 * real CBR and stereo MPEG-1 Layer III content, but the tone content
 * varies: {@code jazz.mp3} is non-DTMF music and {@code stereo.mp3}'s DTMF
 * content is not guaranteed. For those two fixtures the test asserts only
 * that {@code AudioSources.open(...)} returns an MP3-provider source and
 * that {@code DtmfFileDecoder.decode(...)} returns without error. For
 * {@code 12345678.mp3} the test additionally asserts the tone list is
 * non-empty.
 *
 * <h2>Fixture-availability guard</h2>
 *
 * If a fixture is not reachable from the working directory, the test
 * skips that parameterised case via {@link org.junit.jupiter.api.Assumptions}.
 * This makes the suite robust to running from an IDE whose working
 * directory happens to be the repo root rather than the module
 * directory.
 *
 * @since 2.0.0
 */
final class OpenMp3IT {

    /** Package root required for the MP3 provider's {@link AudioSource}. */
    private static final String MP3_PACKAGE = "com.tino1b2be.dtmf.io.mp3";

    /** Fixture with known DTMF content (the eight digits in its name). */
    private static final String DTMF_FIXTURE = "12345678.mp3";

    @ParameterizedTest(name = "fixture={0}")
    @ValueSource(strings = {"12345678.mp3", "jazz.mp3", "stereo.mp3"})
    @DisplayName("AudioSources.open(mp3Path) returns an AudioSource in the mp3 package "
            + "and DtmfFileDecoder decodes without error")
    void mp3DispatchesToMp3Provider(String fixtureName) throws IOException {
        Path fixture = locateFixture(fixtureName);
        assumeTrue(Files.exists(fixture),
                () -> "MP3 fixture not reachable at " + fixture.toAbsolutePath()
                        + "; set the working directory to the dtmf-io module root "
                        + "or copy the fixture into dtmf-io's integrationTest resources.");

        // 1. AudioSources.open(...) must return an MP3-provider source.
        String sourceClassName;
        try (AudioSource source = AudioSources.open(fixture)) {
            sourceClassName = source.getClass().getName();
        }
        assertTrue(
                sourceClassName.startsWith(MP3_PACKAGE + "."),
                () -> "Expected AudioSource class under " + MP3_PACKAGE
                        + ", got " + sourceClassName);

        // 2. DtmfFileDecoder.decode(...) must drive the full pipeline
        // without error. For the DTMF fixture we additionally assert the
        // tone list is non-empty; the other fixtures exercise the happy
        // path but their tone content is not guaranteed.
        List<DtmfTone> tones = DtmfFileDecoder.decode(fixture, DtmfConfig.forNoisyAudio());
        if (DTMF_FIXTURE.equals(fixtureName)) {
            assertFalse(
                    tones.isEmpty(),
                    () -> "Expected non-empty tone list for " + fixtureName
                            + ", got " + tones);
        }
    }

    /**
     * Resolve the path to an MP3 fixture under
     * {@code dtmf-core/src/integrationTest/resources/samples/}. The
     * returned path is relative to Gradle's working directory for the
     * {@code :dtmf-io:integrationTest} task (the {@code dtmf-io} project
     * directory), so {@code ../dtmf-core/...} reaches the shared fixtures
     * without requiring a {@code processIntegrationTestResources} alias.
     */
    private static Path locateFixture(String name) {
        return Paths.get("..", "dtmf-core", "src", "integrationTest",
                "resources", "samples", name);
    }
}
