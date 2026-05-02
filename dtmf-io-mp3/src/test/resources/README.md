# `dtmf-io-mp3` test resources

MP3 test fixtures are not checked into this module directly. They are
aliased at build time from
`dtmf-core/src/integrationTest/resources/samples/` via the
`processTestResources` task in `dtmf-io-mp3/build.gradle.kts` (wired in
Task 1.5 of the `dtmf-io` spec, Requirement 15.4).

The following three fixtures from `dtmf-core`'s integration test corpus
land on the `dtmf-io-mp3` test classpath under `shared-samples/`:

- `shared-samples/12345678.mp3` — a generated "12345678" DTMF sequence
- `shared-samples/jazz.mp3` — non-DTMF audio, used as a negative anchor
- `shared-samples/stereo.mp3` — stereo MP3 to exercise the 2-channel path

Unit tests load them with, e.g.,
`getClass().getResourceAsStream("/shared-samples/12345678.mp3")`.

This aliasing avoids duplicating large binary fixtures across modules.
Changes to the source files in `dtmf-core` propagate to the MP3 module's
test classpath on the next build; renaming or removing any of the three
files in `dtmf-core` will fail the `:dtmf-io-mp3:processTestResources`
task with a missing-input error, which is the intended behaviour.
