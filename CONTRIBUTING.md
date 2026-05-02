# Contributing to DTMF-Decoder

Thanks for your interest in contributing. This document covers everything you need to get a development environment working and to land changes.

## Prerequisites

DTMF-Decoder v2 targets **Java 17** as both source and target bytecode level for every module. You need a JDK 17 installed locally before you can build, test, or run any of the v2 modules. The Gradle wrapper (`./gradlew`) handles Gradle itself — you do not need to install Gradle manually.

### macOS (Apple Silicon and Intel)

Install OpenJDK 17 via [Homebrew](https://brew.sh):

```bash
brew install openjdk@17
```

`openjdk@17` is keg-only, so Homebrew does not put it on your `PATH` or register it with the system `java` wrapper automatically. Pick one of the following to make it discoverable.

**Option A — system-wide symlink (recommended).** Lets the macOS `java` wrapper find it so `/usr/bin/java -version` reports 17, and `/usr/libexec/java_home -v 17` resolves correctly. Requires `sudo`:

```bash
sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk \
  /Library/Java/JavaVirtualMachines/openjdk-17.jdk
```

On Intel Macs, replace `/opt/homebrew` with `/usr/local` throughout.

**Option B — shell `PATH` only.** No `sudo` needed. Adds the keg-only JDK to your shell's `PATH` so `java` and `javac` in new shells report 17:

```bash
echo 'export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"' >> ~/.zshrc
# Open a new terminal, or: source ~/.zshrc
```

**Option C — `JAVA_HOME` only.** Sufficient for Gradle and most build tools, which read `JAVA_HOME` directly:

```bash
echo 'export JAVA_HOME="/opt/homebrew/opt/openjdk@17"' >> ~/.zshrc
```

### Verify the install

Whichever option you picked, the following commands must both print `17.x`:

```bash
java -version
javac -version
```

Expected output (version numbers may differ in the patch level):

```
openjdk version "17.0.x" ...
javac 17.0.x
```

If `java -version` still reports no runtime after Option A, confirm the symlink target exists (`ls -la /Library/Java/JavaVirtualMachines/openjdk-17.jdk`) and that `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk` is a real directory.

### Linux

Install OpenJDK 17 via your package manager, for example:

```bash
# Debian / Ubuntu
sudo apt-get install openjdk-17-jdk

# Fedora / RHEL
sudo dnf install java-17-openjdk-devel
```

Then verify with `java -version` and `javac -version` as above.

### Windows

Install a JDK 17 distribution (Temurin, Microsoft, or Oracle) from your vendor of choice and make sure the `bin` directory is on `PATH`. Verify with `java -version` and `javac -version` in a new PowerShell or Command Prompt session.

## Building

Once JDK 17 is installed and verified, from the repository root:

```bash
./gradlew build
```

This compiles every module and runs every module's tests. No other local setup is required — dependencies are fetched via the Gradle wrapper from Maven Central.

## Shared test fixtures (MP3)

The `dtmf-io-mp3` module's unit tests reuse the MP3 fixtures committed in `dtmf-core/src/integrationTest/resources/samples/` rather than duplicating the binary blobs under `dtmf-io-mp3`. The mechanism is a `processTestResources` alias in `dtmf-io-mp3/build.gradle.kts` (wired in Task 1.5 of the `dtmf-io` spec, Requirement 15.4) that copies the following three files onto the `dtmf-io-mp3` test classpath under `shared-samples/`:

- `shared-samples/12345678.mp3` — a generated "12345678" DTMF sequence
- `shared-samples/jazz.mp3` — non-DTMF audio, used as a negative anchor
- `shared-samples/stereo.mp3` — stereo MP3 to exercise the 2-channel path

Unit tests load them with, e.g., `getClass().getResourceAsStream("/shared-samples/12345678.mp3")`. Renaming or removing any of the three files in `dtmf-core` will fail the `:dtmf-io-mp3:processTestResources` task with a missing-input error — that is the intended behaviour; the task's inputs are named explicitly so a rename surfaces at the build level rather than silently dropping coverage.
