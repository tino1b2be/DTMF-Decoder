// `dtmf-io-wav` — the WAV `AudioSourceProvider` implementation for
// `dtmf-io` (Requirement 1.3, Task 1.4). The only runtime dependency is
// `:dtmf-io`, declared here as `api` so that consumers pulling in
// `dtmf-io-wav` transitively see `AudioSource`, `AudioSourceProvider`,
// `AudioSources`, `DtmfFileDecoder`, and the `dtmf-core` surface that
// `dtmf-io` re-exports.
//
// Zero external runtime dependencies live here by design (Requirement 1.3):
// the WAV reader is clean-room (no `javax.sound.sampled`, no third-party
// RIFF library). The provider is registered with `dtmf-io`'s SPI via
// `META-INF/services/com.tino1b2be.dtmf.io.AudioSourceProvider` and is
// discovered at runtime by `java.util.ServiceLoader`. Adding an external
// codec dependency to this module is a requirement regression.
//
// JUnit 5 and jqwik test wiring, the Java 17 toolchain (Requirement 1.5),
// `-Xlint:all -Werror`, UTF-8 encoding, sources+javadoc jars, and the bare
// `maven-publish` publication all come from
// `dtmf.published-library-conventions` (layered on top of
// `dtmf.java-library-conventions`). Maven coordinates
// (`com.tino1b2be:dtmf-io-wav:2.0.0`) are inherited from the root
// `build.gradle.kts` via `allprojects`.

plugins {
    id("dtmf.published-library-conventions")
}

dependencies {
    api(project(":dtmf-io"))

    // `DtmfGenerator` access for unit-test-time round-trip fixtures
    // (Requirement 1.6): tests generate a known DTMF tone via
    // `dtmf-core`, encode it as WAV bytes with a test-only helper,
    // decode through `WavAudioSource`, and assert bit-exact recovery.
    testImplementation(project(":dtmf-core"))
}
