package com.tino1b2be.dtmf.io.internal;

import com.tino1b2be.dtmf.io.PcmEncoding;

import java.nio.ByteOrder;
import java.util.Objects;

/**
 * Shared PCM sample normalisation helper for the {@code dtmf-io} family of
 * modules. Bytes-to-{@code double} decoding for every
 * {@code (bitDepth, byteOrder, encoding)} tuple this project supports lives
 * here in a single place, so the normalisation formulas cannot drift between
 * {@code RawPcmAudioSource}, the clean-room WAV reader in
 * {@code dtmf-io-wav}, and the {@code mp3spi}-backed decoder in
 * {@code dtmf-io-mp3}.
 *
 * <p><strong>This class is not part of the published API.</strong> It lives
 * in {@code com.tino1b2be.dtmf.io.internal}, whose stability contract
 * (see the package Javadoc) explicitly allows breakage between any two
 * releases. It is {@code public} at the type level purely so the WAV and
 * MP3 provider modules &mdash; which live in sibling Java packages &mdash;
 * can reach it; external callers MUST NOT depend on it.
 *
 * <h2>Conversion formulas (Requirements 3.6, 7.12, 9.14)</h2>
 *
 * The normalisation matches {@code dtmf-core}'s
 * {@code com.tino1b2be.dtmf.internal.SampleConverter} contract exactly:
 *
 * <ul>
 *   <li><strong>Signed integer PCM</strong>: decoded as a two's-complement
 *       signed integer in the requested byte order, then divided by
 *       {@code 2^(bitDepth - 1)}. {@code Short.MIN_VALUE} maps exactly to
 *       {@code -1.0}; {@code Short.MAX_VALUE} maps to
 *       {@code 32767/32768 ≈ 0.99996948}. The analogous exact-and-near
 *       mapping holds at 24, 32, and 64 bits.</li>
 *   <li><strong>Unsigned integer PCM</strong>: decoded as an unsigned
 *       integer in the requested byte order, then the midpoint
 *       {@code 2^(bitDepth - 1)} is subtracted and the result is divided
 *       by {@code 2^(bitDepth - 1)}. An unsigned sample whose value is the
 *       midpoint therefore maps to exactly {@code 0.0}; an unsigned sample
 *       of {@code 0} maps to {@code -1.0}; the maximum unsigned value maps
 *       just below {@code +1.0}.</li>
 *   <li><strong>IEEE float</strong>: the raw bit pattern is assembled in
 *       the requested byte order and reinterpreted via
 *       {@link Float#intBitsToFloat(int)} (32-bit) or
 *       {@link Double#longBitsToDouble(long)} (64-bit). No scaling is
 *       applied &mdash; float PCM samples are already in
 *       {@code [-1.0, 1.0]} by convention. The 32-bit variant widens to
 *       {@code double} by direct cast.</li>
 * </ul>
 *
 * <h2>Supported tuples</h2>
 *
 * Every {@code (bitDepth, byteOrder, encoding)} combination that appears in
 * the PCM sample-formats table of {@code design.md} is supported:
 *
 * <ul>
 *   <li>{@code SIGNED_INT}: {@code bitDepth ∈ {16, 24, 32, 64}} &times;
 *       {@code byteOrder ∈ {LITTLE_ENDIAN, BIG_ENDIAN}}.</li>
 *   <li>{@code UNSIGNED_INT}: {@code bitDepth ∈ {16, 24, 32, 64}} &times;
 *       {@code byteOrder ∈ {LITTLE_ENDIAN, BIG_ENDIAN}}.</li>
 *   <li>{@code IEEE_FLOAT}: {@code bitDepth ∈ {32, 64}} &times;
 *       {@code byteOrder ∈ {LITTLE_ENDIAN, BIG_ENDIAN}}.</li>
 * </ul>
 *
 * Any other combination is rejected by
 * {@link #decoderFor(int, ByteOrder, PcmEncoding)} with an
 * {@link IllegalArgumentException}; callers upstream (notably
 * {@code RawPcmAudioSource}'s constructor) are expected to have validated
 * their inputs before asking for a decoder, so this dispatcher's rejection
 * is the second line of defence.
 *
 * @since 2.1.0
 */
public final class SampleConversion {

