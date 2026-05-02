package com.tino1b2be.dtmf.io;

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
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Fixture-size and no-leaked-encoder smoke tests for {@code dtmf-io}
 * (Task 9.2).
 *
 * <p>Anchors four invariants that the build must preserve as the
 * modules evolve:
 *
 * <ol>
 *   <li><b>Requirement 15.2 — no WAV fixture over 10 KB.</b> Walk
 *       {@code dtmf-io-wav/src/main/resources/} and
 *       {@code dtmf-io-wav/src/test/resources/} and assert that no
 *       {@code .wav} file exceeds the 10 KB bound. WAV fixtures for the
 *       WAV provider's tests are generated in-memory from
 *       {@link com.tino1b2be.dtmf.DtmfGenerator}, so the on-disk count
 *       is expected to be zero in practice; this test catches the
 *       accidental regression of a contributor committing a large binary
 *       sample.</li>
 *
 *   <li><b>Requirement 15.3 — at most five MP3 fixtures, each &le; 200
 *       KB.</b> Walk {@code dtmf-io-mp3/src/main/resources/} and
 *       {@code dtmf-io-mp3/src/test/resources/fixtures/} and assert the
 *       combined {@code .mp3} count is at most five and that no single
 *       file exceeds 200 KB. The shared-sample aliasing arranged by
 *       {@code processTestResources} in {@code dtmf-io-mp3/build.gradle
 *       .kts} keeps the committed count at zero; this test catches the
 *       accidental regression of committing binary blobs directly under
 *       the module.</li>
 *
 *   <li><b>Requirement 2.8 &amp; 18.6 — no legacy v1 class names
 *       reappear.</b> Walk
 *       {@code dtmf-io-wav/src/main/java/} and
 *       {@code dtmf-io-mp3/src/main/java/} and assert that no
 *       {@code .java} file declares a class named {@code AudioFile},
 *       {@code WavFile}, {@code MP3File}, {@code OGGFile}, or
 *       {@code TempAudio}. These are the legacy v1 types whose
 *       resurrection under any package is forbidden by Requirement
 *       18.6.</li>
 *
 *   <li><b>Requirement 18.4 &mdash; no leaked encoder surface.</b> Walk
 *       {@code dtmf-io-wav/src/main/java/} and assert that no
 *       {@code .java} file declares a class whose name ends in
 *       {@code Encoder} or {@code Writer}. Writing WAV is out of scope
 *       for this spec; the only encoder that ships lives in
 *       {@code dtmf-io-wav/src/test/java/} as {@code WavEncoder} (Task
 *       6.7). Any {@code *Encoder} or {@code *Writer} class sneaking
 *       into {@code main/} is a boundary violation.</li>
 * </ol>
 *
 * <h2>Working-directory resolution</h2>
 *
 * <p>Gradle's {@code Test} task launches each module's tests with
 * {@code user.dir} set to that module's project directory by default,
 * so {@code :dtmf-io:test} runs with {@code user.dir =
 * <repo>/dtmf-io}. Sibling modules are therefore reachable as
 * {@code ../dtmf-io-wav/...} and {@code ../dtmf-io-mp3/...}. For IDE
 * runs that leave the working directory at the repository root, the
 * fallback path {@code dtmf-io-wav/...} resolves the same directory.
 * {@link #resolveRepoChild(String...)} tries both layouts and returns
 * the first one that exists, letting the tests run unchanged from
 * either launch context.
 */
class FixtureSmokeTest {

    /** Maximum allowed size of any {@code .wav} file under {@code dtmf-io-wav/}. */
    private static final long MAX_WAV_BYTES = 10L * 1024L;

    /** Maximum allowed size of any {@code .mp3} fixture under {@code dtmf-io-mp3/}. */
    private static final long MAX_MP3_BYTES = 200L * 1024L;

    /** Maximum allowed number of committed {@code .mp3} fixtures (Req 15.3). */
    private static final int MAX_MP3_COUNT = 5;

    /** Legacy v1 class simple names that must not reappear in v2 (Req 2.8, 18.6). */
    private static final Set<String> FORBIDDEN_LEGACY_CLASS_NAMES =
            Set.of("AudioFile", "WavFile", "MP3File", "OGGFile", "TempAudio");

    /**
     * Matches any top-level or nested class/interface/enum/record
     * declaration. Captures the simple name in group 1. The pattern is
     * intentionally lenient about modifiers because we only care about
     * the declared name.
     */
    private static final Pattern TYPE_DECLARATION = Pattern.compile(
            "(?m)^\\s*(?:public\\s+|private\\s+|protected\\s+|static\\s+|final\\s+|abstract\\s+|sealed\\s+|non-sealed\\s+)*"
                    + "(?:class|interface|enum|record)\\s+([A-Za-z_][A-Za-z0-9_]*)\\b");

    /**
     * Asserts Requirement 15.2: no {@code .wav} file larger than 10 KB
     * lives under {@code dtmf-io-wav/src/main/resources/} or
     * {@code dtmf-io-wav/src/test/resources/}. Generated WAV content is
     * meant to be produced at test time from {@link com.tino1b2be.dtmf
     * .DtmfGenerator}; this smoke test catches the accidental
     * regression of checking in a binary fixture that exceeds the
     * bound.
     */
    @Test
    void noWavFixtureExceedsTenKilobytes() throws IOException {
        List<Path> searchRoots = List.of(
                resolveRepoChild("dtmf-io-wav", "src", "main", "resources"),
                resolveRepoChild("dtmf-io-wav", "src", "test", "resources"));

        List<String> offenders = new ArrayList<>();
        for (Path root : searchRoots) {
            if (!Files.isDirectory(root)) {
                // Directory may not exist yet (e.g. no test/resources/
                // created); absence is fine.
                continue;
            }
            forEachFileWithExtension(root, ".wav", file -> {
                long size = Files.size(file);
                if (size > MAX_WAV_BYTES) {
                    offenders.add(file + " (" + size + " bytes > "
                            + MAX_WAV_BYTES + " byte cap)");
                }
            });
        }

        assertTrue(
                offenders.isEmpty(),
                "Requirement 15.2 forbids committing any .wav file larger than "
                        + MAX_WAV_BYTES + " bytes under dtmf-io-wav/; found: "
                        + offenders);
    }

    /**
     * Asserts Requirement 15.3: the combined count of committed
     * {@code .mp3} fixtures under {@code dtmf-io-mp3/src/main/resources/}
     * and {@code dtmf-io-mp3/src/test/resources/fixtures/} is at most
     * five, and every such file is no larger than 200 KB. The preferred
     * layout (Task 7.5 option A) is zero committed fixtures and
     * aliasing from {@code dtmf-core} via {@code processTestResources};
     * this test permits up to five if a contributor chooses option B
     * but holds the 200 KB line per file regardless.
     */
    @Test
    void mp3FixturesAreBoundedInCountAndSize() throws IOException {
        List<Path> searchRoots = List.of(
                resolveRepoChild("dtmf-io-mp3", "src", "main", "resources"),
                resolveRepoChild("dtmf-io-mp3", "src", "test", "resources", "fixtures"));

        List<Path> mp3Files = new ArrayList<>();
        List<String> oversized = new ArrayList<>();
        for (Path root : searchRoots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            forEachFileWithExtension(root, ".mp3", file -> {
                mp3Files.add(file);
                long size = Files.size(file);
                if (size > MAX_MP3_BYTES) {
                    oversized.add(file + " (" + size + " bytes > "
                            + MAX_MP3_BYTES + " byte cap)");
                }
            });
        }

        assertTrue(
                mp3Files.size() <= MAX_MP3_COUNT,
                "Requirement 15.3 caps committed .mp3 fixtures at "
                        + MAX_MP3_COUNT + "; found " + mp3Files.size() + ": " + mp3Files);
        assertTrue(
                oversized.isEmpty(),
                "Requirement 15.3 caps each committed .mp3 fixture at "
                        + MAX_MP3_BYTES + " bytes; over-size files: " + oversized);
    }

    /**
     * Asserts Requirements 2.8 and 18.6: no legacy v1 type
     * ({@code AudioFile}, {@code WavFile}, {@code MP3File},
     * {@code OGGFile}, {@code TempAudio}) is declared anywhere under
     * {@code dtmf-io-wav/src/main/java/} or
     * {@code dtmf-io-mp3/src/main/java/}. The {@code BuildShapeTest}
     * already checks that no class under the legacy
     * {@code com.tino1b2be.audio} package lands on the
     * {@code dtmf-io} classpath; this test complements that by
     * rejecting the forbidden simple names under any package in the
     * new provider modules.
     */
    @Test
    void noLegacyClassNamesInProviderModuleSources() throws IOException {
        List<Path> searchRoots = List.of(
                resolveRepoChild("dtmf-io-wav", "src", "main", "java"),
                resolveRepoChild("dtmf-io-mp3", "src", "main", "java"));

        List<String> offenders = new ArrayList<>();
        for (Path root : searchRoots) {
            assertTrue(
                    Files.isDirectory(root),
                    "Expected " + root + " to be a directory, but it was not; "
                            + "has the project layout changed?");
            forEachFileWithExtension(root, ".java", file -> {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                Matcher m = TYPE_DECLARATION.matcher(content);
                while (m.find()) {
                    String simpleName = m.group(1);
                    if (FORBIDDEN_LEGACY_CLASS_NAMES.contains(simpleName)) {
                        offenders.add(file + " declares forbidden type '"
                                + simpleName + "'");
                    }
                }
            });
        }

        assertTrue(
                offenders.isEmpty(),
                "Requirements 2.8 and 18.6 forbid re-introducing the legacy v1 types "
                        + FORBIDDEN_LEGACY_CLASS_NAMES + " under any package; found: "
                        + offenders);
    }

    /**
     * Asserts Requirement 18.4: no encoder or writer class lives under
     * {@code dtmf-io-wav/src/main/java/}. Writing WAV bytes is out of
     * scope for this spec — the only encoder that ships is the
     * test-only {@code WavEncoder} under
     * {@code dtmf-io-wav/src/test/java/} (Task 6.7). Any class in
     * {@code main/} whose simple name ends in {@code Encoder} or
     * {@code Writer} is flagged as a boundary violation, acting as a
     * lightweight proxy for the harder-to-check "no class writes WAV
     * bytes" rule.
     */
    @Test
    void noEncoderOrWriterClassInWavMainSources() throws IOException {
        Path root = resolveRepoChild("dtmf-io-wav", "src", "main", "java");
        assertTrue(
                Files.isDirectory(root),
                "Expected " + root + " to be a directory, but it was not.");

        List<String> offenders = new ArrayList<>();
        forEachFileWithExtension(root, ".java", file -> {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            Matcher m = TYPE_DECLARATION.matcher(content);
            while (m.find()) {
                String simpleName = m.group(1);
                if (simpleName.endsWith("Encoder") || simpleName.endsWith("Writer")) {
                    offenders.add(file + " declares '" + simpleName
                            + "' (Encoder/Writer surface is forbidden in main/)");
                }
            }
        });

        assertTrue(
                offenders.isEmpty(),
                "Requirement 18.4 forbids WAV encoder/writer classes in "
                        + "dtmf-io-wav/src/main/java/; found: " + offenders);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Resolve a repository-relative path that should work both when
     * {@code user.dir} is the {@code dtmf-io} module directory (Gradle
     * default) and when it is the repository root (IDE default). Tries
     * {@code ../<segments>} first — the Gradle layout — and falls back
     * to {@code <segments>} if that does not exist.
     */
    private static Path resolveRepoChild(String... segments) {
        Path asSibling = Paths.get("..", segments).toAbsolutePath().normalize();
        if (Files.exists(asSibling)) {
            return asSibling;
        }
        Path fromRepoRoot = Paths.get("", segments).toAbsolutePath().normalize();
        return fromRepoRoot;
    }

    /**
     * Functional interface used by {@link #forEachFileWithExtension}
     * so visitors can throw {@link IOException} without being wrapped.
     */
    @FunctionalInterface
    private interface IoConsumer<T> {
        void accept(T t) throws IOException;
    }

    /**
     * Walk {@code root} and invoke {@code visitor} on every regular
     * file whose lowercase name ends with {@code extension} (including
     * the dot). The walk is case-insensitive on the extension so that
     * e.g. {@code .WAV} is treated the same as {@code .wav}.
     */
    private static void forEachFileWithExtension(
            Path root, String extension, IoConsumer<Path> visitor) throws IOException {
        String suffix = extension.toLowerCase(Locale.ROOT);
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (attrs.isRegularFile() && name.endsWith(suffix)) {
                    visitor.accept(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
