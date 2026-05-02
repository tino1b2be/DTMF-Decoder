// `goertzel` — leaf Gradle module that ships the general-purpose Goertzel
// filter and bank (Requirements 1.4, 1.5). It is the only v2 module with no
// runtime dependencies outside the JDK, which is why the dependencies block
// is deliberately absent: test-time JUnit 5 and jqwik wiring comes from the
// `dtmf.java-library-conventions` plugin that is layered in underneath
// `dtmf.published-library-conventions`.
//
// Maven coordinates (`com.tino1b2be:goertzel:2.1.0`) are inherited from the
// root `build.gradle.kts` via `allprojects`. The published-library convention
// attaches a bare `maven-publish` publication; no signing or remote repository
// is configured because Maven Central publishing is out of scope for the
// foundation spec (Requirement 16.6).

plugins {
    id("dtmf.published-library-conventions")
}