    /** Divisor for PCM16 &rarr; normalised double ({@code 2^15}). */
    private static final double PCM16_DIVISOR = 32768.0;
    /** Midpoint subtracted from unsigned PCM16 before division. */
    private static final int PCM16_MIDPOINT = 32768;

    /** Divisor for PCM24 &rarr; normalised double ({@code 2^23}). */
    private static final double PCM24_DIVISOR = 8388608.0;
    /** Midpoint subtracted from unsigned PCM24 before division. */
    private static final int PCM24_MIDPOINT = 8388608;

    /** Divisor for PCM32 &rarr; normalised double ({@code 2^31}). */
    private static final double PCM32_DIVISOR = 2147483648.0;
    /** Midpoint subtracted from unsigned PCM32 before division. */
    private static final long PCM32_MIDPOINT = 2147483648L;

    /** Divisor for PCM64 &rarr; normalised double ({@code 2^63}). */
    private static final double PCM64_DIVISOR = 9223372036854775808.0;
    /**
     * Midpoint subtracted from unsigned PCM64 before division. Held as a
     * {@code double} because {@code 2^63} is not representable as a signed
     * {@code long}; the subtraction is performed in {@code double} space
     * to avoid wrap-around on the high end of the unsigned range.
     */
    private static final double PCM64_MIDPOINT = 9223372036854775808.0;

    private SampleConversion() { }

    // ------------------------------------------------------------------
    // Public dispatcher
    // ------------------------------------------------------------------

    /**
     * Primitive functional interface for a single-frame PCM decoder.
     *
     * <p>Implementations read {@code bytesPerSample} bytes from
     * {@code data} starting at {@code offset}, interpret them per the
     * decoder's fixed {@code (bitDepth, byteOrder, encoding)} tuple, and
     * return the normalised {@code double} sample. Bounds checking is the
     * caller's responsibility: each call assumes {@code data} contains
     * enough bytes starting at {@code offset}.
     *
     * <p>Decoders are stateless and may be cached or shared across
     * threads.
     *
     * @since 2.1.0
     */
    @FunctionalInterface
    public interface SampleDecoder {
        /**
         * Decode a single PCM sample starting at {@code offset} into a
         * normalised {@code double} in {@code [-1.0, 1.0]}.
         *
         * @param data   byte buffer holding raw PCM bytes
         * @param offset byte index of the first byte of the sample; the
         *               decoder reads the fixed number of bytes for its
         *               bit depth starting here
         * @return normalised sample value
         */
        double decode(byte[] data, int offset);
    }

    /**
     * Return the {@link SampleDecoder} for a given
     * {@code (bitDepth, byteOrder, encoding)} tuple.
     *
     * @param bitDepth one of {@code 16}, {@code 24}, {@code 32},
     *                 {@code 64}
     * @param order    {@link ByteOrder#LITTLE_ENDIAN} or
     *                 {@link ByteOrder#BIG_ENDIAN}
     * @param encoding PCM numeric encoding
     * @return decoder for the requested tuple
     * @throws NullPointerException     if {@code order} or
     *                                  {@code encoding} is {@code null}
     * @throws IllegalArgumentException if the
     *                                  {@code (bitDepth, encoding)} pair
     *                                  is not supported (for example
     *                                  {@code IEEE_FLOAT} with
     *                                  {@code bitDepth == 16})
     */
    public static SampleDecoder decoderFor(int bitDepth, ByteOrder order, PcmEncoding encoding) {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(encoding, "encoding");
        boolean little = order == ByteOrder.LITTLE_ENDIAN;
        switch (encoding) {
            case SIGNED_INT:
                switch (bitDepth) {
                    case 16: return little ? SampleConversion::decodePcm16LE
                                           : SampleConversion::decodePcm16BE;
                    case 24: return little ? SampleConversion::decodePcm24LE
                                           : SampleConversion::decodePcm24BE;
                    case 32: return little ? SampleConversion::decodePcm32LE
                                           : SampleConversion::decodePcm32BE;
                    case 64: return little ? SampleConversion::decodePcm64LE
                                           : SampleConversion::decodePcm64BE;
                    default: throw unsupported(bitDepth, order, encoding);
                }
            case UNSIGNED_INT:
                switch (bitDepth) {
                    case 16: return little ? SampleConversion::decodeUnsignedPcm16LE
                                           : SampleConversion::decodeUnsignedPcm16BE;
                    case 24: return little ? SampleConversion::decodeUnsignedPcm24LE
                                           : SampleConversion::decodeUnsignedPcm24BE;
                    case 32: return little ? SampleConversion::decodeUnsignedPcm32LE
                                           : SampleConversion::decodeUnsignedPcm32BE;
                    case 64: return little ? SampleConversion::decodeUnsignedPcm64LE
                                           : SampleConversion::decodeUnsignedPcm64BE;
                    default: throw unsupported(bitDepth, order, encoding);
                }
            case IEEE_FLOAT:
                switch (bitDepth) {
                    case 32: return little ? SampleConversion::decodeFloat32LE
                                           : SampleConversion::decodeFloat32BE;
                    case 64: return little ? SampleConversion::decodeFloat64LE
                                           : SampleConversion::decodeFloat64BE;
                    default: throw unsupported(bitDepth, order, encoding);
                }
            default:
                // Defensive: PcmEncoding is a closed enum, but leave the
                // branch so a future new value fails loudly.
                throw new IllegalArgumentException("Unknown PcmEncoding: " + encoding);
        }
    }

