/**
 * Package-private implementation details for {@code com.tino1b2be.dtmf.io.wav}.
 *
 * <p>Nothing under this package is part of the published {@code dtmf-io-wav}
 * API. Types declared here are package-private by convention (either
 * explicitly or by being non-{@code public}) and are free to change, move,
 * or disappear between any two releases without notice. External callers
 * MUST NOT depend on any class, method, constant, or file in this package.
 *
 * <p>Expected residents of this package include the RIFF chunk record and
 * reader that parse the {@code RIFF}/{@code WAVE} (and {@code RF64}/
 * {@code WAVE}) container structure, the {@code WaveFormat} metadata
 * record populated from a validated {@code fmt } chunk, and the
 * {@code WavSampleReader} that drives {@code WavAudioSource} by pulling
 * frame bytes from the payload and handing them to the shared PCM-to-double
 * sample-conversion helper from {@code com.tino1b2be.dtmf.io.internal}. All
 * of those names are internal detail — the public contract for WAV reading
 * lives on {@code WavAudioSourceProvider} and {@code WavAudioSource} in the
 * parent package.
 *
 * @since 2.0.0
 */
package com.tino1b2be.dtmf.io.wav.internal;
