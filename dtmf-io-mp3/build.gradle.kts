// `dtmf-io-mp3` — the MP3 `AudioSourceProvider` implementation for
// `dtmf-io` (Requirement 1.4, Task 1.5). Its only project runtime
// dependency is `:dtmf-io`, declared here as `api` so that consumers
// pulling in `dtmf-io-mp3` transitively see `AudioSource`,
// `AudioSourceProvider`, `AudioSources`, `DtmfFileDecoder`, and the
// `dtmf-core` surface that `dtmf-io` re-exports.
//
// Exactly two external runtime dependencies live here by design
// (Requirement 1.4): `javazoom:jlayer:1.0.1` provides the MPEG Layer III
// decoder and `com.googlecode.soundlibs:mp3spi:1.9.5.4` bridges JLayer
// into the `javax.sound.sampled` SPI, which the MP3 provider consumes via
// `AudioSystem.getAudioInputStream(...)`. Both are pinned through the
// version catalog (`gradle/libs.versions.toml`) so `libs.jlayer` and
// `libs.mp3spi` are the single source of truth for their coordinates.
// Adding a third external runtime dependency to this module is a
// requirement regression.
//
// The provider is registered with `dtmf-io`'s SPI via
// `META-INF/services/com.tino1b2be.dtmf.io.AudioSourceProvider` and is
// discovered at runtime by `java.util.ServiceLoader`.
//
// JUnit 5 and jqwik test wiring, the Java 17 toolchain (Requirement 1.5),
// `-Xlint:all -Werror`, UTF-8 encoding, sources+javadoc jars, and the bare
// `maven-publish` publication all come from
// `dtmf.published-library-conventions` (layered on top of
// `dtmf.java-library-conventions`). Maven coordinates
// (`com.tino1b2be:dtmf-io-mp3:2.1.0`) are inherited from the root
// `build.gradle.kts` via `allprojects`.

plugins {
    id("dtmf.published-library-conventions")
}

dependencies {
    api(project(":dtmf-io"))

    // Exactly two external runtime dependencies (Requirement 1.4).
    implementation(libs.jlayer)
    implementation(libs.mp3spi)

    // `DtmfGenerator` access for unit-test-time round-trip fixtures
    // (Requirement 1.6): tests generate a known DTMF tone via
    // `dtmf-core`, encode it as MP3 bytes with a test fixture, decode
    // through `Mp3AudioSource`, and assert the detected keys match the
    // generator's input within the detection-rate tolerance.
    testImplementation(project(":dtmf-core"))
}

// -------------------------------------------------------------------------
// Shared MP3 sample aliasing (Task 1.5, Requirement 15.4)
// -------------------------------------------------------------------------
//
// The three MP3 fixtures live in `dtmf-core/src/integrationTest/resources/
// samples/` so `dtmf-core`'s own integration tests can exercise them. To
// avoid duplicating the binary blobs (and the associated review-and-merge
// friction) they are aliased into this module's test classpath under the
// sub-directory `shared-samples/` via `processTestResources`.
//
// Unit tests in `dtmf-io-mp3/src/test/java` therefore load the fixtures
// with e.g. `getResource("/shared-samples/12345678.mp3")`, and no binary
// file ships under this module's own source tree. The three filenames
// (`12345678.mp3`, `jazz.mp3`, `stereo.mp3`) are named explicitly so the
// task's inputs are tracked precisely — a rename or removal of any
// listed file in `dtmf-core` surfaces as a missing-input build failure
// rather than silently dropping coverage.

tasks.named<Copy>("processTestResources") {
    val sharedSamplesDir = rootProject.file(
        "dtmf-core/src/integrationTest/resources/samples"
    )
    from(sharedSamplesDir) {
        include("12345678.mp3", "jazz.mp3", "stereo.mp3")
        into("shared-samples")
    }
}