    private static IllegalArgumentException unsupported(int bitDepth, ByteOrder order, PcmEncoding encoding) {
        return new IllegalArgumentException(
                "Unsupported (bitDepth, byteOrder, encoding) tuple: ("
                        + bitDepth + ", " + order + ", " + encoding + ")");
    }

    // ------------------------------------------------------------------
    // PCM16 signed
    // ------------------------------------------------------------------

    /**
     * Decode a little-endian signed PCM16 sample and normalise by
     * {@code 2^15}.
     *
     * @param b      source bytes
     * @param offset index of the low byte of the sample
     * @return normalised sample in {@code [-1.0, 1.0]}
     */
    public static double decodePcm16LE(byte[] b, int offset) {
        int v = ((b[offset] & 0xFF))
              | (b[offset + 1] << 8);              // sign-extend high byte
        return ((short) v) / PCM16_DIVISOR;
    }

    /**
     * Decode a big-endian signed PCM16 sample and normalise by
     * {@code 2^15}.
     *
     * @param b      source bytes
     * @param offset index of the high byte of the sample
     * @return normalised sample in {@code [-1.0, 1.0]}
     */
    public static double decodePcm16BE(byte[] b, int offset) {
        int v = (b[offset] << 8)                   // sign-extend high byte
              | (b[offset + 1] & 0xFF);
        return ((short) v) / PCM16_DIVISOR;
    }

    // ------------------------------------------------------------------
    // PCM24 signed
    // ------------------------------------------------------------------

    /**
     * Decode a little-endian signed PCM24 sample, sign-extend from bit
     * 23, and normalise by {@code 2^23}.
     *
     * @param b      source bytes
     * @param offset index of the low byte of the sample
     * @return normalised sample in {@code [-1.0, 1.0]}
     */
    public static double decodePcm24LE(byte[] b, int offset) {
        int v = (b[offset] & 0xFF)
              | ((b[offset + 1] & 0xFF) << 8)
              | ((b[offset + 2] & 0xFF) << 16);
        // Sign-extend from bit 23: shift sign bit up to bit 31 then
        // arithmetic-shift right eight bits so the sign fills the high
        // byte. `>>` on an `int` is arithmetic in Java.
        v = (v << 8) >> 8;
        return v / PCM24_DIVISOR;
    }

    /**
     * Decode a big-endian signed PCM24 sample, sign-extend from bit 23,
     * and normalise by {@code 2^23}.
     *
     * @param b      source bytes
     * @param offset index of the high byte of the sample
     * @return normalised sample in {@code [-1.0, 1.0]}
     */
    public static double decodePcm24BE(byte[] b, int offset) {
        int v = ((b[offset] & 0xFF) << 16)
              | ((b[offset + 1] & 0xFF) << 8)
              | (b[offset + 2] & 0xFF);
        v = (v << 8) >> 8;
        return v / PCM24_DIVISOR;
    }

    // ------------------------------------------------------------------
    // PCM32 signed
    // ------------------------------------------------------------------

