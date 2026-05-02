# DTMF-Decoder v2

A Java 17 library for detecting and generating [DTMF](https://en.wikipedia.org/wiki/Dual-tone_multi-frequency_signaling) (Dual-Tone Multi-Frequency) signalling tones per ITU-T Q.23 and Q.24. Ships a Goertzel-based detection backend with both batch and streaming APIs.

> **Status:** v2 foundation. File I/O (WAV/MP3/OGG), CLI, GUI, microphone capture, Android support, and Maven Central publishing are explicitly out of scope for this spec and planned for follow-on releases. See [Out of scope](#out-of-scope) below.

## Modules

The project is a Gradle multi-module build. Four modules ship as artifacts; a fifth root aggregator coordinates the build.

| Module | Coordinates | Depends on | Purpose |
|---|---|---|---|
| `goertzel` | `com.tino1b2be:goertzel:2.0.0` | JDK 17 only | General-purpose Goertzel filter + filter bank |
| `dtmf-core` | `com.tino1b2be:dtmf-core:2.0.0` | `goertzel` | DTMF detection, generation, streaming |
| `dtmf-benchmarks` | *(not published)* | `dtmf-core`, `goertzel` | JMH benchmarks |
| `dtmf-bom` | `com.tino1b2be:dtmf-bom:2.0.0` | *(BOM only)* | Pins `goertzel` and `dtmf-core` at a coordinated version |

## Prerequisites

- **JDK 17** — the only thing you need to install locally. The Gradle wrapper (`./gradlew`) handles Gradle itself.

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for JDK install instructions on macOS, Linux, and Windows.

## Build

```bash
./gradlew build
```

Runs compilation and every module's tests on a clean checkout. Integration tests (99.5% detection-rate corpus, 60-second silence, noise false-positive) live in a separate source set and run via:

```bash
./gradlew :dtmf-core:integrationTest
```

## Quickstart

### Batch decode

```java
import com.tino1b2be.dtmf.*;
import java.util.List;

double[] samples = /* your normalised PCM in [-1.0, 1.0] */;
DtmfConfig cfg = DtmfConfig.forTelephony();
List<DtmfTone> tones = DtmfDecoder.decode(samples, cfg);
for (DtmfTone t : tones) {
    System.out.printf("%c at %s (%.2f)%n",
        t.key(), t.startTime(), t.confidence());
}
```

Other sample formats work identically: `decode(short[], cfg)`, `decode(float[], cfg)`, `decode(int[], cfg)`, `decodePcm24(int[], cfg)`.

### Push-based streaming detector

```java
DtmfDetector detector = new DtmfDetector(DtmfConfig.forTelephony());
detector.onTone(tone -> System.out.println("Got " + tone.key()));

while (/* more audio */) {
    double[] chunk = /* read next chunk */;
    detector.process(chunk);
}
detector.flush();
```

The callback fires exactly once per confirmed tone, at tone-end, synchronously before the corresponding `process` or `flush` call returns. Chunking does not affect the emitted sequence: feeding a buffer in one call or in any number of chunks produces the same tones with the same cumulative sample indices.

### Pull-based streaming iterator

```java
try (DtmfStream stream = DtmfStream.fromSamples(samples, cfg)) {
    while (stream.hasNext()) {
        DtmfTone t = stream.next();
        // ...
    }
}
```

Or with a custom source:

```java
DtmfStream.SampleSource source = (buffer, offset, length) -> {
    // Read up to `length` samples into buffer[offset .. offset+length).
    // Return the number read, or -1 at end-of-stream.
};
try (DtmfStream stream = DtmfStream.fromSource(source, cfg)) { /* ... */ }
```

### Generating DTMF audio

```java
double[] audio = DtmfGenerator.generate("123A", DtmfConfig.forTelephony());
```

Each character produces a tone of `minimumToneDuration` samples at the ITU-T Q.23 frequency pair for that key, separated by `minimumGapDuration` samples of silence.

## Configuration

`DtmfConfig` has four preset factories covering the common scenarios:

| Factory | Use case | Differences |
|---|---|---|
| `DtmfConfig.defaults()` | Same as `forTelephony` | — |
| `DtmfConfig.forTelephony()` | ITU-T Q.24 telephony | 40 ms tone, 40 ms gap, threshold 0.25, 2 confirmation frames |
| `DtmfConfig.forVoip()` | VoIP (packet-loss concealment) | 3 confirmation frames |
| `DtmfConfig.forNoisyAudio()` | Noisy environments | 50 ms tone, threshold 0.35, 4 confirmation frames |

For anything the presets do not cover — non-standard sample rates (`[4000, 192000]` Hz), custom twist tolerances, window functions, block size — use the advanced builder:

```java
DtmfConfig cfg = DtmfConfig.advanced()
    .sampleRate(16000)
    .minimumToneDuration(Duration.ofMillis(60))
    .channelMode(ChannelMode.STEREO_INDEPENDENT)
    .windowFunction(WindowFunction.HAMMING)
    .forwardTwistDb(3.0)
    .reverseTwistDb(-6.0)
    .confirmationFrames(3)
    .build();
```

## Supported sample rates

The standard factories accept exactly `{8000, 16000, 44100, 48000}` Hz. The advanced builder accepts any integer sample rate in `[4000, 192000]` Hz. The library automatically sizes each analysis block so the Goertzel bin width lands in `[40, 60]` Hz at every supported rate.

## Channel modes

- `MONO` — single-channel input; every tone tagged `channel = 0`
- `STEREO_INDEPENDENT` — interleaved stereo decoded as two independent channels; emissions tagged `channel = 0` (left) or `channel = 1` (right)
- `STEREO_DOWNMIX` — interleaved stereo averaged into one mono stream before detection; every tone tagged `channel = 0`

## Out of scope

The following are explicitly **not** part of v2 foundation:

- **File I/O** — no WAV, MP3, or OGG readers. Callers supply PCM samples as `double[]`, `short[]`, `float[]`, or `int[]`.
- **CLI** — no command-line interface module.
- **GUI** — no Swing, AWT, JavaFX, or applet code.
- **Microphone capture** — no real-time audio input.
- **Android** — no Android-specific code or dependencies.
- **Maven Central publishing** — no signing, no release workflows.
- **v1 compatibility** — the v1 API under `com.tino1b2be.dtmfdecoder` has been removed. There is no binary or source compatibility shim.

These will land in follow-on specs.

## License

[MIT](LICENSE).
