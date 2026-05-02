package com.tino1b2be.dtmf.io;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Module-structure smoke tests (Task 9.3).
 *
 * <p>Anchors six structural invariants that follow from Requirements 1,
 * 2, and 18 of {@code dtmf-io}. Each invariant is checked by reading a
 * build or source artifact from the repository layout rather than by
 * probing Gradle at runtime; this keeps the test fast (no build-tool
 * coupling) and makes the failure message read like a line-number
 * pointer into the offending file.
 *
 * <ol>
 *   <li><b>Requirement 1.2 — {@code dtmf-io} has exactly one non-test
 *       runtime dependency, {@code :dtmf-core}.</b> Parse
 *       {@code dtmf-io/build.gradle.kts}, collect every
 *       {@code api}/{@code implementation}/{@code runtimeOnly}
 *       declaration (ignoring {@code test*} and {@code integrationTest*}
 *       configurations), and assert the set equals
 *       {@code {":dtmf-core"}}.</li>
 *
 *   <li><b>Requirement 1.3 — {@code dtmf-io-wav} has exactly one
 *       non-test runtime dependency, {@code :dtmf-io}.</b></li>
 *
 *   <li><b>Requirement 1.4 — {@code dtmf-io-mp3} has exactly three
 *       non-test runtime dependencies: {@code :dtmf-io},
 *       {@code javazoom:jlayer:1.0.1}, and
 *       {@code com.googlecode.soundlibs:mp3spi:1.9.5.4}.</b> The last
 *       two are pinned through the version catalog
 *       ({@code gradle/libs.versions.toml}) as {@code libs.jlayer} and
 *       {@code libs.mp3spi}, so the parser resolves the catalog
 *       accessors by cross-referencing
 *       {@code gradle/libs.versions.toml} and compares the resulting
 *       coordinates against the expected triple.</li>
 *
 *   <li><b>Requirements 2.4/2.5/2.6 — every production class in each
 *       module lives under the module's package root.</b> Walk
 *       {@code <module>/src/main/java} for each of the three modules
 *       and assert every {@code .java} file declares a package rooted
 *       at {@code com.tino1b2be.dtmf.io}, {@code com.tino1b2be.dtmf.io
 *       .wav}, or {@code com.tino1b2be.dtmf.io.mp3} respectively.
 *       {@link BuildShapeTest} already covers the {@code dtmf-io}
 *       case; this test adds the two provider modules so all three are
 *       pinned from one place.</li>
 *
 *   <li><b>Requirement 2.7 — {@code dtmf-bom} pins all five
 *       published coordinates.</b> Parse
 *       {@code dtmf-bom/build.gradle.kts} and assert its constraints
 *       block contains {@code api} entries for
 *       {@code com.tino1b2be:goertzel:2.0.0},
 *       {@code com.tino1b2be:dtmf-core:2.0.0},
 *       {@code com.tino1b2be:dtmf-io:2.0.0},
 *       {@code com.tino1b2be:dtmf-io-wav:2.0.0}, and
 *       {@code com.tino1b2be:dtmf-io-mp3:2.0.0}.</li>
 * </ol>
 *
 * <h2>Working-directory resolution</h2>
 *
 * <p>Gradle launches {@code :dtmf-io:test} with {@code user.dir} set to
 * the {@code dtmf-io/} module directory, so sibling modules are
 * reachable as {@code ../dtmf-io-wav}, {@code ../dtmf-io-mp3}, and
 * {@code ../dtmf-bom}. IDE runs that leave {@code user.dir} at the
 * repository root resolve to the same files via
 * {@code dtmf-io-wav/...} directly. {@link #resolveRepoChild} tries
 * both layouts so the test runs unchanged from either launch context.
 */
class ModuleStructureTest {

    // ------------------------------------------------------------------
    // Dependency-parsing regexes
    // ------------------------------------------------------------------

    /**
     * Matches a Gradle Kotlin-DSL dependency declaration of the shape:
     *
     * <pre>{@code
     *   configuration("coordinate")
     *   configuration(project(":name"))
     *   configuration(libs.alias)
     *   configuration(libs.alias.with.dots)
     *   "configurationName"("coordinate")
     *   "configurationName"(project(":name"))
     *   "configurationName"(libs.alias)
     * }</pre>
     *
     * <p>Group 1 captures the configuration name (possibly from inside
     * string quotes). Group 2 captures the raw argument text between
     * the outer parentheses so it can be classified downstream as a
     * {@code project(":...")} reference, a {@code libs.alias} catalog
     * accessor, or a literal {@code "group:name:version"} coordinate.
     *
     * <p>Lines starting with a shell-comment (impossible in Kotlin) or
     * a Kotlin single-line comment ({@code //}) are rejected by the
     * leading {@code [^/\\n]*?} anchor combined with the
     * (?m)-multiline matcher, which ensures each match starts on a
     * fresh line whose first non-whitespace content is a configuration
     * identifier.
     */
    private static final Pattern DEPENDENCY_LINE = Pattern.compile(
            "(?m)^\\s*(?:\"([A-Za-z][A-Za-z0-9_]*)\"|([A-Za-z][A-Za-z0-9_]*))\\s*\\(([^\\n]+?)\\)\\s*$");

    /** Extracts the project path from {@code project(":name")}. */
    private static final Pattern PROJECT_REF = Pattern.compile(
            "project\\(\\s*\"(:[A-Za-z0-9_\\-./]+)\"\\s*\\)");

    /** Extracts the catalog alias from {@code libs.alias} / {@code libs.alias.with.dots}. */
    private static final Pattern LIBS_REF = Pattern.compile(
            "^libs\\.([A-Za-z_][A-Za-z0-9_.]*)$");

    /**
     * Matches a literal Maven coordinate string like
     * {@code "group:name:version"}.
     */
    private static final Pattern COORDINATE_LITERAL = Pattern.compile(
            "^\"([^:\"\\s]+):([^:\"\\s]+):([^:\"\\s]+)\"$");

    /**
     * Catalog library entry: matches {@code alias = { module = "g:n",
     * version.ref = "v" }}. Group 1 is the alias (with dashes, not
     * dots), group 2 is the module {@code group:name}, group 3 is the
     * version-ref key.
     */
    private static final Pattern CATALOG_MODULE_WITH_VERSION_REF = Pattern.compile(
            "(?m)^\\s*([A-Za-z][A-Za-z0-9_\\-]*)\\s*=\\s*\\{\\s*module\\s*=\\s*\"([^\"]+)\"\\s*,\\s*"
                    + "version\\.ref\\s*=\\s*\"([^\"]+)\"\\s*\\}");

    /** Catalog version pin: matches {@code key = "value"} under the {@code [versions]} section. */
    private static final Pattern CATALOG_VERSION_PIN = Pattern.compile(
            "(?m)^\\s*([A-Za-z][A-Za-z0-9_\\-]*)\\s*=\\s*\"([^\"]+)\"");

    /**
     * Matches the {@code constraints { ... api("coord") ... }} block
     * in {@code dtmf-bom/build.gradle.kts}. Group 1 is the block body
     * (the text between the matching braces).
     */
    private static final Pattern CONSTRAINTS_BLOCK = Pattern.compile(
            "constraints\\s*\\{([\\s\\S]*?)\\}", Pattern.DOTALL);

    /** Matches an {@code api("...")} coordinate inside the constraints block. */
    private static final Pattern CONSTRAINT_API_COORD = Pattern.compile(
            "api\\(\\s*\"([^\"]+)\"\\s*\\)");

    /**
     * Matches a Java {@code package} declaration at the top of a source
     * file.
     */
    private static final Pattern PACKAGE_DECLARATION = Pattern.compile(
            "(?m)^\\s*package\\s+([a-zA-Z_][\\w.]*)\\s*;");

    // ------------------------------------------------------------------
    // Expected module coordinates
    // ------------------------------------------------------------------

    private static final String DTMF_CORE = ":dtmf-core";
    private static final String DTMF_IO = ":dtmf-io";
    private static final String JLAYER_COORDINATE = "javazoom:jlayer:1.0.1";
    private static final String MP3SPI_COORDINATE = "com.googlecode.soundlibs:mp3spi:1.9.5.4";

    private static final String GROUP = "com.tino1b2be";
    private static final String VERSION = "2.0.0";

    private static final List<String> EXPECTED_BOM_COORDINATES = List.of(
            GROUP + ":goertzel:" + VERSION,
            GROUP + ":dtmf-core:" + VERSION,
            GROUP + ":dtmf-io:" + VERSION,
            GROUP + ":dtmf-io-wav:" + VERSION,
            GROUP + ":dtmf-io-mp3:" + VERSION);

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    /**
     * Requirement 1.2: {@code dtmf-io} declares exactly one non-test
     * runtime dependency, the {@code :dtmf-core} project. The
     * {@code testImplementation(project(":dtmf-io-wav"))} declaration
     * that feeds {@link ErrorPathsTest} and the
     * {@code integrationTest*} declarations that wire the real
     * providers into the integration-test classpath are excluded by
     * configuration scope.
     */
    @Test
    void dtmfIoHasExactlyOneNonTestRuntimeDependency() throws IOException {
        Set<String> actual = parseRuntimeDependencies(
                resolveRepoChild("dtmf-io", "build.gradle.kts"));
        assertEquals(
                Set.of(DTMF_CORE),
                actual,
                "Requirement 1.2: dtmf-io must declare exactly one non-test runtime "
                        + "dependency (:dtmf-core); found: " + actual);
    }

    /**
     * Requirement 1.3: {@code dtmf-io-wav} declares exactly one
     * non-test runtime dependency, the {@code :dtmf-io} project.
     */
    @Test
    void dtmfIoWavHasExactlyOneNonTestRuntimeDependency() throws IOException {
        Set<String> actual = parseRuntimeDependencies(
                resolveRepoChild("dtmf-io-wav", "build.gradle.kts"));
        assertEquals(
                Set.of(DTMF_IO),
                actual,
                "Requirement 1.3: dtmf-io-wav must declare exactly one non-test runtime "
                        + "dependency (:dtmf-io); found: " + actual);
    }

    /**
     * Requirement 1.4: {@code dtmf-io-mp3} declares exactly three
     * non-test runtime dependencies: {@code :dtmf-io}, and the two
     * external libraries pinned by the version catalog as
     * {@code libs.jlayer} ({@value #JLAYER_COORDINATE}) and
     * {@code libs.mp3spi} ({@value #MP3SPI_COORDINATE}).
     */
    @Test
    void dtmfIoMp3HasExactlyThreeNonTestRuntimeDependencies() throws IOException {
        Set<String> actual = parseRuntimeDependencies(
                resolveRepoChild("dtmf-io-mp3", "build.gradle.kts"));
        assertEquals(
                Set.of(DTMF_IO, JLAYER_COORDINATE, MP3SPI_COORDINATE),
                actual,
                "Requirement 1.4: dtmf-io-mp3 must declare exactly three non-test runtime "
                        + "dependencies (:dtmf-io, " + JLAYER_COORDINATE + ", "
                        + MP3SPI_COORDINATE + "); found: " + actual);
    }

    /**
     * Requirements 2.4, 2.5, 2.6: every production class lives under
     * the module's package root.
     *
     * <p>The corresponding check for {@code dtmf-io} is already
     * performed by {@link BuildShapeTest}; asserting it here too is
     * redundant but keeps the diagnostic close to the other module
     * checks for ease of triage.
     */
    @Test
    void everyProductionClassLivesUnderItsModulePackageRoot() throws IOException {
        assertAll(
                () -> assertAllSourceFilesUnderPackage(
                        resolveRepoChild("dtmf-io", "src", "main", "java"),
                        "com.tino1b2be.dtmf.io",
                        "Requirement 2.4"),
                () -> assertAllSourceFilesUnderPackage(
                        resolveRepoChild("dtmf-io-wav", "src", "main", "java"),
                        "com.tino1b2be.dtmf.io.wav",
                        "Requirement 2.5"),
                () -> assertAllSourceFilesUnderPackage(
                        resolveRepoChild("dtmf-io-mp3", "src", "main", "java"),
                        "com.tino1b2be.dtmf.io.mp3",
                        "Requirement 2.6"));
    }

    /**
     * Requirement 2.7: the {@code dtmf-bom} constraints block pins all
     * five published {@code com.tino1b2be} coordinates at version
     * {@value #VERSION}.
     */
    @Test
    void dtmfBomPinsAllFivePublishedCoordinates() throws IOException {
        Path bomBuild = resolveRepoChild("dtmf-bom", "build.gradle.kts");
        String content = Files.readString(bomBuild, StandardCharsets.UTF_8);
        // Strip comments first — the BOM's file header mentions
        // "`dependencies { constraints { ... } }`" in prose, and a naive
        // match on the raw content would latch onto that commented-out
        // snippet instead of the real block below.
        String stripped = stripComments(content);

        Matcher blockMatcher = CONSTRAINTS_BLOCK.matcher(stripped);
        if (!blockMatcher.find()) {
            fail("dtmf-bom/build.gradle.kts must declare a `constraints { ... }` block; "
                    + "none found.");
        }
        String block = blockMatcher.group(1);

        Set<String> actualCoordinates = new LinkedHashSet<>();
        Matcher coordMatcher = CONSTRAINT_API_COORD.matcher(block);
        while (coordMatcher.find()) {
            actualCoordinates.add(coordMatcher.group(1));
        }

        for (String expected : EXPECTED_BOM_COORDINATES) {
            assertTrue(
                    actualCoordinates.contains(expected),
                    "Requirement 2.7: dtmf-bom constraints must include "
                            + expected + "; found: " + actualCoordinates);
        }
    }

    // ------------------------------------------------------------------
    // Dependency parsing
    // ------------------------------------------------------------------

    /**
     * Parse {@code buildFile} and return the set of non-test runtime
     * dependency coordinates.
     *
     * <p>A "non-test runtime" configuration is one whose name does not
     * start with {@code test} and does not start with
     * {@code integrationTest}, {@code jmh}, or any other test-like
     * prefix. Currently recognized runtime configuration names are
     * {@code api}, {@code implementation}, {@code runtimeOnly}, and
     * {@code compileOnly}. Anything else (including
     * {@code testImplementation}, {@code testRuntimeOnly},
     * {@code integrationTestImplementation},
     * {@code integrationTestRuntimeOnly}) is skipped.
     *
     * <p>Each dependency is resolved to a canonical string:
     * {@code ":project-name"} for a {@code project(":name")}
     * reference, the literal coordinate for a
     * {@code "group:name:version"} string, or the resolved
     * {@code group:name:version} triple for a {@code libs.alias}
     * catalog accessor.
     */
    private static Set<String> parseRuntimeDependencies(Path buildFile) throws IOException {
        assertTrue(
                Files.isRegularFile(buildFile),
                "Expected to read " + buildFile + " but it is not a regular file.");
        String content = Files.readString(buildFile, StandardCharsets.UTF_8);
        String stripped = stripComments(content);

        Set<String> coordinates = new LinkedHashSet<>();
        Matcher m = DEPENDENCY_LINE.matcher(stripped);
        while (m.find()) {
            String configuration = m.group(1) != null ? m.group(1) : m.group(2);
            String argument = m.group(3).trim();
            if (!isRuntimeConfiguration(configuration)) {
                continue;
            }
            coordinates.add(resolveDependency(argument));
        }
        return coordinates;
    }

    /**
     * Runtime configurations are exactly those that feed the published
     * artifact's runtime classpath: {@code api},
     * {@code implementation}, {@code runtimeOnly},
     * {@code compileOnly}. Anything else (test-only, benchmark-only,
     * integration-test-only, platform attributes) is excluded.
     */
    private static boolean isRuntimeConfiguration(String name) {
        return switch (name) {
            case "api", "implementation", "runtimeOnly", "compileOnly" -> true;
            default -> false;
        };
    }

    /**
     * Resolve a dependency argument (the text between the outer
     * parentheses of a Gradle dependency line) to its canonical
     * coordinate string.
     */
    private static String resolveDependency(String argument) throws IOException {
        Matcher projectMatcher = PROJECT_REF.matcher(argument);
        if (projectMatcher.matches()) {
            return projectMatcher.group(1);
        }
        Matcher coordinateMatcher = COORDINATE_LITERAL.matcher(argument);
        if (coordinateMatcher.matches()) {
            return coordinateMatcher.group(1) + ":" + coordinateMatcher.group(2) + ":"
                    + coordinateMatcher.group(3);
        }
        Matcher libsMatcher = LIBS_REF.matcher(argument);
        if (libsMatcher.matches()) {
            String alias = libsMatcher.group(1);
            return resolveCatalogAlias(alias);
        }
        return "<unrecognized-dependency: " + argument + ">";
    }

    /**
     * Resolve a version-catalog alias (as appears in Kotlin DSL —
     * dots for nesting) to its {@code group:name:version} coordinate.
     * The catalog stores aliases with dashes; the Kotlin DSL exposes
     * them with dashes translated to dots, so we reverse the
     * translation before lookup.
     */
    private static String resolveCatalogAlias(String dottedAlias) throws IOException {
        String tomlAlias = dottedAlias.replace('.', '-');
        Path catalog = resolveRepoChild("gradle", "libs.versions.toml");
        assertTrue(
                Files.isRegularFile(catalog),
                "Expected to read " + catalog + " but it is not a regular file.");
        String content = Files.readString(catalog, StandardCharsets.UTF_8);

        // Split the file roughly by section headers; we only need the
        // [versions] section to pin version refs and the [libraries]
        // section to resolve module coordinates.
        String versionsSection = extractTomlSection(content, "versions");
        String librariesSection = extractTomlSection(content, "libraries");

        Matcher moduleMatcher = CATALOG_MODULE_WITH_VERSION_REF.matcher(librariesSection);
        while (moduleMatcher.find()) {
            String alias = moduleMatcher.group(1);
            if (!alias.equals(tomlAlias)) {
                continue;
            }
            String module = moduleMatcher.group(2);
            String versionRef = moduleMatcher.group(3);
            String version = resolveVersionRef(versionsSection, versionRef);
            return module + ":" + version;
        }
        return "<unresolved-catalog-alias: " + dottedAlias + ">";
    }

    /**
     * Extract the body of a TOML section (the text between
     * {@code [sectionName]} and the next {@code [} section header, or
     * end-of-file). Returns an empty string when the section is
     * absent, so downstream regex matching naturally produces no hits.
     */
    private static String extractTomlSection(String tomlContent, String sectionName) {
        Pattern header = Pattern.compile("(?m)^\\[" + Pattern.quote(sectionName) + "\\]\\s*$");
        Matcher headerMatcher = header.matcher(tomlContent);
        if (!headerMatcher.find()) {
            return "";
        }
        int start = headerMatcher.end();
        Pattern nextHeader = Pattern.compile("(?m)^\\[[A-Za-z]");
        Matcher nextHeaderMatcher = nextHeader.matcher(tomlContent);
        if (nextHeaderMatcher.find(start)) {
            return tomlContent.substring(start, nextHeaderMatcher.start());
        }
        return tomlContent.substring(start);
    }

    /** Look up {@code key = "value"} in the {@code [versions]} section body. */
    private static String resolveVersionRef(String versionsSection, String key) {
        Matcher m = CATALOG_VERSION_PIN.matcher(versionsSection);
        while (m.find()) {
            if (m.group(1).equals(key)) {
                return m.group(2);
            }
        }
        return "<unresolved-version-ref: " + key + ">";
    }

    /**
     * Strip Kotlin single-line and block comments from the build file
     * before regex matching. Without this, a commented-out
     * {@code // implementation("foo:bar:1.0")} line would be picked up
     * as a real dependency by {@link #DEPENDENCY_LINE}.
     */
    private static String stripComments(String content) {
        // Strip /* ... */ block comments first (non-greedy across lines).
        String noBlockComments = content.replaceAll(
                "(?s)/\\*.*?\\*/", "");
        // Strip // to end-of-line comments.
        return noBlockComments.replaceAll("(?m)//[^\\n]*", "");
    }

    // ------------------------------------------------------------------
    // Source-package checks
    // ------------------------------------------------------------------

    /**
     * Assert that every {@code .java} file under {@code sourceRoot}
     * declares a package rooted at {@code requiredPrefix} (either
     * exactly the prefix or a sub-package of it). {@code requirementTag}
     * is included in failure messages for traceability back to the
     * spec.
     */
    private static void assertAllSourceFilesUnderPackage(
            Path sourceRoot, String requiredPrefix, String requirementTag) throws IOException {
        assertTrue(
                Files.isDirectory(sourceRoot),
                requirementTag + ": expected " + sourceRoot + " to exist as a source root.");

        List<String> offenders = new ArrayList<>();
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String name = file.getFileName().toString();
                if (!name.toLowerCase(Locale.ROOT).endsWith(".java")) {
                    return FileVisitResult.CONTINUE;
                }
                String content = Files.readString(file, StandardCharsets.UTF_8);
                Matcher m = PACKAGE_DECLARATION.matcher(content);
                if (!m.find()) {
                    offenders.add(sourceRoot.relativize(file) + " (no package declaration)");
                    return FileVisitResult.CONTINUE;
                }
                String pkg = m.group(1);
                if (!pkg.equals(requiredPrefix) && !pkg.startsWith(requiredPrefix + ".")) {
                    offenders.add(sourceRoot.relativize(file) + " (package=" + pkg + ")");
                }
                return FileVisitResult.CONTINUE;
            }
        });

        assertTrue(
                offenders.isEmpty(),
                requirementTag + ": every source file under " + sourceRoot
                        + " must be in a package rooted at '" + requiredPrefix
                        + "'; offenders: " + offenders);
    }

    // ------------------------------------------------------------------
    // Path resolution
    // ------------------------------------------------------------------

    /**
     * Resolve a repository-relative path regardless of whether
     * {@code user.dir} is the {@code dtmf-io} module directory (Gradle
     * default) or the repository root (IDE default). Tries the
     * {@code ../<segments>} layout first; falls back to the
     * {@code <segments>} layout if that does not exist.
     */
    private static Path resolveRepoChild(String... segments) {
        Path asSibling = Paths.get("..", segments).toAbsolutePath().normalize();
        if (Files.exists(asSibling)) {
            return asSibling;
        }
        Path fromRepoRoot = Paths.get("", segments).toAbsolutePath().normalize();
        return fromRepoRoot;
    }

}
