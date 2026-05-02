/**
 * WAV {@code AudioSourceProvider} implementation for DTMF-Decoder v2.
 *
 * <p>This package hosts the public surface of the {@code dtmf-io-wav}
 * module: {@code WavAudioSourceProvider}, the content-based WAV provider
 * that the {@code dtmf-io} facade discovers via
 * {@link java.util.ServiceLoader}, and {@code WavAudioSource}, the
 * {@code com.tino1b2be.dtmf.io.AudioSource} implementation it returns from
 * {@code open(...)}. The provider is registered through
 * {@code META-INF/services/com.tino1b2be.dtmf.io.AudioSourceProvider} so
 * simply adding {@code dtmf-io-wav} to a consumer's runtime classpath
 * enables {@code AudioSources.open(wavFile)} against PCM and IEEE float WAV
 * files without any additional wiring.
 *
 * <p>The reader is a clean-room RIFF parser (Requirement 9.12): it does
 * not delegate format decoding to {@code javax.sound.sampled} and it
 * declares zero external runtime dependencies (Requirement 1.3). Supported
 * payloads are PCM integer at 16, 24, or 32 bits and IEEE float at 32 or
 * 64 bits, each at mono or stereo channel counts (Requirements 9.7 through
 * 9.9); {@code WAVEFORMATEXTENSIBLE} headers whose {@code SubFormat} GUID
 * resolves to one of those cases are opened transparently, and compressed
 * codecs (&mu;-law, A-law, ADPCM, etc.) are rejected with an
 * {@code UnsupportedAudioFormatException} identifying the compression code
 * (Requirement 9.10). All production classes in the {@code dtmf-io-wav}
 * module live under this package root (Requirement 2.5); parsing utilities
 * that are not part of the public surface live under
 * {@code com.tino1b2be.dtmf.io.wav.internal}.
 *
 * <p>The concrete types are introduced starting at Stage 6 of the
 * {@code dtmf-io} spec; this {@code package-info.java} is present from
 * Stage 1 so the source tree exists for the build-shape smoke tests in
 * Task 1.8.
 *
 * @since 2.1.0
 */
package com.tino1b2be.dtmf.io.wav;