    /**
     * Decode a little-endian signed PCM32 sample and normalise by
     * {@code 2^31}.
     *
     * @param b      source bytes
     * @param offset index of the low byte of the sample
     * @return normalised sample in {@code [-1.0, 1.0]}
     */
    public static double decodePcm32LE(byte[] b, int offset) {
        int v = (b[offset] & 0xFF)
              | ((b[offset + 1] & 0xFF) << 8)
              | ((b[offset + 2] & 0xFF) << 16)
              | (b[offset + 3] << 24);
        return v / PCM32_DIVISOR;
    }

    /**
     * Decode a big-endian signed PCM32 sample and normalise by
     * {@code 2^31}.
     *
     * @param b      source bytes
     * @param offset index of the high byte of the sample
     * @return normalised sample in {@code [-1.0, 1.0]}
     */
    public static double decodePcm32BE(byte[] b, int offset) {
        int v = (b[offset] << 24)
              | ((b[offset + 1] & 0xFF) << 16)
              | ((b[offset + 2] & 0xFF) << 8)
              | (b[offset + 3] & 0xFF);
        return v / PCM32_DIVISOR;
    }

    // ------------------------------------------------------------------
    // PCM64 signed
    // ------------------------------------------------------------------

    /**
     * Decode a little-endian signed PCM64 sample and normalise by
     * {@code 2^63}.
     *
     * @param b      source bytes
     * @param offset index of the low byte of the sample
     * @return normalised sample in {@code [-1.0, 1.0]}
     */
    public static double decodePcm64LE(byte[] b, int offset) {
        long v = (b[offset] & 0xFFL)
              | ((b[offset + 1] & 0xFFL) << 8)
              | ((b[offset + 2] & 0xFFL) << 16)
              | ((b[offset + 3] & 0xFFL) << 24)
              | ((b[offset + 4] & 0xFFL) << 32)
              | ((b[offset + 5] & 0xFFL) << 40)
              | ((b[offset + 6] & 0xFFL) << 48)
              | (((long) b[offset + 7]) << 56);
        return v / PCM64_DIVISOR;
    }

    /**
     * Decode a big-endian signed PCM64 sample and normalise by
     * {@code 2^63}.
     *
     * @param b      source bytes
     * @param offset index of the high byte of the sample
     * @return normalised sample in {@code [-1.0, 1.0]}
     */
    public static double decodePcm64BE(byte[] b, int offset) {
        long v = (((long) b[offset]) << 56)
              | ((b[offset + 1] & 0xFFL) << 48)
              | ((b[offset + 2] & 0xFFL) << 40)
              | ((b[offset + 3] & 0xFFL) << 32)
              | ((b[offset + 4] & 0xFFL) << 24)
              | ((b[offset + 5] & 0xFFL) << 16)
              | ((b[offset + 6] & 0xFFL) << 8)
              | (b[offset + 7] & 0xFFL);
        return v / PCM64_DIVISOR;
    }

    // ------------------------------------------------------------------
    // PCM16 unsigned
    // ------------------------------------------------------------------

    /**
     * Decode a little-endian unsigned PCM16 sample, subtract the midpoint
     * {@code 2^15}, and normalise by {@code 2^15}.
     *
     * @param b      source bytes
     * @param offset index of the low byte of the sample
     * @return normalised sample in {@code [-1.0, 1.0]}
     */
    public static double decodeUnsignedPcm16LE(byte[] b, int offset) {
        int v = (b[offset] & 0xFF)
              | ((b[offset + 1] & 0xFF) << 8);
        return (v - PCM16_MIDPOINT) / PCM16_DIVISOR;
    }

    /**
     * Decode a big-endian unsigned PCM16 sample, subtract the midpoint
     * {@code 2^15}, and normalise by {@code 2^15}.
     *
     * @param b      source bytes
     * @param offset index of the high byte of the sample
     * @return normalised sample in {@code [-1.0, 1.0]}
     */
    public static double decodeUnsignedPcm16BE(byte[] b, int offset) {
        int v = ((b[offset] & 0xFF) << 8)
              | (b[offset + 1] & 0xFF);
        return (v - PCM16_MIDPOINT) / PCM16_DIVISOR;
    }

    // ------------------------------------------------------------------
    // PCM24 unsigned
    // ------------------------------------------------------------------

