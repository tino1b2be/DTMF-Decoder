/**
 * MP3 {@code AudioSourceProvider} implementation for DTMF-Decoder v2.
 *
 * <p>This package hosts the public surface of the {@code dtmf-io-mp3}
 * module: {@code Mp3AudioSourceProvider}, the content-based MP3 provider
 * that the {@code dtmf-io} facade discovers via
 * {@link java.util.ServiceLoader}, and {@code Mp3AudioSource}, the
 * {@code com.tino1b2be.dtmf.io.AudioSource} implementation it returns from
 * {@code open(...)}. The provider is registered through
 * {@code META-INF/services/com.tino1b2be.dtmf.io.AudioSourceProvider} so
 * simply adding {@code dtmf-io-mp3} to a consumer's runtime classpath
 * enables {@code AudioSources.open(mp3File)} against MPEG-1 and MPEG-2
 * Layer III content without any additional wiring.
 *
 * <p>Decoding is delegated to exactly two external libraries
 * (Requirement 1.4): {@code javazoom:jlayer:1.0.1} for the MPEG Layer III
 * decoder proper, and {@code com.googlecode.soundlibs:mp3spi:1.9.5.4} for
 * the bridge into the {@code javax.sound.sampled} SPI that
 * {@code Mp3AudioSource} consumes via
 * {@link javax.sound.sampled.AudioSystem#getAudioInputStream(java.io.InputStream)}.
 * Supported payloads are MPEG-1 and MPEG-2 Layer III at mono or stereo
 * channel counts, across both constant-bitrate and variable-bitrate streams
 * (Requirements 10.7 and 10.12); Layer I and Layer II payloads and
 * structurally malformed MPEG data are rejected with an
 * {@code UnsupportedAudioFormatException} identifying the defect or layer
 * (Requirement 10.8). Because the underlying decode is forward-only, MP3
 * sources report {@code canSeek() == false} and {@code bitDepth() == 16}
 * (Requirements 10.9 and 10.11). All production classes in the
 * {@code dtmf-io-mp3} module live under this package root
 * (Requirement 2.6); parsing utilities that are not part of the public
 * surface live under {@code com.tino1b2be.dtmf.io.mp3.internal}.
 *
 * <p>The concrete types are introduced starting at Stage 7 of the
 * {@code dtmf-io} spec; this {@code package-info.java} is present from
 * Stage 1 so the source tree exists for the build-shape smoke tests in
 * Task 1.8.
 *
 * @since 2.0.0
 */
package com.tino1b2be.dtmf.io.mp3;
