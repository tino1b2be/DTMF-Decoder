// `dtmf-io` — the format-agnostic file-I/O layer for DTMF decoding
// (Requirement 1.2, Task 1.3). The only runtime dependency is `:dtmf-core`,
// declared here as `api` so that consumers pulling in `dtmf-io` transitively
// see `DtmfConfig`, `DtmfTone`, `DtmfDecoder`, and the rest of the core
// surface they need to invoke `DtmfFileDecoder`.
//
// Zero external runtime dependencies live here by design (Requirement 1.2):
// WAV and MP3 support ships in the sibling `dtmf-io-wav` and `dtmf-io-mp3`
// modules, discovered at runtime via `java.util.ServiceLoader`. Adding an
// external codec dependency to this module is a requirement regression.
//
// JUnit 5 and jqwik test wiring, the Java 17 toolchain (Requirement 1.5),
// `-Xlint:all -Werror`, UTF-8 encoding, sources+javadoc jars, and the bare
// `maven-publish` publication all come from
// `dtmf.published-library-conventions` (layered on top of
// `dtmf.java-library-conventions`). Maven coordinates
// (`com.tino1b2be:dtmf-io:2.0.0`) are inherited from the root
// `build.gradle.kts` via `allprojects`.

plugins {
    id("dtmf.published-library-conventions")
}

dependencies {
    api(project(":dtmf-core"))

    // -------------------------------------------------------------------
    // Test-only access to the real WAV provider (Task 9.1)
    // -------------------------------------------------------------------
    //
    // `ErrorPathsTest` anchors Requirement 12's error-type invariants
    // (`UnsupportedAudioFormatException` vs bare `IOException`) by
    // feeding hand-built byte fixtures — a μ-law WAV and a WAV with no
    // `data` chunk — through the actual `WavAudioSourceProvider`. The
    // other unit tests in this module deliberately avoid the real
    // providers (they inject fake scoring doubles via
    // `AudioSources.openForTesting(..., providers)`); those tests stay
    // unaffected because they never touch `ServiceLoader`. The test
    // creates `WavAudioSourceProvider` directly via `new` and passes it
    // through the `openForTesting(...)` seam, so the static
    // `AudioSources` provider cache is never populated with WAV during
    // the unit test run.
    //
    // This is NOT a runtime cycle: `dtmf-io-wav` only depends on
    // `dtmf-io`'s main output; `dtmf-io`'s *test* classpath depending
    // on `dtmf-io-wav` is a well-formed DAG at the source-set level.
    testImplementation(project(":dtmf-io-wav"))
}

// -------------------------------------------------------------------------
// Integration-test source set (Task 1.3, Requirements 1.6, 16.1, 16.2)
// -------------------------------------------------------------------------
//
// Integration tests for `dtmf-io` exercise the real `ServiceLoader` dispatch
// path with the WAV and MP3 providers on the runtime classpath. They live
// in `src/integrationTest/java` so the default `test` task stays fast and
// provider-free (unit tests inject scoring doubles instead).
//
// Classpath shape mirrors `dtmf-core`'s integration-test wiring: the
// `integrationTest` compileClasspath sees both `main` and `test` outputs so
// IT helpers can reuse unit-test utilities, and every dependency declared
// on `testImplementation` / `testRuntimeOnly` is inherited via
// `extendsFrom` below — JUnit 5 + jqwik come along automatically.
//
// The two `integrationTestRuntimeOnly` dependencies are the whole point of
// this wiring: they put `:dtmf-io-wav` and `:dtmf-io-mp3` on the IT runtime
// classpath so `ServiceLoader.load(AudioSourceProvider.class, …)` inside
// `AudioSources` discovers both real providers at IT time (Req 16.1, 16.2).
// `integrationTestImplementation(project(":dtmf-core"))` pulls in
// `DtmfGenerator` for building round-trip audio fixtures at test time.

sourceSets {
    create("integrationTest") {
        // java.srcDir and resources.srcDir default to src/integrationTest/java
        // and src/integrationTest/resources for a source set named
        // `integrationTest`; do not re-add them here or Gradle registers
        // each directory twice and breaks processIntegrationTestResources
        // once resource files land under it (learned the hard way in
        // `dtmf-core`'s build).
        compileClasspath += sourceSets["main"].output + sourceSets["test"].output
        runtimeClasspath += output + compileClasspath
    }
}

val integrationTestImplementation by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}
val integrationTestRuntimeOnly by configurations.getting {
    extendsFrom(configurations.testRuntimeOnly.get())
}

dependencies {
    // DtmfGenerator access for IT-time round-trip fixture generation.
    "integrationTestImplementation"(project(":dtmf-core"))

    // Both real providers on the IT runtime classpath so
    // `ServiceLoader` discovers them during `AudioSources.open(...)`
    // dispatch (Requirements 16.1, 16.2).
    "integrationTestRuntimeOnly"(project(":dtmf-io-wav"))
    "integrationTestRuntimeOnly"(project(":dtmf-io-mp3"))
}

tasks.register<Test>("integrationTest") {
    description = "Runs dtmf-io integration tests with real WAV/MP3 providers on the classpath."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform {
        includeEngines("junit-jupiter", "jqwik")
    }
    shouldRunAfter("test")
    // Integration tests may decode minute-long MP3 fixtures and run
    // ServiceLoader against several providers at once; match the heap
    // setting used by `dtmf-core`'s integration task.
    maxHeapSize = "1g"
}

tasks.named("check") { dependsOn("integrationTest") }
