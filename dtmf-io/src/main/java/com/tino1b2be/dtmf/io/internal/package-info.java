/**
 * Package-private implementation details for {@code com.tino1b2be.dtmf.io}.
 *
 * <p>Nothing under this package is part of the published {@code dtmf-io} API.
 * Types declared here are package-private by convention (either explicitly or
 * by being non-{@code public}) and are free to change, move, or disappear
 * between any two releases without notice. External callers MUST NOT depend
 * on any class, method, constant, or file in this package.
 *
 * <p>Expected residents of this package include the shared PCM-to-double
 * sample-conversion helper used by {@code RawPcmAudioSource},
 * {@code WavAudioSource}, and {@code Mp3AudioSource} (sample normalization
 * per Requirements 3.6, 7.12, and 9.14), the {@code ProviderScore} record
 * used by {@code AudioSources} to track SPI Priority Score bookkeeping
 * during provider dispatch (Requirement 5.6), and a stereo-to-mono
 * downmixer used by {@code DtmfFileDecoder} when the caller asks for
 * {@code MONO} against a two-channel source (Requirement 8.7). All of
 * those names are internal detail — the public contract for each
 * behaviour lives on the public types in the parent package.
 *
 * @since 2.1.0
 */
package com.tino1b2be.dtmf.io.internal;
