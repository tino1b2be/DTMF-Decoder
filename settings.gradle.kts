// Root settings for the DTMF-Decoder v2 multi-module build.
//
// Layout (dtmf-v2-foundation Requirement 1.1 delivered the first four
// subprojects; dtmf-io Requirement 1.1 adds the three `dtmf-io*` modules
// for a total of seven subprojects under the root aggregator):
//   dtmf-v2 (this root)
//   ├── goertzel           — general-purpose Goertzel filter library
//   ├── dtmf-core          — DTMF detection and generation, depends on :goertzel
//   ├── dtmf-benchmarks    — JMH benchmarks, not published
//   ├── dtmf-bom           — BOM pinning coordinated artifact versions
//   ├── dtmf-io            — pull-based AudioSource SPI + DtmfFileDecoder glue
//   ├── dtmf-io-wav        — WAV AudioSourceProvider (clean-room RIFF parser)
//   └── dtmf-io-mp3        — MP3 AudioSourceProvider (jlayer + mp3spi)
//
// Legacy v1 flat `build.gradle` still lives at the repo root during the
// migration; it is removed in Stage 14 of the foundation spec.

rootProject.name = "dtmf-v2"

include("goertzel")
include("dtmf-core")
include("dtmf-benchmarks")
include("dtmf-bom")
include("dtmf-io")
include("dtmf-io-wav")
include("dtmf-io-mp3")

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