    /**
     * Decode a little-endian unsigned PCM24 sample, subtract the midpoint
     * {@code 2^23}, and normalise by {@code 2^23}.
     *
     * @param b      source bytes
     * @param offset index of the low byte of the sample
     * @return normalised sample in {@code [-1.0, 1.0]}
     */
    public static double decodeUnsignedPcm24LE(byte[] b, int offset) {
        int v = (b[offset] & 0xFF)
              | ((b[offset + 1] & 0xFF) << 8)
              | ((b[offset + 2] & 0xFF) << 16);
        return (v - PCM24_MIDPOINT) / PCM24_DIVISOR;
    }

    /**
     * Decode a big-endian unsigned PCM24 sample, subtract the midpoint
     * {@code 2^23}, and normalise by {@code 2^23}.
     *
     * @param b      source bytes
     * @param offset index of the high byte of the sample
     * @return normalised sample in {@code [-1.0, 1.0]}
     */
    public static double decodeUnsignedPcm24BE(byte[] b, int offset) {
        int v = ((b[offset] & 0xFF) << 16)
              | ((b[offset + 1] & 0xFF) << 8)
              | (b[offset + 2] & 0xFF);
        return (v - PCM24_MIDPOINT) / PCM24_DIVISOR;
    }

    // ------------------------------------------------------------------
    // PCM32 unsigned
    // ------------------------------------------------------------------

    /**
     * Decode a little-endian unsigned PCM32 sample, subtract the midpoint
     * {@code 2^31}, and normalise by {@code 2^31}.
     *
     * <p>The unsigned value is read into a {@code long} (since
     * {@code 2^32 - 1} does not fit in {@code int}), so the subtraction
     * is exact.
     *
     * @param b      source bytes
     * @param offset index of the low byte of the sample
     * @return normalised sample in {@code [-1.0, 1.0]}
     */
    public static double decodeUnsignedPcm32LE(byte[] b, int offset) {
        long v = (b[offset] & 0xFFL)
              | ((b[offset + 1] & 0xFFL) << 8)
              | ((b[offset + 2] & 0xFFL) << 16)
              | ((b[offset + 3] & 0xFFL) << 24);
        return (v - PCM32_MIDPOINT) / PCM32_DIVISOR;
    }

    /**
     * Decode a big-endian unsigned PCM32 sample, subtract the midpoint
     * {@code 2^31}, and normalise by {@code 2^31}.
     *
     * @param b      source bytes
     * @param offset index of the high byte of the sample
     * @return normalised sample in {@code [-1.0, 1.0]}
     */
    public static double decodeUnsignedPcm32BE(byte[] b, int offset) {
        long v = ((b[offset] & 0xFFL) << 24)
              | ((b[offset + 1] & 0xFFL) << 16)
              | ((b[offset + 2] & 0xFFL) << 8)
              | (b[offset + 3] & 0xFFL);
        return (v - PCM32_MIDPOINT) / PCM32_DIVISOR;
    }

    // ------------------------------------------------------------------
    // PCM64 unsigned
    // ------------------------------------------------------------------

    /**
     * Decode a little-endian unsigned PCM64 sample, subtract the midpoint
     * {@code 2^63}, and normalise by {@code 2^63}.
     *
     * <p>{@code 2^63} is not representable as a signed {@code long}, so
     * the raw 64-bit payload is widened to {@code double} via
     * {@link #toUnsignedDouble(long)} before the subtraction; the result
     * is therefore approximate at the ULP level of {@code double} but
     * exact for the telephony-scale magnitudes real callers have.
     *
     * @param b      source bytes
     * @param offset index of the low byte of the sample
     * @return normalised sample in {@code [-1.0, 1.0]}
     */
    public static double decodeUnsignedPcm64LE(byte[] b, int offset) {
        long raw = (b[offset] & 0xFFL)
                 | ((b[offset + 1] & 0xFFL) << 8)
                 | ((b[offset + 2] & 0xFFL) << 16)
                 | ((b[offset + 3] & 0xFFL) << 24)
                 | ((b[offset + 4] & 0xFFL) << 32)
                 | ((b[offset + 5] & 0xFFL) << 40)
                 | ((b[offset + 6] & 0xFFL) << 48)
                 | (((long) b[offset + 7]) << 56);
        return (toUnsignedDouble(raw) - PCM64_MIDPOINT) / PCM64_DIVISOR;
    }

