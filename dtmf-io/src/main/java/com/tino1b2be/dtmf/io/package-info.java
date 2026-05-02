/**
 * Format-agnostic audio I/O surface for DTMF-Decoder v2.
 *
 * <p>This package hosts the public surface of the {@code dtmf-io} module:
 * the unified pull-based read interface {@code AudioSource}, the SPI contract
 * {@code AudioSourceProvider} that format modules implement and register via
 * {@code META-INF/services}, the {@code AudioSources} facade that discovers
 * providers through {@link java.util.ServiceLoader} and dispatches to the
 * best match by content-based detection, the {@code DtmfFileDecoder} glue
 * into {@code com.tino1b2be.dtmf.DtmfDecoder}, the {@code RawPcmAudioSource}
 * wrapper for callers that already have PCM bytes in memory, the
 * {@code PcmEncoding} discriminator enum, and the
 * {@code UnsupportedAudioFormatException} subtype of
 * {@link java.io.IOException} that distinguishes "format not recognized"
 * from real I/O failures. Implementation details live under
 * {@code com.tino1b2be.dtmf.io.internal} and are not part of the published
 * API.
 *
 * <p>Zero external runtime dependencies live in this package by design
 * (Requirement 1.2): WAV and MP3 support ships in the sibling
 * {@code com.tino1b2be.dtmf.io.wav} and {@code com.tino1b2be.dtmf.io.mp3}
 * packages (and their matching Gradle modules {@code dtmf-io-wav} and
 * {@code dtmf-io-mp3}), and future formats plug in the same way without
 * touching this package or {@code dtmf-core}. All production classes in the
 * {@code dtmf-io} module live under this package root (Requirement 2.4); the
 * legacy v1 package {@code com.tino1b2be.audio} is not revived.
 *
 * <p>The concrete types are introduced starting at Stage 2 of the
 * {@code dtmf-io} spec; this {@code package-info.java} is present from
 * Stage 1 so the source tree exists for the build-shape smoke tests in
 * Task 1.8.
 *
 * @since 2.0.0
 */
package com.tino1b2be.dtmf.io;
