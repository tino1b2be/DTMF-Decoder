// `dtmf-bom` — Bill of Materials pinning the v2 module versions for
// downstream consumers (Requirements 1.7, 2.1, 2.2, 2.3). Packaging is
// `pom`, produced by the `java-platform` plugin.
//
// `allowDependencies()` is required because by default `java-platform`
// refuses non-constraint dependencies; without it, declaring even the
// constraints below through the `dependencies { constraints { ... } }` block
// works, but the flag keeps the option open for future `api` additions (e.g.
// to align on a specific SLF4J version) without having to revisit plugin
// configuration.
//
// Group and version are stamped by the root `build.gradle.kts` via
// `allprojects` — this BOM therefore publishes as
// `com.tino1b2be:dtmf-bom:2.0.0` and pins the two shipping libraries at the
// same coordinate. Maven Central publishing is intentionally out of scope
// for the foundation spec (Requirement 16.6); this module only wires the
// publication so `publishToMavenLocal` works for local smoke-testing.

plugins {
    `java-platform`
    `maven-publish`
}

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        api("com.tino1b2be:goertzel:2.0.0")
        api("com.tino1b2be:dtmf-core:2.0.0")
        api("com.tino1b2be:dtmf-io:2.0.0")
        api("com.tino1b2be:dtmf-io-wav:2.0.0")
        api("com.tino1b2be:dtmf-io-mp3:2.0.0")
    }
}

publishing {
    publications {
        create<MavenPublication>("bom") {
            from(components["javaPlatform"])
        }
    }
}
