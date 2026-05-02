package com.tino1b2be.dtmf.io;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Build-shape smoke tests for {@code dtmf-io}.
 *
 * <p>These tests assert the static shape of the {@code dtmf-io} module and
 * its runtime classpath rather than any I/O behavior:
 *
 * <ol>
 *   <li>{@code com.tino1b2be.dtmf.DtmfConfig} from {@code dtmf-core} resolves
 *       via {@link Class#forName(String)}, proving the Gradle
 *       {@code api(project(":dtmf-core"))} wiring in
 *       {@code dtmf-io/build.gradle.kts} puts the core library on
 *       {@code dtmf-io}'s runtime classpath (Requirement 1.2).</li>
 *   <li>No class under the legacy v1 packages {@code com.tino1b2be.audio}
 *       (Requirement 2.8) or {@code com.tino1b2be.dtmfdecoder}
 *       (Requirement 18.6) appears anywhere on {@code dtmf-io}'s test
 *       runtime classpath.</li>
 *   <li>Every Java source file under {@code dtmf-io/src/main/java}
 *       declares a package that starts with {@code com.tino1b2be.dtmf.io}
 *       (Requirement 2.4).</li>
 * </ol>
 *
 * <p>The classpath-scanning checks inspect the {@code java.class.path}
 * system property, which Gradle populates with every project's compiled
 * output directory and every external jar on the
 * {@code testRuntimeClasspath} configuration. The source-walking check
 * reads the repository layout from {@code user.dir} — Gradle runs this
 * test with {@code dtmf-io/} as the working directory, so
 * {@code src/main/java} is always reachable as a relative path.
 */
class BuildShapeTest {

    /** Relative package path for the legacy v1 audio I/O surface. */
    private static final String LEGACY_AUDIO_PACKAGE_PATH = "com/tino1b2be/audio";

    /** Relative package path for the legacy v1 decoder surface. */
    private static final String LEGACY_DTMFDECODER_PACKAGE_PATH = "com/tino1b2be/dtmfdecoder";

    /** Required package prefix for every {@code dtmf-io} production class (Req 2.4). */
    private static final String REQUIRED_PACKAGE_PREFIX = "com.tino1b2be.dtmf.io";

    /**
     * Matches a Java {@code package} declaration at the start of a source file,
     * tolerating leading whitespace, single-line comments, and blank lines between
     * the file header (Javadoc / license block) and the declaration itself.
     */
    private static final Pattern PACKAGE_DECLARATION =
            Pattern.compile("(?m)^\\s*package\\s+([a-zA-Z_][\\w.]*)\\s*;");

    /**
     * Asserts that {@code com.tino1b2be.dtmf.DtmfConfig} resolves at runtime,
     * proving that {@code dtmf-core} is on the {@code dtmf-io} test runtime
     * classpath (Requirement 1.2). If {@code api(project(":dtmf-core"))} ever
     * regressed to {@code implementation} in a way that dropped it off the
     * runtime classpath, or if the coordinate moved without the build
     * following, this test would catch it before any later {@code DtmfDecoder}
     * call failed with {@code NoClassDefFoundError}.
     */
    @Test
    void dtmfCoreIsOnRuntimeClasspath() {
        Class<?> cfg = assertDoesNotThrow(
                () -> Class.forName("com.tino1b2be.dtmf.DtmfConfig"),
                "Expected com.tino1b2be.dtmf.DtmfConfig to resolve from dtmf-io's runtime "
                        + "classpath; is dtmf-core still declared as api(project(\":dtmf-core\"))?");
        assertTrue(
                cfg.getPackage() != null && "com.tino1b2be.dtmf".equals(cfg.getPackage().getName()),
                "DtmfConfig resolved but reports unexpected package: " + cfg.getPackage());
    }

    /**
     * Asserts that no class under the legacy v1 package
     * {@code com.tino1b2be.audio} (Requirement 2.8) appears anywhere on the
     * {@code dtmf-io} test runtime classpath.
     */
    @Test
    void noLegacyAudioPackageOnClasspath() {
        List<String> offenders = scanClasspathFor(LEGACY_AUDIO_PACKAGE_PATH);
        assertTrue(
                offenders.isEmpty(),
                "Found legacy com.tino1b2be.audio classes on the classpath: " + offenders);
    }

    /**
     * Asserts that no class under the legacy v1 package
     * {@code com.tino1b2be.dtmfdecoder} (Requirement 18.6) appears anywhere
     * on the {@code dtmf-io} test runtime classpath.
     */
    @Test
    void noLegacyDtmfDecoderPackageOnClasspath() {
        List<String> offenders = scanClasspathFor(LEGACY_DTMFDECODER_PACKAGE_PATH);
        assertTrue(
                offenders.isEmpty(),
                "Found legacy com.tino1b2be.dtmfdecoder classes on the classpath: " + offenders);
    }

    /**
     * Asserts that every Java source file under
     * {@code dtmf-io/src/main/java} declares a package that starts with
     * {@code com.tino1b2be.dtmf.io} (Requirement 2.4). The test walks the
     * source tree rather than the compiled classpath because (a) the
     * requirement is stated in terms of source layout and (b) source scanning
     * works even when {@code dtmf-io} has no production classes yet (Stage 1
     * of the spec).
     */
    @Test
    void allProductionClassesLiveUnderDtmfIoPackage() throws IOException {
        Path sourceRoot = resolveSourceRoot();
        assertTrue(
                Files.isDirectory(sourceRoot),
                "Expected dtmf-io source root at " + sourceRoot
                        + " but it does not exist or is not a directory");

        List<String> offenders = new ArrayList<>();
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String name = file.getFileName().toString();
                if (!name.endsWith(".java")) {
                    return FileVisitResult.CONTINUE;
                }
                String pkg = readPackageDeclaration(file);
                if (pkg == null) {
                    offenders.add(sourceRoot.relativize(file)
                            + " (no package declaration found)");
                } else if (!pkg.equals(REQUIRED_PACKAGE_PREFIX)
                        && !pkg.startsWith(REQUIRED_PACKAGE_PREFIX + ".")) {
                    offenders.add(sourceRoot.relativize(file) + " (package=" + pkg + ")");
                }
                return FileVisitResult.CONTINUE;
            }
        });

        assertTrue(
                offenders.isEmpty(),
                "Every source file under dtmf-io/src/main/java must declare a package under "
                        + REQUIRED_PACKAGE_PREFIX + ", but these did not: " + offenders);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * Resolve the {@code dtmf-io/src/main/java} directory from the working
     * directory Gradle launches this test in.
     *
     * <p>Gradle's {@code Test} task sets {@code user.dir} to the module's
     * project directory by default, so when this test runs from the
     * {@code :dtmf-io:test} task the source root is simply
     * {@code ./src/main/java}. As a fallback for IDE runs that keep the
     * repository root as the working directory, also try
     * {@code dtmf-io/src/main/java}.
     */
    private static Path resolveSourceRoot() {
        Path moduleLocal = Paths.get("src", "main", "java").toAbsolutePath().normalize();
        if (Files.isDirectory(moduleLocal)) {
            return moduleLocal;
        }
        return Paths.get("dtmf-io", "src", "main", "java").toAbsolutePath().normalize();
    }

    /**
     * Read the first {@code package} declaration out of a Java source file.
     * Returns {@code null} when no declaration is found (e.g., the file only
     * contains a default-package class, which the production modules do not
     * permit).
     */
    private static String readPackageDeclaration(Path file) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        Matcher m = PACKAGE_DECLARATION.matcher(content);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Scan every entry on {@code java.class.path} for any class file whose
     * resource path begins with {@code packagePath + "/"}, where
     * {@code packagePath} uses slash separators (e.g.,
     * {@code "com/tino1b2be/audio"}). Returns a list of offending locations
     * in {@code <classpath-entry>!<relative-path>} form, or an empty list
     * when the classpath is clean.
     */
    private static List<String> scanClasspathFor(String packagePath) {
        List<String> offenders = new ArrayList<>();
        for (String entry : classpathEntries()) {
            offenders.addAll(findClassesIn(entry, packagePath));
        }
        return offenders;
    }

    /** Split the {@code java.class.path} system property into individual entries. */
    private static List<String> classpathEntries() {
        String raw = System.getProperty("java.class.path", "");
        if (raw.isEmpty()) {
            return List.of();
        }
        String[] parts = raw.split(Pattern.quote(File.pathSeparator));
        List<String> out = new ArrayList<>(parts.length);
        for (String p : parts) {
            if (!p.isEmpty()) {
                out.add(p);
            }
        }
        return out;
    }

    private static List<String> findClassesIn(String entry, String packagePath) {
        File file = new File(entry);
        if (!file.exists()) {
            return List.of();
        }
        if (file.isDirectory()) {
            return findClassesInDirectory(file, packagePath);
        }
        String lower = file.getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jar") || lower.endsWith(".zip")) {
            return findClassesInJar(file, packagePath);
        }
        return List.of();
    }

    private static List<String> findClassesInDirectory(File root, String packagePath) {
        List<String> hits = new ArrayList<>();
        Path rootPath = root.toPath();
        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String rel = rootPath.relativize(file).toString()
                            .replace(File.separatorChar, '/');
                    if (rel.startsWith(packagePath + "/") && rel.endsWith(".class")) {
                        hits.add(root + "!" + rel);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // Surface unreadable entries as diagnostics rather than silent passes.
            hits.add(root + " (unreadable: " + e.getMessage() + ")");
        }
        return hits;
    }

    private static List<String> findClassesInJar(File jar, String packagePath) {
        List<String> hits = new ArrayList<>();
        try (JarFile jf = new JarFile(jar)) {
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                String name = e.getName();
                if (name.startsWith(packagePath + "/") && name.endsWith(".class")) {
                    hits.add(jar + "!" + name);
                }
            }
        } catch (IOException e) {
            hits.add(jar + " (unreadable: " + e.getMessage() + ")");
        }
        return hits;
    }
}
