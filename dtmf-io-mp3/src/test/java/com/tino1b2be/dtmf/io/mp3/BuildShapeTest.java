package com.tino1b2be.dtmf.io.mp3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Build-shape smoke tests for {@code dtmf-io-mp3}.
 *
 * <p>These tests assert the static shape of the module:
 *
 * <ol>
 *   <li>The SPI registration resource at
 *       {@code META-INF/services/com.tino1b2be.dtmf.io.AudioSourceProvider}
 *       is present on the test classpath and its sole non-empty,
 *       non-comment line equals the FQCN of {@link Mp3AudioSourceProvider}
 *       (Requirement 10.2).</li>
 *   <li>Any {@code .mp3} fixture checked into
 *       {@code dtmf-io-mp3/src/test/resources/fixtures/} is at most
 *       200 KiB (Requirement 15.3). The current module relies on the
 *       aliasing wired in Task 1.5 and commits no fixtures directly,
 *       so the common case walks an empty directory and asserts
 *       nothing; if a later change commits fixtures, the size cap
 *       fires on anything oversized.</li>
 * </ol>
 */
class BuildShapeTest {

    private static final String SERVICES_FILE_NAME = "com.tino1b2be.dtmf.io.AudioSourceProvider";
    private static final String EXPECTED_PROVIDER_FQCN =
            "com.tino1b2be.dtmf.io.mp3.Mp3AudioSourceProvider";
    private static final long MAX_MP3_FIXTURE_BYTES = 200L * 1024L;

    @Test
    void spiRegistrationResourceDeclaresMp3Provider() throws IOException {
        URL resource = Mp3AudioSourceProvider.class.getClassLoader()
                .getResource("META-INF/services/" + SERVICES_FILE_NAME);
        assertNotNull(resource,
                "Requirement 10.2: META-INF/services/" + SERVICES_FILE_NAME
                        + " must be present on the classpath");

        List<String> declaredClasses = new ArrayList<>();
        try (InputStream in = resource.openStream();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String raw;
            while ((raw = reader.readLine()) != null) {
                int hash = raw.indexOf('#');
                String line = (hash < 0 ? raw : raw.substring(0, hash)).trim();
                if (!line.isEmpty()) {
                    declaredClasses.add(line);
                }
            }
        }

        assertEquals(1, declaredClasses.size(),
                "Requirement 10.2: " + SERVICES_FILE_NAME
                        + " must contain exactly one non-empty, non-comment "
                        + "line but found: " + declaredClasses);
        assertEquals(EXPECTED_PROVIDER_FQCN, declaredClasses.get(0),
                "Requirement 10.2: SPI registration must list exactly "
                        + EXPECTED_PROVIDER_FQCN);
    }

    @Test
    void committedMp3FixturesAreAtMost200KiB() throws IOException {
        Path fixturesDir = resolveFixturesDir();
        if (!Files.isDirectory(fixturesDir)) {
            // Task 7.5 chose the aliasing path (Option A) — shared
            // fixtures live in dtmf-core and are copied onto this
            // module's test classpath by processTestResources. No
            // committed fixtures means nothing to size-check here.
            return;
        }

        List<String> offenders = new ArrayList<>();
        Files.walkFileTree(fixturesDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().toLowerCase().endsWith(".mp3")) {
                    long size = attrs.size();
                    if (size > MAX_MP3_FIXTURE_BYTES) {
                        offenders.add(
                                fixturesDir.relativize(file) + " (" + size + " bytes)");
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        assertTrue(offenders.isEmpty(),
                "Requirement 15.3: committed MP3 fixtures must each be "
                        + "<= 200 KiB. Offenders: " + offenders);
    }

    private static Path resolveFixturesDir() {
        Path moduleLocal = Paths.get("src", "test", "resources", "fixtures")
                .toAbsolutePath().normalize();
        if (Files.isDirectory(moduleLocal)) {
            return moduleLocal;
        }
        return Paths.get("dtmf-io-mp3", "src", "test", "resources", "fixtures")
                .toAbsolutePath().normalize();
    }
}
