// Root settings for the DTMF-Decoder v2 multi-module build.
//
// Layout (Requirement 1.1 — "five modules": root aggregator + four subprojects):
//   dtmf-v2 (this root)
//   ├── goertzel           — general-purpose Goertzel filter library
//   ├── dtmf-core          — DTMF detection and generation, depends on :goertzel
//   ├── dtmf-benchmarks    — JMH benchmarks, not published
//   └── dtmf-bom           — BOM pinning coordinated artifact versions
//
// Legacy v1 flat `build.gradle` still lives at the repo root during the
// migration; it is removed in Stage 14 of the foundation spec.

rootProject.name = "dtmf-v2"

include("goertzel")
include("dtmf-core")
include("dtmf-benchmarks")
include("dtmf-bom")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    // The version catalog is auto-loaded from `gradle/libs.versions.toml`
    // once that file is created in Task 1.4. Explicitly invoking
    // `versionCatalogs { create("libs") { ... } }` here would fail
    // until the TOML file exists, so we rely on Gradle's built-in
    // auto-detection of the `gradle/libs.versions.toml` convention.
}
