package com.tino1b2be.dtmf.io.wav.internal;

import java.util.Objects;

/**
 * Parsed metadata from a WAV file's {@code fmt } chunk together with the
 * byte-range coordinates of its {@code data} payload. Populated by the RIFF
 * parser in this package and handed to
 * {@code com.tino1b2be.dtmf.io.wav.WavAudioSource} and
 * {@code WavSampleReader} so they can decode frames without re-reading the
 * header.
 *
 * <p>This record is the shared description of a validated, supported WAV
 * stream. By the time one is constructed, the parser has already rejected
 * compressed encodings (&micro;-law, A-law, ADPCM; Requirement 9.10),
 * unsupported bit depths, and unsupported channel counts
 * (Requirements 9.7, 9.8, 9.9). Every field below is therefore guaranteed
 * to be internally consistent and in range.
 *
 * <p><strong>Field-level contract:</strong>
 * <ul>
 *   <li>{@code sampleRate} &mdash; in Hertz, the value of the {@code fmt }
 *       chunk's {@code nSamplesPerSec} field. Must be strictly positive.
 *       The downstream {@code DtmfFileDecoder} enforces the module-wide
 *       {@code [4000, 192000]} window; this record itself does not impose
 *       that upper bound because the parser needs to carry any positive
 *       rate through to {@code WavAudioSource.sampleRate()} before the
 *       higher-level guard runs.</li>
 *   <li>{@code channelCount} &mdash; from {@code nChannels}. Must be
 *       strictly positive. In practice the parser restricts the set of
 *       <em>decoded</em> channel counts to {@code 1} or {@code 2}
 *       (Requirements 9.7, 9.8, 9.9); this record accepts any positive
 *       value so unit tests can exercise the underlying container format
 *       without threading the channel guard all the way down.</li>
 *   <li>{@code bitDepth} &mdash; from {@code wBitsPerSample}, or from
 *       {@code wValidBitsPerSample} for {@code WAVEFORMATEXTENSIBLE}.
 *       Must be one of {@code 16}, {@code 24}, {@code 32}, or {@code 64}.
 *       The {@code dtmf-io} module does not support {@code 8}-bit PCM
 *       (Requirement 3.4).</li>
 *   <li>{@code bytesPerFrame} &mdash; from the {@code fmt } chunk's
 *       {@code nBlockAlign} field, validated by the parser to equal
 *       {@code (bitDepth / 8) * channelCount}. This record re-validates
 *       the relationship so a bug in the parser cannot silently propagate
 *       a mismatch downstream.</li>
 *   <li>{@code encoding} &mdash; either {@link Encoding#PCM_SIGNED} (from
 *       {@code wFormatTag = 0x0001} or the {@code KSDATAFORMAT_SUBTYPE_PCM}
 *       GUID) or {@link Encoding#IEEE_FLOAT} (from
 *       {@code wFormatTag = 0x0003} or {@code KSDATAFORMAT_SUBTYPE_IEEE_FLOAT}).
 *       {@code IEEE_FLOAT} is only valid at {@code 32} or {@code 64} bits
 *       &mdash; a constraint enforced by this record's compact constructor
 *       as a belt-and-braces check on top of the parser's own validation.</li>
 *   <li>{@code dataStartByteOffset} &mdash; absolute byte offset within
 *       the input stream or file where the payload of the {@code data}
 *       chunk begins (i.e. immediately after its 8-byte
 *       {@code "data" | size} header). Must be non-negative.
 *       {@code WavAudioSource} uses this value as the seek anchor when
 *       {@code canSeek()} returns {@code true}: a seek to frame
 *       {@code f} sets the channel position to
 *       {@code dataStartByteOffset + f * bytesPerFrame}.</li>
 *   <li>{@code dataSizeBytes} &mdash; length of the {@code data} chunk
 *       payload in bytes, taken either from the chunk's 32-bit size field
 *       (standard {@code RIFF}/{@code WAVE}) or from the {@code ds64}
 *       chunk's 64-bit {@code dataSize} field (RF64). Must be
 *       non-negative.</li>
 *   <li>{@code totalFrames} &mdash; {@code dataSizeBytes / bytesPerFrame},
 *       i.e. the number of complete frames the file is declared to
 *       contain. Must be non-negative. Stored eagerly (rather than
 *       derived on every call) because {@code AudioSource.totalFrames()}
 *       is on the hot metadata path for {@code DtmfFileDecoder}'s block
 *       sizing.</li>
 * </ul>
 *
 * <p><strong>This record is not part of the published API.</strong> It
 * lives in {@code com.tino1b2be.dtmf.io.wav.internal}, whose stability
 * contract (see the package Javadoc) explicitly allows breakage between
 * any two releases. It is {@code public} at the type level purely so
 * {@code WavAudioSource} and {@code WavAudioSourceProvider} &mdash; which
 * live in the parent package and cannot otherwise see a package-private
 * type here &mdash; can reach it; external callers MUST NOT depend on it.
 *
 * @param sampleRate          sample rate in Hertz; must be strictly
 *                            positive
 * @param channelCount        number of interleaved channels in each frame;
 *                            must be strictly positive
 * @param bitDepth            bits per sample; must be one of
 *                            {@code {16, 24, 32, 64}}
 * @param bytesPerFrame       size of one complete frame in bytes; must
 *                            equal {@code (bitDepth / 8) * channelCount}
 * @param encoding            PCM integer or IEEE float; must be non-null
 *                            and, when {@link Encoding#IEEE_FLOAT}, must
 *                            be paired with a bit depth in
 *                            {@code {32, 64}}
 * @param dataStartByteOffset absolute byte offset of the {@code data}
 *                            chunk payload within the enclosing RIFF
 *                            form; must be non-negative
 * @param dataSizeBytes       length of the {@code data} chunk payload in
 *                            bytes; must be non-negative
 * @param totalFrames         number of complete frames in the payload
 *                            ({@code dataSizeBytes / bytesPerFrame});
 *                            must be non-negative
 * @since 2.1.0
 */
