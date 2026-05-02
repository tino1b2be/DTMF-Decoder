// `dtmf-benchmarks` — JMH benchmark harness for the v2 foundation
// (Requirements 1.6, 15.1). This module is NOT published (Req 1.6); the
// `PublishToMavenRepository` / `PublishToMavenLocal` tasks are explicitly
// disabled below so that a misconfigured root `publish` invocation can never
// accidentally ship benchmark bytecode to a Maven repository.
//
// Plugins:
//   - `dtmf.java-library-conventions` gives us the Java 17 toolchain,
//     `-Xlint:all -Werror`, JUnit 5, and jqwik for any small unit tests the
//     benchmark sources might need.
//   - `me.champeau.jmh` (0.7.2, pinned via the version catalog in
//     `gradle/libs.versions.toml`) creates the `jmh` source set and the
//     `jmh`, `jmhJar`, etc. tasks.
//
// The `jmh(...)` configuration contributes to the JMH classpath only; it is
// not part of the module's main/api classpath. `commons-math3` is included
// solely for the optional `FftComparisonBenchmark` (Req 10.3) and has no
// bearing on the production Goertzel implementation.

plugins {
    id("dtmf.java-library-conventions")
    alias(libs.plugins.jmh)
}

dependencies {
    "jmh"(project(":dtmf-core"))
    "jmh"(project(":goertzel"))
    // Optional FFT comparator — only used for benchmarking per Req 10.3.
    "jmh"(libs.commons.math3)
}

// Mark module as non-publishable (Req 1.6). Even if a consumer invokes
// `./gradlew publish` at the root, the benchmark jar is never uploaded.
tasks.withType<PublishToMavenRepository>().configureEach { enabled = false }
tasks.withType<PublishToMavenLocal>().configureEach { enabled = false }
