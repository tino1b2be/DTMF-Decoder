// Published-library conventions for subprojects that ship a Maven artifact
// (goertzel, dtmf-core).
//
// Layered on top of `dtmf.java-library-conventions`, this plugin adds a bare
// `maven-publish` publication bound to the `java` component. It deliberately
// omits signing and any repository/publishing-to-Central wiring — per
// Requirement 16.6, Maven Central publishing is out of scope for the v2
// foundation spec.

plugins {
    id("dtmf.java-library-conventions")
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
