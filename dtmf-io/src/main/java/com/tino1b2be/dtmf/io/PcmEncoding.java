package com.tino1b2be.dtmf.io;

/**
 * PCM encoding discriminator for {@link RawPcmAudioSource} and other linear-PCM
 * carriers in {@code dtmf-io}.
 *
 * <p>The encoding is orthogonal to endianness and bit depth: a raw PCM byte
 * buffer is fully described by the triple {@code (bitDepth, byteOrder,
 * encoding)}, and {@code RawPcmAudioSource}'s constructor takes all three as
 * separate parameters. This enum captures only the numeric-format dimension
 * of the tuple; little-endian vs big-endian is carried by
 * {@link java.nio.ByteOrder}.
 *
 * <p>Not every {@code (encoding, bitDepth)} pair is valid. The cross-reference
 * table below is enforced by {@code RawPcmAudioSource}'s constructor
 * validation (Requirements 7.7 and 7.8) and by the WAV provider's
 * {@code fmt } chunk parser:
 *
 * <ul>
 *   <li>{@link #SIGNED_INT} &mdash; valid bit depths are {@code {16, 24, 32,
 *       64}}. Two's-complement signed integer PCM; by far the most common
 *       encoding in the wild (WAV {@code PCM_SIGNED}, telephony recordings,
 *       CD audio).</li>
 *   <li>{@link #UNSIGNED_INT} &mdash; valid bit depths are {@code {16, 24, 32,
 *       64}}. Unsigned integer PCM, in which the midpoint
 *       {@code 2^(bitDepth - 1)} represents silence. Rare at these bit
 *       depths (the classic unsigned-PCM use case is 8-bit WAV, which is
 *       out of scope for this module); supported here for generality.</li>
 *   <li>{@link #IEEE_FLOAT} &mdash; valid bit depths are {@code {32, 64}}
 *       <em>only</em>. IEEE 754 floating point samples, already in
 *       {@code [-1.0, 1.0]} by convention and therefore read without
 *       scaling. Constructing a {@code RawPcmAudioSource} with
 *       {@code IEEE_FLOAT} at {@code 16} or {@code 24} bits throws
 *       {@link IllegalArgumentException}.</li>
 * </ul>
 *
 * <p>Bit depth {@code 8} is not represented by any value of this enum because
 * the whole module targets {@code 16}, {@code 24}, {@code 32}, and {@code 64}
 * bits (Requirement 3.4). MP3 sources do not use this enum: they always
 * decode to signed 16-bit PCM and bypass the raw-PCM constructor path
 * entirely.
 *
 * @since 2.1.0
 * @see RawPcmAudioSource
 */
public enum PcmEncoding {

    /**
     * Two's-complement signed integer PCM. Valid bit depths:
     * {@code {16, 24, 32, 64}}.
     */
    SIGNED_INT,

    /**
     * Unsigned integer PCM; the midpoint {@code 2^(bitDepth - 1)} represents
     * silence. Valid bit depths: {@code {16, 24, 32, 64}}.
     */
    UNSIGNED_INT,

    /**
     * IEEE 754 floating-point PCM, already in {@code [-1.0, 1.0]} by
     * convention and read without scaling. Valid bit depths:
     * {@code {32, 64}} only.
     */
    IEEE_FLOAT
}
