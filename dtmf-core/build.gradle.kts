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
// (`com.tino1b2be:dtmf-core:2.0.0`) are inherited from the root
// `build.gradle.kts` via `allprojects`.

plugins {
    id("dtmf.published-library-conventions")
}

dependencies {
    api(project(":goertzel"))
}
