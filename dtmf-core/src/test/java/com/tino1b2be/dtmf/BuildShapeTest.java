package com.tino1b2be.dtmf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Test;

/**
 * Build-shape smoke tests for {@code dtmf-core}.
 *
 * <p>These tests assert the runtime shape of the {@code dtmf-core} test
 * classpath rather than any detection/generation behavior:
 *
 * <ol>
 *   <li>the {@code goertzel} module is on the runtime classpath, so the
 *       Gradle {@code api(project(":goertzel"))} wiring in
 *       {@code dtmf-core/build.gradle.kts} is effective (Requirement 2.6,
 *       Task 1.9);</li>
 *   <li>no class lives under the legacy v1 package
 *       {@code com.tino1b2be.dtmfdecoder}, so the v2 rewrite has not
 *       accidentally re-introduced or carried over the legacy surface
 *       (Requirement 2.6, Task 1.9).</li>
 * </ol>
 *
 * <p>Both checks inspect the test JVM's {@code java.class.path} system
 * property, which Gradle populates with every module's compiled-output
 * directory (and every external dependency's jar) for the
 * {@code testRuntimeClasspath} configuration. Using the classpath directly
 * keeps these smoke tests useful even at Stage 1 of the foundation build,
 * when neither {@code goertzel} nor {@code dtmf-core} has any compiled
 * classes yet: what we want to prove is "the build wires the right things
 * together", not "any particular symbol resolves".
 */
class BuildShapeTest {

    /** The legacy v1 package root that must not appear anywhere on the classpath. */
    private static final String LEGACY_PACKAGE_PATH = "com/tino1b2be/dtmfdecoder";

    /**
     * Asserts that the {@code goertzel} module's compiled output is on the
     * {@code dtmf-core} test runtime classpath.
     *
     * <p>{@code dtmf-core}'s build declares {@code api(project(":goertzel"))}
     * (Requirement 1.5); Gradle turns that into a classpath entry pointing at
     * {@code goertzel/build/classes/java/main} (or the module's jar, once
     * {@code :goertzel:jar} produces one). A missing entry here would mean
     * the module wiring is broken, and any later use of {@code GoertzelBank}
     * from {@code dtmf-core} production code would fail with
     * {@code NoClassDefFoundError}.
     */
    @Test
    void goertzelModuleIsOnRuntimeClasspath() {
        List<String> classpathEntries = classpathEntries();
        boolean present = classpathEntries.stream().anyMatch(BuildShapeTest::isGoertzelEntry);
        assertTrue(
                present,
                "Expected the goertzel module output on the dtmf-core runtime classpath, "
                        + "but found none in: " + classpathEntries);
    }

    /**
     * Asserts that no class lives under the legacy v1 package
     * {@code com.tino1b2be.dtmfdecoder} anywhere on the {@code dtmf-core}
     * test runtime classpath (Requirement 2.6).
     *
     * <p>Scans every classpath entry — compiled-output directories from the
     * new v2 modules and every external dependency jar — for any file whose
     * path starts with {@code com/tino1b2be/dtmfdecoder/} and ends with
     * {@code .class}. The presence of any such file would indicate the v1
     * legacy surface has been re-introduced into the new build.
     */
    @Test
    void noLegacyDtmfDecoderPackageOnClasspath() {
        List<String> offenders = new ArrayList<>();
        for (String entry : classpathEntries()) {
            offenders.addAll(findLegacyClassesIn(entry));
        }
        assertTrue(
                offenders.isEmpty(),
                "Found legacy com.tino1b2be.dtmfdecoder classes on the classpath: " + offenders);
    }

    /** Split the {@code java.class.path} system property into individual entries. */
    private static List<String> classpathEntries() {
        String raw = System.getProperty("java.class.path", "");
        if (raw.isEmpty()) {
            return List.of();
        }
        String[] parts = raw.split(java.util.regex.Pattern.quote(File.pathSeparator));
        List<String> out = new ArrayList<>(parts.length);
        for (String p : parts) {
            if (!p.isEmpty()) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * Returns {@code true} when {@code entry} points at the {@code goertzel}
     * module's compiled output (either a {@code goertzel/build/classes/...}
     * directory or a {@code goertzel-<version>.jar}).
     */
    private static boolean isGoertzelEntry(String entry) {
        String normalized = entry.replace(File.separatorChar, '/');
        if (normalized.endsWith(".jar")) {
            String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
            return fileName.startsWith("goertzel-") || fileName.equals("goertzel.jar");
        }
        return normalized.contains("/goertzel/build/classes/")
                || normalized.endsWith("/goertzel/build/classes")
                || normalized.contains("/goertzel/build/resources/");
    }

    /**
     * Return the fully-qualified class-file paths under
     * {@value #LEGACY_PACKAGE_PATH} found in the given classpath {@code entry}.
     * An empty list means the entry is clean.
     */
    private static List<String> findLegacyClassesIn(String entry) {
        File file = new File(entry);
        if (!file.exists()) {
            return List.of();
        }
        if (file.isDirectory()) {
            return findLegacyClassesInDirectory(file);
        }
        String lower = file.getName().toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".jar") || lower.endsWith(".zip")) {
            return findLegacyClassesInJar(file);
        }
        return List.of();
    }

    private static List<String> findLegacyClassesInDirectory(File root) {
        List<String> hits = new ArrayList<>();
        Path rootPath = root.toPath();
        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String rel = rootPath.relativize(file).toString()
                            .replace(File.separatorChar, '/');
                    if (rel.startsWith(LEGACY_PACKAGE_PATH + "/") && rel.endsWith(".class")) {
                        hits.add(root + "!" + rel);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // Unreadable directory entries on the classpath should not break the smoke
            // test; surface them as a diagnostic rather than a false positive.
            hits.add(root + " (unreadable: " + e.getMessage() + ")");
        }
        return hits;
    }

    private static List<String> findLegacyClassesInJar(File jar) {
        List<String> hits = new ArrayList<>();
        try (JarFile jf = new JarFile(jar)) {
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                String name = e.getName();
                if (name.startsWith(LEGACY_PACKAGE_PATH + "/") && name.endsWith(".class")) {
                    hits.add(jar + "!" + name);
                }
            }
        } catch (IOException e) {
            hits.add(jar + " (unreadable: " + e.getMessage() + ")");
        }
        return hits;
    }
}