    /**
     * Decode a big-endian unsigned PCM64 sample, subtract the midpoint
     * {@code 2^63}, and normalise by {@code 2^63}.
     *
     * @param b      source bytes
     * @param offset index of the high byte of the sample
     * @return normalised sample in {@code [-1.0, 1.0]}
     */
    public static double decodeUnsignedPcm64BE(byte[] b, int offset) {
        long raw = (((long) b[offset]) << 56)
                 | ((b[offset + 1] & 0xFFL) << 48)
                 | ((b[offset + 2] & 0xFFL) << 40)
                 | ((b[offset + 3] & 0xFFL) << 32)
                 | ((b[offset + 4] & 0xFFL) << 24)
                 | ((b[offset + 5] & 0xFFL) << 16)
                 | ((b[offset + 6] & 0xFFL) << 8)
                 | (b[offset + 7] & 0xFFL);
        return (toUnsignedDouble(raw) - PCM64_MIDPOINT) / PCM64_DIVISOR;
    }

    /**
     * Widen a raw 64-bit value interpreted as unsigned into a
     * {@code double}. Non-negative {@code long}s widen directly;
     * negative {@code long}s (unsigned values &ge; {@code 2^63}) are
     * converted by clearing the sign bit and adding {@code 2^63} back in
     * {@code double} space.
     */
    private static double toUnsignedDouble(long raw) {
        if (raw >= 0L) {
            return (double) raw;
        }
        // Clear the sign bit then add 2^63 back in double space to
        // recover the true unsigned magnitude.
        return ((double) (raw & Long.MAX_VALUE)) + PCM64_MIDPOINT;
    }

    // ------------------------------------------------------------------
    // IEEE floating point
    // ------------------------------------------------------------------

    /**
     * Decode a little-endian IEEE 754 32-bit float sample and widen to
     * {@code double} without scaling.
     *
     * @param b      source bytes
     * @param offset index of the low byte of the sample
     * @return the widened float value
     */
    public static double decodeFloat32LE(byte[] b, int offset) {
        int bits = (b[offset] & 0xFF)
                 | ((b[offset + 1] & 0xFF) << 8)
                 | ((b[offset + 2] & 0xFF) << 16)
                 | (b[offset + 3] << 24);
        return Float.intBitsToFloat(bits);
    }

    /**
     * Decode a big-endian IEEE 754 32-bit float sample and widen to
     * {@code double} without scaling.
     *
     * @param b      source bytes
     * @param offset index of the high byte of the sample
     * @return the widened float value
     */
    public static double decodeFloat32BE(byte[] b, int offset) {
        int bits = (b[offset] << 24)
                 | ((b[offset + 1] & 0xFF) << 16)
                 | ((b[offset + 2] & 0xFF) << 8)
                 | (b[offset + 3] & 0xFF);
        return Float.intBitsToFloat(bits);
    }

    /**
     * Decode a little-endian IEEE 754 64-bit double sample bit-exactly.
     *
     * @param b      source bytes
     * @param offset index of the low byte of the sample
     * @return the decoded double value
     */
    public static double decodeFloat64LE(byte[] b, int offset) {
        long bits = (b[offset] & 0xFFL)
                  | ((b[offset + 1] & 0xFFL) << 8)
                  | ((b[offset + 2] & 0xFFL) << 16)
                  | ((b[offset + 3] & 0xFFL) << 24)
                  | ((b[offset + 4] & 0xFFL) << 32)
                  | ((b[offset + 5] & 0xFFL) << 40)
                  | ((b[offset + 6] & 0xFFL) << 48)
                  | (((long) b[offset + 7]) << 56);
        return Double.longBitsToDouble(bits);
    }

    /**
     * Decode a big-endian IEEE 754 64-bit double sample bit-exactly.
     *
     * @param b      source bytes
     * @param offset index of the high byte of the sample
     * @return the decoded double value
     */
    public static double decodeFloat64BE(byte[] b, int offset) {
        long bits = (((long) b[offset]) << 56)
                  | ((b[offset + 1] & 0xFFL) << 48)
                  | ((b[offset + 2] & 0xFFL) << 40)
                  | ((b[offset + 3] & 0xFFL) << 32)
                  | ((b[offset + 4] & 0xFFL) << 24)
                  | ((b[offset + 5] & 0xFFL) << 16)
                  | ((b[offset + 6] & 0xFFL) << 8)
                  | (b[offset + 7] & 0xFFL);
        return Double.longBitsToDouble(bits);
    }
}
