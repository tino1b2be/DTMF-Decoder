# Security Policy

## Reporting a vulnerability

If you believe you have found a security vulnerability in DTMF-Decoder, please report it via [GitHub's private vulnerability reporting](https://github.com/tino1b2be/DTMF-Decoder/security/advisories/new).

Please do **not** open a public issue for a suspected vulnerability.

When reporting, please include as much of the following as you can:

- The affected module and version (`goertzel`, `dtmf-core`, etc.)
- A description of the vulnerability and its potential impact
- Steps to reproduce, ideally including a minimal code sample or audio buffer that triggers the issue
- Whether you are aware of public discussions or exploits

## Scope

The library processes audio samples supplied by the caller. Inputs are treated as potentially adversarial — for example, a caller might pass:

- Arrays containing `NaN`, `±Infinity`, or values outside `[-1.0, 1.0]`
- Arrays of zero or unusual length (including odd-length stereo inputs)
- Sample rates near the edges of the supported domain

The library's input validation (see [`docs/requirements.md`](docs/requirements.md), Requirement 16) is designed to fail fast on invalid inputs rather than produce wrong results. If you find an input class that causes:

- An undeclared unchecked exception
- Silent data corruption (wrong tones emitted without any indication of failure)
- Excessive memory or CPU consumption disproportionate to the input size
- Any other behaviour that could be weaponised in a larger system

…please report it.

## Supported versions

The `2.x` line is actively maintained. Older versions are not supported.

## Response timeline

There is no guaranteed response time. Reports are reviewed on a best-effort basis and this project is maintained outside of any paid-support arrangement.
