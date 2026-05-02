# DTMF-Decoder — Requirements

This document is the normative behavioural contract for the DTMF-Decoder library. Every requirement is testable and has at least one unit or property test validating it. Where a requirement is backed by a property test, the test file carries a tag comment of the form `// Feature: dtmf-v2-foundation, Property N: <title>` and the property's Javadoc names the requirement it validates.

The reference standards are **[ITU-T Q.23](../Documentation/T-REC-Q.23-198811-I!!PDF-E.pdf)** (tone generation) and **[ITU-T Q.24](../Documentation/T-REC-Q.24-198811-I!!PDF-E.pdf)** (detector behaviour, including standard twist). Copies of both standards sit in [`Documentation/`](../Documentation) so the library can be read and understood offline.

Requirements use [EARS](https://alistairmavin.com/ears/) phrasing — each acceptance criterion is of the form *WHEN / WHILE / IF / WHERE / THE &lt;system&gt; SHALL …*. This keeps conditions unambiguous and makes criteria directly machine-checkable.

## Glossary

- **DTMF**: Dual-Tone Multi-Frequency signalling per ITU-T Q.23/Q.24. Sixteen key symbols (`0`–`9`, `A`–`D`, `*`, `#`) each encoded as the sum of one low-group tone (697, 770, 852, 941 Hz) and one high-group tone (1209, 1336, 1477, 1633 Hz).
- **DTMF_Decoder**: The public batch API that converts an array of PCM samples into a list of detected DTMF tones. Implemented by `com.tino1b2be.dtmf.DtmfDecoder`.
- **DTMF_Detector**: The public push-based streaming API that accepts audio chunks and invokes a callback on each detected tone. Implemented by `com.tino1b2be.dtmf.DtmfDetector`. Not thread-safe; one instance per stream.
- **DTMF_Stream**: The public pull-based streaming API built on top of `DTMF_Detector`, exposing an `Iterator<DtmfTone>` over a source stream.
- **DTMF_Generator**: The public generation API that produces PCM samples for a given DTMF key sequence. Implemented by `com.tino1b2be.dtmf.DtmfGenerator`.
- **DtmfTone**: The immutable record representing one detected or generated tone. Fields: `key` (char), `startSample` (long), `endSample` (long), `sampleRate` (int), `confidence` (double in [0.0, 1.0]), `channel` (int, 0 for mono or left, 1 for right).
- **DtmfConfig**: The public configuration type with six common knobs (sample rate, analysis block size, minimum tone duration, minimum gap duration, detection threshold, channel mode) plus an `advanced()` builder exposing window function, twist tolerances, and confirmation-frame count.
- **GoertzelBank**: The public low-level API in the `goertzel` module that runs a set of Goertzel filters over a signal. Exposed to callers who want to build their own detectors.
- **Goertzel_Filter**: A single Goertzel-algorithm filter targeting one frequency bin.
- **Analysis_Block**: A contiguous window of audio samples over which Goertzel energies are computed. Default length is chosen so bin width is approximately 50 Hz independent of sample rate.
- **Standard_Twist**: Per ITU-T Q.24, the permitted power ratio between high-group and low-group tones: +4 dB forward twist (high group louder than low group), −8 dB reverse twist (low group louder than high group).
- **Tone_End_Event**: The moment at which `DTMF_Detector` emits a `DtmfTone` to its callback, defined as the first `Analysis_Block` that does not confirm the currently active tone.
- **PCM16**, **PCM24**, **PCM32**, **PCM_Float**, **PCM_Double**: Linear pulse-code-modulated sample formats with, respectively, 16-bit signed integer, 24-bit signed integer packed into `int`, 32-bit signed integer, 32-bit IEEE float, and 64-bit IEEE double samples. Normalised range for float/double is `[-1.0, 1.0]`.
- **Supported_Sample_Rate**: One of the exact values 8000, 16000, 44100, or 48000 Hz.
- **BOM**: Bill of Materials; a Maven/Gradle artifact with `pom` packaging that pins versions of a set of coordinated artifacts.

## Requirements

### Requirement 1: Multi-module Gradle Project Layout

**User Story:** As a maintainer, I want a standard Gradle multi-module layout, so that each published artifact has isolated dependencies and tests.

1. The project SHALL be a Gradle multi-module build containing exactly five modules named `goertzel`, `dtmf-core`, `dtmf-benchmarks`, `dtmf-bom`, and the root aggregator.
2. The project SHALL include a committed Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`).
3. The project SHALL target Java 17 as both source and target bytecode level for every module.
4. The `goertzel` module SHALL declare zero runtime dependencies outside the Java standard library.
5. The `dtmf-core` module SHALL declare `goertzel` as its only runtime dependency outside the Java standard library.
6. The `dtmf-benchmarks` module SHALL NOT be published and SHALL be marked as such in its build configuration.
7. The `dtmf-bom` module SHALL be packaged as a Maven BOM (`pom` packaging) listing `goertzel` and `dtmf-core` at version `2.0.0`.
8. The project SHALL place Java sources under each module's `src/main/java` and tests under `src/test/java`.
9. WHEN `./gradlew build` is executed on a clean checkout, the project SHALL compile every module and run every module's tests without requiring any pre-installed dependency outside a JDK 17.

### Requirement 2: Maven Coordinates and Package Layout

**User Story:** As a library consumer, I want stable, conventional coordinates and packages, so that I can declare and import the library predictably.

1. The `goertzel` module SHALL publish under the Maven coordinates `com.tino1b2be:goertzel:2.0.0`.
2. The `dtmf-core` module SHALL publish under the Maven coordinates `com.tino1b2be:dtmf-core:2.0.0`.
3. The `dtmf-bom` module SHALL publish under the Maven coordinates `com.tino1b2be:dtmf-bom:2.0.0`.
4. The `goertzel` module SHALL place all production classes under the Java package root `com.tino1b2be.goertzel`.
5. The `dtmf-core` module SHALL place all production classes under the Java package root `com.tino1b2be.dtmf`.
6. The project SHALL NOT expose any class under the legacy package `com.tino1b2be.dtmfdecoder`.

### Requirement 3: Supported Sample Rates

**User Story:** As a library consumer, I want to know exactly which sample rates are supported, so that I can reject or resample audio before calling the decoder.

1. The DTMF_Decoder SHALL treat 8000, 16000, 44100, and 48000 Hz as Supported_Sample_Rate values.
2. WHEN a caller constructs a `DtmfConfig` with a sample rate equal to a Supported_Sample_Rate, the DTMF_Decoder SHALL accept the configuration.
3. IF a caller constructs a `DtmfConfig` via the standard factory methods with a sample rate that is not a Supported_Sample_Rate, THEN the DTMF_Decoder SHALL throw `IllegalArgumentException` identifying the unsupported rate and listing the Supported_Sample_Rate set.
4. WHERE the advanced configuration API (`DtmfConfig.advanced()`) is used, the DTMF_Decoder SHALL accept any integer sample rate in the closed range [4000, 192000] Hz.
5. The DTMF_Decoder SHALL size each Analysis_Block so that the Goertzel bin width is within the closed range [40, 60] Hz for every Supported_Sample_Rate.
6. IF a caller supplies samples whose count implies a sample rate different from the one declared in `DtmfConfig`, THEN the DTMF_Decoder SHALL process the samples using the declared sample rate without inspecting or inferring the actual rate.

### Requirement 4: Supported Input Sample Formats

**User Story:** As a library consumer, I want to pass audio in whichever PCM format I already have, so that I do not have to write normalisation code before decoding.

1. The DTMF_Decoder SHALL expose a batch entry point accepting `double[]` samples normalised to the range `[-1.0, 1.0]`.
2. The DTMF_Decoder SHALL expose a batch entry point accepting `short[]` samples representing signed PCM16.
3. The DTMF_Decoder SHALL expose a batch entry point accepting `float[]` samples normalised to the range `[-1.0, 1.0]`.
4. The DTMF_Decoder SHALL expose a batch entry point accepting `int[]` samples representing signed PCM32, with a documented helper for callers supplying PCM24 packed into the low 24 bits.
5. WHEN a `short[]` input is received, the DTMF_Decoder SHALL internally convert samples to `double` by dividing by 32768.0 before analysis.
6. WHEN a `float[]` input is received, the DTMF_Decoder SHALL internally convert samples to `double` by widening cast without scaling.
7. WHEN an `int[]` input is received, the DTMF_Decoder SHALL internally convert samples to `double` by dividing by 2147483648.0 before analysis.
8. IF any input sample array is `null`, THEN the DTMF_Decoder SHALL throw `NullPointerException` identifying the parameter name.
9. The DTMF_Detector SHALL expose streaming `process(chunk)` overloads mirroring the batch format set (`double[]`, `short[]`, `float[]`, `int[]`).

### Requirement 5: Batch Decoding API

**User Story:** As a library consumer, I want a single-call decode method that returns every tone in a buffer, so that I can process pre-recorded audio without managing state.

1. The DTMF_Decoder SHALL expose a method with signature equivalent to `List<DtmfTone> decode(double[] samples, DtmfConfig config)`.
2. The DTMF_Decoder SHALL return detected tones in non-decreasing order of `startSample`.
3. The DTMF_Decoder SHALL populate every returned `DtmfTone` with `startSample` and `endSample` indices referring to positions within the input array, with `startSample >= 0`, `endSample > startSample`, and `endSample <= samples.length`.
4. The DTMF_Decoder SHALL populate every returned `DtmfTone` with a `confidence` value in the closed range `[0.0, 1.0]`.
5. The DTMF_Decoder SHALL populate every returned `DtmfTone` with a `sampleRate` equal to the sample rate declared in the supplied `DtmfConfig`.
6. The DTMF_Decoder SHALL populate every returned `DtmfTone` with a `key` value drawn from the set `{'0'..'9', 'A', 'B', 'C', 'D', '*', '#'}`.
7. WHEN the input array is empty, the DTMF_Decoder SHALL return an empty list without throwing.

### Requirement 6: Streaming Push Detection API

**User Story:** As a library consumer processing audio as it arrives, I want a push-based detector that emits tones via a callback, so that I do not have to buffer the whole stream.

1. The DTMF_Detector SHALL expose a method equivalent to `void onTone(Consumer<DtmfTone> callback)` that registers a single callback, replacing any previously registered callback.
2. The DTMF_Detector SHALL expose a method equivalent to `void process(double[] chunk)` that consumes a variable-length chunk of samples.
3. The DTMF_Detector SHALL expose a method equivalent to `void flush()` that forces any tone in progress to be emitted if its duration already satisfies the configured minimum.
4. WHEN a tone ends, the DTMF_Detector SHALL invoke the registered callback exactly once for that tone, at the Tone_End_Event, before returning from the `process` or `flush` call that detected the end.
5. The DTMF_Detector SHALL NOT invoke the registered callback at tone start.
6. IF `process` is called concurrently from more than one thread on the same `DTMF_Detector` instance, THEN the behaviour is undefined and the DTMF_Detector SHALL document that instances are not thread-safe.
7. WHEN `process` is called repeatedly with chunks whose concatenation equals a single buffer `B`, the DTMF_Detector SHALL emit the same sequence of tones (same keys, same order, same `startSample`/`endSample` relative to the cumulative sample count) as a single `process(B)` call on a fresh detector.
8. The DTMF_Detector SHALL populate `startSample` and `endSample` on emitted tones as cumulative sample indices measured from the first sample ever passed to that detector instance.

### Requirement 7: Streaming Pull API

**User Story:** As a library consumer who prefers pull-style iteration, I want an `Iterator<DtmfTone>` over a source stream, so that I can integrate with code that expects iterators.

1. The DTMF_Stream SHALL expose a static factory method that constructs a `DtmfStream` from an audio sample source and a `DtmfConfig`.
2. The DTMF_Stream SHALL implement `Iterator<DtmfTone>`.
3. WHEN `hasNext()` is called, the DTMF_Stream SHALL pull and analyse audio from its source until either a tone is available or the source is exhausted.
4. WHEN the source is exhausted and no tone is pending, the DTMF_Stream SHALL return `false` from `hasNext()`.
5. The DTMF_Stream SHALL produce tones in the same order and with the same field values as DTMF_Detector configured identically and fed the same samples.

### Requirement 8: Tiered Configuration

**User Story:** As a library consumer, I want a small default configuration for common cases and an advanced configuration for specialist tuning, so that I do not have to learn DSP to use the library.

1. The DtmfConfig SHALL expose exactly six common knobs: sample rate, analysis block size, minimum tone duration, minimum gap duration, detection threshold, and channel mode.
2. The DtmfConfig SHALL expose a nested advanced builder accessible via `DtmfConfig.advanced()` that additionally exposes: window function, forward twist tolerance in dB, reverse twist tolerance in dB, and confirmation-frame count.
3. The DtmfConfig SHALL expose a static factory `defaults()` returning a configuration suitable for 8 kHz mono telephony audio with Standard_Twist tolerances.
4. The DtmfConfig SHALL expose a static factory `forTelephony()` returning a configuration with Standard_Twist tolerances and a 40 ms minimum tone duration.
5. The DtmfConfig SHALL expose a static factory `forVoip()` returning a configuration tuned for packet-loss concealment artifacts, with a larger confirmation-frame count than `forTelephony()`.
6. The DtmfConfig SHALL expose a static factory `forNoisyAudio()` returning a configuration with a stricter detection threshold and more confirmation frames than `forTelephony()`.
7. The DtmfConfig SHALL be immutable once constructed.
8. IF a caller attempts to construct a `DtmfConfig` with a minimum tone duration less than 10 ms, THEN the DtmfConfig SHALL throw `IllegalArgumentException`.

### Requirement 9: Twist Detection per ITU-T Q.24

**User Story:** As a telephony engineer, I want the decoder to reject tone pairs that violate standard twist limits, so that signalling artifacts outside Q.24 bounds are not mistaken for valid keys.

1. The DTMF_Decoder SHALL compute twist as `10 * log10(high_group_energy / low_group_energy)` for each candidate tone.
2. WHEN `DtmfConfig.forTelephony()` or `DtmfConfig.defaults()` is used, the DTMF_Decoder SHALL reject candidate tones whose twist exceeds +4 dB (forward twist) or is less than −8 dB (reverse twist).
3. WHERE the advanced API configures custom twist tolerances, the DTMF_Decoder SHALL apply exactly those tolerances and no others.
4. WHEN a candidate tone is rejected for twist, the DTMF_Decoder SHALL NOT emit a `DtmfTone` for that candidate.

### Requirement 10: Goertzel as Default Detection Backend

**User Story:** As a performance-conscious consumer, I want a single well-characterised detection backend, so that library behaviour is predictable and cheap.

1. The DTMF_Decoder SHALL use the `goertzel` module's Goertzel_Filter implementation as its sole detection backend in production code paths.
2. The DTMF_Decoder SHALL NOT expose an FFT-based detection backend in its public API.
3. The `dtmf-benchmarks` module MAY include an FFT-based implementation solely for comparative JMH benchmarks.
4. The `goertzel` module SHALL expose a `GoertzelBank` public API that allows callers outside DTMF use cases to run arbitrary target frequencies over arbitrary sample arrays.

### Requirement 11: DTMF Tone Generation

**User Story:** As a library consumer and test author, I want to generate PCM audio for a DTMF key sequence, so that I can produce test vectors and play back tones.

1. The DTMF_Generator SHALL expose a method equivalent to `double[] generate(String sequence, DtmfConfig config)` that produces normalised samples in `[-1.0, 1.0]`.
2. The DTMF_Generator SHALL produce tones using the frequency pairs defined by ITU-T Q.23 for each key in `{'0'..'9', 'A', 'B', 'C', 'D', '*', '#'}`.
3. IF the input sequence contains a character outside the accepted key set, THEN the DTMF_Generator SHALL throw `IllegalArgumentException` identifying the offending character and its position.
4. The DTMF_Generator SHALL produce each tone at the minimum tone duration configured in the supplied `DtmfConfig`.
5. The DTMF_Generator SHALL produce silence equal to the minimum gap duration configured in the supplied `DtmfConfig` between consecutive tones.
6. WHEN a sequence is passed through `DTMF_Generator.generate` and then through `DTMF_Decoder.decode` using a configuration with matching sample rate and Standard_Twist tolerances, the DTMF_Decoder SHALL return a list of `DtmfTone` whose `key` field, read in order, equals the input sequence (round-trip property).

### Requirement 12: Detection Accuracy Targets

**User Story:** As a telephony engineer, I want measurable accuracy targets, so that 'good enough' is a defined bar rather than a judgement call.

1. The DTMF_Decoder SHALL achieve a correct-detection rate of at least 99.5% over a test corpus of Q.23-compliant generated tones at least 40 ms in duration, at Standard_Twist, across every Supported_Sample_Rate.
2. The DTMF_Decoder SHALL emit zero false-positive tones when decoding a pure silence input of any length up to 60 seconds at every Supported_Sample_Rate.
3. The DTMF_Decoder SHALL emit at most one false-positive tone per hour of white-noise-only input at a signal-to-noise ratio of 15 dB or better, evaluated at 8 kHz.
4. WHEN detecting tones at least 40 ms long generated by `DTMF_Generator`, the DTMF_Decoder SHALL produce `startSample` values within ±1 Analysis_Block length of the true tone start, and `endSample` values within ±1 Analysis_Block length of the true tone end.

### Requirement 13: Mono and Stereo Channel Handling

**User Story:** As a library consumer processing recorded calls, I want to decode mono or stereo audio and either tag tones per channel or downmix first, so that I can choose the behaviour that matches my pipeline.

1. The DtmfConfig SHALL expose a channel mode value with three cases: `MONO`, `STEREO_INDEPENDENT`, and `STEREO_DOWNMIX`.
2. WHEN channel mode is `MONO`, the DTMF_Decoder SHALL treat the input array as a single channel and tag every emitted `DtmfTone` with `channel = 0`.
3. WHEN channel mode is `STEREO_INDEPENDENT`, the DTMF_Decoder SHALL treat the input array as interleaved left/right PCM, decode each channel independently, and tag emitted tones with `channel = 0` (left) or `channel = 1` (right).
4. WHEN channel mode is `STEREO_DOWNMIX`, the DTMF_Decoder SHALL average left and right samples into a single mono stream before detection and tag every emitted `DtmfTone` with `channel = 0`.
5. IF channel mode is `STEREO_INDEPENDENT` or `STEREO_DOWNMIX` and the input sample count is odd, THEN the DTMF_Decoder SHALL throw `IllegalArgumentException` identifying the malformed input.

### Requirement 14: DtmfTone Helpers

**User Story:** As a library consumer reporting timestamps to humans, I want easy conversion from sample indices to wall-clock durations, so that I do not have to do the arithmetic myself.

1. The DtmfTone SHALL expose a method equivalent to `Duration startTime()` returning the start position as a `java.time.Duration` computed from `startSample` and `sampleRate`.
2. The DtmfTone SHALL expose a method equivalent to `Duration endTime()` returning the end position as a `java.time.Duration` computed from `endSample` and `sampleRate`.
3. The DtmfTone SHALL expose a method equivalent to `Duration duration()` returning `endTime().minus(startTime())`.

### Requirement 15: JMH Benchmarks Module

**User Story:** As a maintainer tracking performance over time, I want a JMH benchmark module that exercises Goertzel and optional FFT baselines, so that regressions are detectable.

1. The `dtmf-benchmarks` module SHALL apply the JMH Gradle plugin and compile under Java 17.
2. The `dtmf-benchmarks` module SHALL include at least one benchmark class exercising `DTMF_Decoder.decode` across every Supported_Sample_Rate.
3. The `dtmf-benchmarks` module SHALL include at least one benchmark class exercising raw `GoertzelBank` throughput.
4. The project SHALL NOT enforce a hard performance target in v2 foundation; benchmark results SHALL be published as a baseline for future regression tracking.

### Requirement 16: Error Handling and Validation

**User Story:** As a library consumer, I want invalid inputs to fail fast with clear messages, so that I can debug quickly.

1. IF any public API parameter is `null` where non-null is required, THEN the project SHALL throw `NullPointerException` with a message identifying the parameter name.
2. IF a caller passes a numeric argument outside its documented domain (negative sample rate, negative duration, threshold outside `[0.0, 1.0]`), THEN the project SHALL throw `IllegalArgumentException` with a message identifying the parameter and the expected domain.
3. The project SHALL NOT throw checked exceptions from any public API.
4. The project SHALL NOT retain references to caller-supplied sample arrays after the method that received them returns (except where the API is explicitly streaming and documents otherwise).

## Property-based test coverage

Every requirement with a non-trivial acceptance criterion is backed by a jqwik property test in `dtmf-core/src/test/java/com/tino1b2be/dtmf/`. The full list of 21 correctness properties is in [`docs/design.md`](design.md#property-based-test-coverage). Each property test file tags itself with a comment:

```java
// Feature: dtmf-v2-foundation, Property N: <title>
```

and the Javadoc names the validated requirement explicitly. This closes the loop between the prose requirement, the encoded property, and the runtime check.

## Out of scope

The following are deliberately not part of this library and are tracked for later work:

- File I/O — no WAV, MP3, or OGG readers. Callers supply PCM samples as `double[]`, `short[]`, `float[]`, or `int[]`.
- CLI — no command-line interface module.
- GUI — no Swing, AWT, JavaFX, or applet code.
- Microphone capture — no real-time audio input.
- Android — no Android-specific code or dependencies.
- Maven Central publishing — no signing, no release workflows.
- v1 API compatibility — the v1 API under `com.tino1b2be.dtmfdecoder` has been removed without a shim.
