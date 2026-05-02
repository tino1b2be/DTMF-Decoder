// `dtmf-core` — the DTMF detection, generation, and streaming API
// (Requirement 1.5). The only runtime dependency is `:goertzel`, declared
// here as `api` so that consumers pulling in `dtmf-core` transitively see the
// `GoertzelFilter` / `GoertzelBank` types they receive back from the public
// surface where relevant.
//
// JUnit 5 and jqwik test wiring, the Java 17 toolchain, `-Xlint:all -Werror`,
// and the bare `maven-publish` publication all come from
// `dtmf.published-library-conventions` (layered on top of
// `dtmf.java-library-conventions`). Maven coordinates
// (`com.tino1b2be:dtmf-core:2.1.0`) are inherited from the root
// `build.gradle.kts` via `allprojects`.

plugins {
    id("dtmf.published-library-conventions")
}

dependencies {
    api(project(":goertzel"))
}

// -------------------------------------------------------------------------
// Integration-test source set (Task 13.1, Requirements 12.1, 12.2, 12.3)
// -------------------------------------------------------------------------
//
// Statistical and long-duration tests live in `src/integrationTest/java` so
// the default `test` task stays fast. The `integrationTest` task runs them
// explicitly and is wired into `check`, so `./gradlew :dtmf-core:check`
// runs both unit and integration tests while `:dtmf-core:test` stays quick.
//
// Classpath shape: integration tests can see both `main` and `test` outputs
// (they often lean on the same helpers as unit tests) and inherit every
// dependency from the `test` configurations — JUnit 5 + jqwik — via the
// `extendsFrom` wiring below.

sourceSets {
    create("integrationTest") {
        // java.srcDir and resources.srcDir are implicit for a source set named
        // `integrationTest` — they default to src/integrationTest/java and
        // src/integrationTest/resources respectively. Re-adding them
        // explicitly was a defensive mistake that registered each directory
        // twice and broke processIntegrationTestResources once files
        // actually landed under resources/.
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

tasks.register<Test>("integrationTest") {
    description = "Runs integration-scale tests (slow; excluded from :test)."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform {
        includeEngines("junit-jupiter", "jqwik")
    }
    shouldRunAfter("test")
    // Long integration buffers (up to 60 s of audio at 48 kHz) need a bit
    // more heap than the default test worker.
    maxHeapSize = "1g"
}

tasks.named("check") { dependsOn("integrationTest") }