public record WaveFormat(
        int sampleRate,
        int channelCount,
        int bitDepth,
        int bytesPerFrame,
        Encoding encoding,
        long dataStartByteOffset,
        long dataSizeBytes,
        long totalFrames) {

    /**
     * Numeric-format discriminator for a validated WAV stream. Only the
     * two encodings supported by this module appear here &mdash; the
     * parser rejects &micro;-law, A-law, ADPCM and every other compressed
     * {@code wFormatTag} value before a {@code WaveFormat} is ever
     * constructed (Requirement 9.10).
     */
    public enum Encoding {

        /**
         * Two's-complement signed integer PCM. Corresponds to
         * {@code wFormatTag = 0x0001} in the {@code fmt } chunk, or to
         * the {@code KSDATAFORMAT_SUBTYPE_PCM} GUID when the file uses
         * {@code WAVEFORMATEXTENSIBLE}. Valid bit depths are
         * {@code {16, 24, 32}} for the WAV container; a {@code 64}-bit
         * signed-integer WAV is pathological and not expected in the
         * wild, but the record itself imposes no upper bit-depth limit
         * beyond the shared {@code {16, 24, 32, 64}} set.
         */
        PCM_SIGNED,

        /**
         * IEEE 754 floating-point PCM, already in {@code [-1.0, 1.0]} by
         * convention and read without scaling. Corresponds to
         * {@code wFormatTag = 0x0003}, or to the
         * {@code KSDATAFORMAT_SUBTYPE_IEEE_FLOAT} GUID under
         * {@code WAVEFORMATEXTENSIBLE}. Valid bit depths are
         * {@code {32, 64}} only.
         */
        IEEE_FLOAT
    }

    /**
     * Compact constructor validating every field against the class-level
     * contract. This is the single source of truth for what constitutes a
     * "supported WAV stream" inside {@code dtmf-io-wav}; the parser runs
     * its own pre-flight checks against the raw header bytes, but the
     * final, authoritative guard lives here so a test fixture that
     * constructs {@code WaveFormat} directly is held to the same invariants
     * as a real file.
     *
     * @throws NullPointerException     if {@code encoding} is {@code null}
     * @throws IllegalArgumentException if any numeric field is out of
     *                                  range, if {@code bytesPerFrame}
     *                                  does not equal
     *                                  {@code (bitDepth / 8) * channelCount},
     *                                  or if {@code encoding} is
     *                                  {@link Encoding#IEEE_FLOAT} with a
     *                                  bit depth outside {@code {32, 64}}
     */
    public WaveFormat {
        Objects.requireNonNull(encoding, "encoding");
        if (sampleRate <= 0) {
            throw new IllegalArgumentException(
                    "sampleRate must be > 0, got " + sampleRate);
        }
        if (channelCount <= 0) {
            throw new IllegalArgumentException(
                    "channelCount must be > 0, got " + channelCount);
        }
        if (bitDepth != 16 && bitDepth != 24 && bitDepth != 32 && bitDepth != 64) {
            throw new IllegalArgumentException(
                    "bitDepth must be one of {16, 24, 32, 64}, got " + bitDepth);
        }
        int expectedBytesPerFrame = (bitDepth / 8) * channelCount;
        if (bytesPerFrame != expectedBytesPerFrame) {
            throw new IllegalArgumentException(
                    "bytesPerFrame must equal (bitDepth / 8) * channelCount = "
                            + expectedBytesPerFrame + " for bitDepth=" + bitDepth
                            + " and channelCount=" + channelCount
                            + ", got " + bytesPerFrame);
        }
        if (encoding == Encoding.IEEE_FLOAT && bitDepth != 32 && bitDepth != 64) {
            throw new IllegalArgumentException(
                    "IEEE_FLOAT encoding requires bitDepth in {32, 64}, got " + bitDepth);
        }
        if (dataStartByteOffset < 0L) {
            throw new IllegalArgumentException(
                    "dataStartByteOffset must be >= 0, got " + dataStartByteOffset);
        }
        if (dataSizeBytes < 0L) {
            throw new IllegalArgumentException(
                    "dataSizeBytes must be >= 0, got " + dataSizeBytes);
        }
        if (totalFrames < 0L) {
            throw new IllegalArgumentException(
                    "totalFrames must be >= 0, got " + totalFrames);
        }
    }
}
