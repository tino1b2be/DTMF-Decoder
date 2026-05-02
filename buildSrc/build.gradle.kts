// buildSrc — precompiled script plugins for the DTMF-Decoder v2 foundation.
//
// Applies `kotlin-dsl` so the two convention plugins under
// `src/main/kotlin/dtmf.*.gradle.kts` are compiled and exposed as regular
// plugin ids (`dtmf.java-library-conventions`,
// `dtmf.published-library-conventions`) to every subproject.
//
// The version coordinates used inside the convention plugins are hard-coded
// to match `gradle/libs.versions.toml`. The libs catalog cannot be accessed
// directly from precompiled Kotlin script plugins in Gradle 8.10.2 without a
// passthrough trick, and the pinned coordinates are a deliberately short list,
// so we keep the convention plugins self-contained. If either file drifts the
// build-shape tests in Task 1.9 will catch it.

plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}
