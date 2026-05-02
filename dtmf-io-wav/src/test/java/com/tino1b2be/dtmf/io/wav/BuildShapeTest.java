package com.tino1b2be.dtmf.io.wav;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
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
 * Build-shape smoke tests for {@code dtmf-io-wav}.
 *
 * <p>These tests assert the static shape of the {@code dtmf-io-wav} module
 * rather than any runtime behavior:
 *
 * <ol>
 *   <li>No source file under {@code dtmf-io-wav/src/main/java} contains
 *       the substring {@code "import javax.sound.sampled"}. Requirement 9.12
 *       states the WAV reader is a clean-room RIFF parser that does not
 *       delegate to {@code javax.sound.sampled} for format decoding; this
 *       test pins that constraint in the build so a future refactor cannot
 *       accidentally reintroduce the dependency.</li>
 *   <li>The SPI registration file at
 *       {@code dtmf-io-wav/src/main/resources/META-INF/services/com.tino1b2be.dtmf.io.AudioSourceProvider}
 *       exists and contains exactly one non-empty, non-comment line whose
 *       content equals the fully qualified class name of
 *       {@link WavAudioSourceProvider} (Requirement 9.2).</li>
 * </ol>
 *
 * <p>The test walks the repository layout from {@code user.dir}. Gradle
 * runs unit tests with the module directory as the working directory, so
 * {@code src/main/java} is always reachable as a relative path. For IDE
 * runs that keep the repository root as the working directory, the helper
 * falls back to {@code dtmf-io-wav/src/main/java} (and the analogous
 * resources root).
 */
class BuildShapeTest {

    /**
     * The forbidden substring. Any production source file containing this
     * literal pulls in {@code javax.sound.sampled}, violating the clean-room
     * contract of Requirement 9.12.
     */
    private static final String FORBIDDEN_IMPORT = "import javax.sound.sampled";

    /** Fully qualified class name of the SPI interface (the services file's name). */
    private static final String SERVICES_FILE_NAME = "com.tino1b2be.dtmf.io.AudioSourceProvider";

    /** Expected single line of the services file (Requirement 9.2). */
    private static final String EXPECTED_PROVIDER_FQCN =
            "com.tino1b2be.dtmf.io.wav.WavAudioSourceProvider";

    /**
     * Walk every Java source file under {@code dtmf-io-wav/src/main/java}
     * and assert none of them contain the literal
     * {@code "import javax.sound.sampled"} (Requirement 9.12).
     *
     * <p>A substring check is sufficient because Java forbids whitespace
     * inside a qualified name and a dotted prefix match covers every member
     * import under the package ({@code javax.sound.sampled.AudioFormat},
     * {@code javax.sound.sampled.spi.AudioFileReader}, and any future
     * subpackage).
     */
    @Test
    void noJavaxSoundSampledImportInMain() throws IOException {
        Path sourceRoot = resolveSourceRoot();
        assertTrue(
                Files.isDirectory(sourceRoot),
                "Expected dtmf-io-wav source root at " + sourceRoot
                        + " but it does not exist or is not a directory");

        List<String> offenders = new ArrayList<>();
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.getFileName().toString().endsWith(".java")) {
                    return FileVisitResult.CONTINUE;
                }
                String content = Files.readString(file, StandardCharsets.UTF_8);
                if (content.contains(FORBIDDEN_IMPORT)) {
                    offenders.add(sourceRoot.relativize(file).toString());
                }
                return FileVisitResult.CONTINUE;
            }
        });

        assertTrue(
                offenders.isEmpty(),
                "Requirement 9.12: dtmf-io-wav is a clean-room RIFF parser and must not "
                        + "import javax.sound.sampled in production code. Offending files: "
                        + offenders);
    }

    /**
     * Assert that the SPI registration file exists and contains exactly one
     * non-empty, non-comment line equal to
     * {@code com.tino1b2be.dtmf.io.wav.WavAudioSourceProvider}
     * (Requirement 9.2).
     *
     * <p>The file is looked up under
     * {@code dtmf-io-wav/src/main/resources/META-INF/services/}; per the
     * {@code ServiceLoader} contract the filename is the fully qualified
     * name of the SPI interface.
     *
     * <p>Blank lines and comment lines (starting with {@code #}) are
     * ignored per the {@code java.util.ServiceLoader} grammar, matching how
     * a runtime loader would interpret the file.
     */
    @Test
    void spiRegistrationFileDeclaresWavProvider() throws IOException {
        Path servicesDir = resolveServicesDir();
        assertTrue(
                Files.isDirectory(servicesDir),
                "Expected dtmf-io-wav META-INF/services directory at " + servicesDir
                        + " but it does not exist or is not a directory");

        Path servicesFile = servicesDir.resolve(SERVICES_FILE_NAME);
        assertTrue(
                Files.isRegularFile(servicesFile),
                "Requirement 9.2: missing SPI registration file " + servicesFile);

        List<String> declaredClasses = new ArrayList<>();
        for (String rawLine : Files.readAllLines(servicesFile, StandardCharsets.UTF_8)) {
            String line = stripComment(rawLine).trim();
            if (!line.isEmpty()) {
                declaredClasses.add(line);
            }
        }

        assertEquals(
                1, declaredClasses.size(),
                "Requirement 9.2: " + SERVICES_FILE_NAME + " must contain exactly one "
                        + "non-empty, non-comment line but found: " + declaredClasses);
        assertEquals(
                EXPECTED_PROVIDER_FQCN, declaredClasses.get(0),
                "Requirement 9.2: the SPI registration file must list exactly "
                        + EXPECTED_PROVIDER_FQCN);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * Resolve {@code dtmf-io-wav/src/main/java} from the current working
     * directory. Gradle's {@code Test} task launches tests with the module
     * directory as {@code user.dir}, so {@code src/main/java} is the primary
     * lookup. For IDE runs that keep the repository root as the working
     * directory, fall back to {@code dtmf-io-wav/src/main/java}.
     */
    private static Path resolveSourceRoot() {
        Path moduleLocal = Paths.get("src", "main", "java").toAbsolutePath().normalize();
        if (Files.isDirectory(moduleLocal)) {
            return moduleLocal;
        }
        return Paths.get("dtmf-io-wav", "src", "main", "java").toAbsolutePath().normalize();
    }

    /**
     * Resolve {@code dtmf-io-wav/src/main/resources/META-INF/services} with
     * the same working-directory tolerance as {@link #resolveSourceRoot()}.
     */
    private static Path resolveServicesDir() {
        Path moduleLocal = Paths.get("src", "main", "resources", "META-INF", "services")
                .toAbsolutePath().normalize();
        if (Files.isDirectory(moduleLocal)) {
            return moduleLocal;
        }
        return Paths.get("dtmf-io-wav", "src", "main", "resources", "META-INF", "services")
                .toAbsolutePath().normalize();
    }

    /**
     * Strip any {@code #} comment from a services-file line, matching the
     * {@link java.util.ServiceLoader} grammar. The {@code #} character and
     * everything after it on the same line are discarded.
     */
    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash < 0 ? line : line.substring(0, hash);
    }
}
