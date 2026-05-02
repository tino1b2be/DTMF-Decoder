// Root build for the DTMF-Decoder v2 multi-module project.
//
// Per Task 1.6 of the dtmf-v2-foundation spec: the root applies no plugins
// itself. Its sole responsibility is to stamp consistent Maven coordinates
// — group `com.tino1b2be` and version `2.0.0` — onto every subproject
// (Requirements 2.1, 2.2, 2.3).
//
// `allprojects` (rather than `subprojects`) is used deliberately. The root
// itself does not publish artifacts, so applying the group/version to it is
// harmless, and `allprojects` keeps the intent — "every project in this
// build carries these coordinates" — visible in one place. Subproject-level
// build scripts (added in Task 1.7) inherit these values without having to
// repeat them.

allprojects {
    group = "com.tino1b2be"
    version = "2.0.0"
}
