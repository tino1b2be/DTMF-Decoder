package com.tino1b2be.dtmf.io.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteOrder;

import com.tino1b2be.dtmf.io.PcmEncoding;
import com.tino1b2be.dtmf.io.internal.SampleConversion.SampleDecoder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

/**
 * Boundary-value unit tests for {@link SampleConversion} (Task 3.2,
 * Requirement 7.12).
 *
 * <p>Requirement 7.12 pins the normalisation formulas for every
 * {@code (bitDepth, byteOrder, encoding)} tuple supported by
 * {@link SampleConversion}: signed integer samples divide by
 * {@code 2^(bitDepth - 1)}; {@code IEEE_FLOAT} samples widen without
 * scaling. That formula has exact-arithmetic boundaries that must hold for
 * every supported format — {@code Short.MIN_VALUE} must map to {@code -1.0}
 * <em>exactly</em>, {@code Short.MAX_VALUE} must map to
 * {@code 32767 / 32768} (not {@code 1.0} or {@code 0.9999…} rounded), and
 * so on at 24, 32, and 64 bits. The PCM24 sign-extension step has its own
 * boundary class (bit 23 set vs cleared) that the tests pin separately.
 *
 * <p>The tests also pin the endianness swap contract: the same
 * non-palindromic byte sequence decoded little-endian versus big-endian
 * must produce different values, each matching the per-byte-order formula.
 * That's the only behavioural contract the LE/BE split adds on top of the
 * bit-depth family.
 *
 * <p>Finally, the dispatcher returned by
 * {@link SampleConversion#decoderFor(int, java.nio.ByteOrder, com.tino1b2be.dtmf.io.PcmEncoding)}
 * is covered for both the happy path (every supported tuple wires to a
 * decoder that matches the static method of the same shape) and the
 * rejection path (unsupported tuples throw {@link IllegalArgumentException}
 * with an informative message, and {@code null} order/encoding throw
 * {@link NullPointerException}).
 */
class SampleConversionTest {

    // ---------------------------------------------------------------------
    // PCM16 signed — boundaries
    // ---------------------------------------------------------------------

    /**
     * {@code Short.MIN_VALUE} (bytes {@code {0x00, 0x80}} little-endian)
     * normalises to exactly {@code -1.0} because {@code -32768 / 32768 == -1.0}
     * is an exact IEEE-754 result.
     */
    @Test
    @DisplayName("PCM16 LE: Short.MIN_VALUE bytes decode to -1.0 exactly")
    void pcm16LeMinValueIsExactlyNegativeOne() {
        byte[] bytes = {0x00, (byte) 0x80};
        double actual = SampleConversion.decodePcm16LE(bytes, 0);
        assertEquals(-1.0, actual, 0.0,
                "Short.MIN_VALUE (-32768) / 2^15 must be exactly -1.0");
    }

    /**
     * {@code Short.MAX_VALUE} (bytes {@code {0xFF, 0x7F}} little-endian)
     * normalises to {@code 32767 / 32768}, which is strictly less than
     * {@code 1.0}. The exact value is representable, so assert it exactly.
     */
    @Test
    @DisplayName("PCM16 LE: Short.MAX_VALUE bytes decode to 32767/32768 (~0.99997)")
    void pcm16LeMaxValueIsJustBelowOne() {
        byte[] bytes = {(byte) 0xFF, 0x7F};
        double actual = SampleConversion.decodePcm16LE(bytes, 0);
        assertEquals(32767.0 / 32768.0, actual, 0.0,
                "Short.MAX_VALUE (32767) / 2^15 must equal 32767/32768 exactly");
        assertTrue(actual < 1.0, "Expected value strictly below 1.0");
        assertTrue(actual > 0.999, "Expected value near 1.0 (~0.99997)");
    }

    /**
     * All-zero bytes decode to exactly {@code 0.0} regardless of byte
     * order.
     */
    @Test
    @DisplayName("PCM16: zero bytes decode to 0.0 for both endiannesses")
    void pcm16ZeroDecodesToZero() {
        byte[] bytes = {0x00, 0x00};
        assertEquals(0.0, SampleConversion.decodePcm16LE(bytes, 0), 0.0,
                "PCM16 LE zero must be 0.0");
        assertEquals(0.0, SampleConversion.decodePcm16BE(bytes, 0), 0.0,
                "PCM16 BE zero must be 0.0");
    }

    /**
     * Same test as {@link #pcm16LeMinValueIsExactlyNegativeOne()} but for
     * big-endian: bytes {@code {0x80, 0x00}} represent {@code -32768} in
     * big-endian, which normalises to exactly {@code -1.0}.
     */
    @Test
    @DisplayName("PCM16 BE: Short.MIN_VALUE bytes decode to -1.0 exactly")
    void pcm16BeMinValueIsExactlyNegativeOne() {
        byte[] bytes = {(byte) 0x80, 0x00};
        double actual = SampleConversion.decodePcm16BE(bytes, 0);
        assertEquals(-1.0, actual, 0.0,
                "Short.MIN_VALUE bytes (big-endian) / 2^15 must be exactly -1.0");
    }

    /** Same exercise for {@code Short.MAX_VALUE} in big-endian. */
    @Test
    @DisplayName("PCM16 BE: Short.MAX_VALUE bytes decode to 32767/32768")
    void pcm16BeMaxValueIsJustBelowOne() {
        byte[] bytes = {0x7F, (byte) 0xFF};
        double actual = SampleConversion.decodePcm16BE(bytes, 0);
        assertEquals(32767.0 / 32768.0, actual, 0.0,
                "Short.MAX_VALUE bytes (big-endian) / 2^15 must equal 32767/32768 exactly");
    }

    // ---------------------------------------------------------------------
    // PCM24 signed — sign-extension boundaries
    // ---------------------------------------------------------------------

    /**
     * PCM24's trickiest corner is the sign-extension step: the assembled
     * 24-bit value {@code 0x7FFFFF} is the largest positive value, and
     * must decode to {@code (2^23 - 1) / 2^23}. Getting this wrong would
     * mean treating bit 23 as a sign bit when it is actually the top data
     * bit of a positive value.
     */
    @Test
    @DisplayName("PCM24 LE: 0x7FFFFF (max positive) decodes to (2^23 - 1)/2^23")
    void pcm24LeMaxPositiveSignExtension() {
        // Little-endian: low byte first — 0xFF, 0xFF, 0x7F encodes 0x7FFFFF.
        byte[] bytes = {(byte) 0xFF, (byte) 0xFF, 0x7F};
        double actual = SampleConversion.decodePcm24LE(bytes, 0);
        double expected = 8388607.0 / 8388608.0; // (2^23 - 1) / 2^23
        assertEquals(expected, actual, 0.0,
                "PCM24 LE 0x7FFFFF must decode to (2^23 - 1) / 2^23 exactly");
        assertTrue(actual < 1.0, "Expected value strictly below 1.0");
    }

    /**
     * Mirror corner: {@code 0x800000} is the smallest negative 24-bit
     * two's-complement value, {@code -2^23}, which must sign-extend to
     * {@code 0xFF800000} (i.e. {@code -8388608} as an {@code int}) and
     * normalise to exactly {@code -1.0}.
     */
    @Test
    @DisplayName("PCM24 LE: 0x800000 (min negative) decodes to -1.0 exactly")
    void pcm24LeMinNegativeSignExtension() {
        // Little-endian: low byte first — 0x00, 0x00, 0x80 encodes 0x800000.
        byte[] bytes = {0x00, 0x00, (byte) 0x80};
        double actual = SampleConversion.decodePcm24LE(bytes, 0);
        assertEquals(-1.0, actual, 0.0,
                "PCM24 LE 0x800000 must sign-extend to -2^23 and decode to -1.0 exactly");
    }

    /** Big-endian equivalent: top byte first, so bytes {@code {0x7F, 0xFF, 0xFF}}. */
    @Test
    @DisplayName("PCM24 BE: 0x7FFFFF decodes to (2^23 - 1)/2^23")
    void pcm24BeMaxPositive() {
        byte[] bytes = {0x7F, (byte) 0xFF, (byte) 0xFF};
        double actual = SampleConversion.decodePcm24BE(bytes, 0);
        assertEquals(8388607.0 / 8388608.0, actual, 0.0,
                "PCM24 BE 0x7FFFFF must decode to (2^23 - 1) / 2^23 exactly");
    }

    /** Big-endian equivalent of the {@code -1.0} corner. */
    @Test
    @DisplayName("PCM24 BE: 0x800000 decodes to -1.0 exactly")
    void pcm24BeMinNegative() {
        byte[] bytes = {(byte) 0x80, 0x00, 0x00};
        double actual = SampleConversion.decodePcm24BE(bytes, 0);
        assertEquals(-1.0, actual, 0.0,
                "PCM24 BE 0x800000 must decode to -1.0 exactly");
    }

    /**
     * PCM24 sign-extension is sensitive to bit 23 specifically; pin the
     * value just below {@code 0x800000} (i.e. {@code 0x7FFFFE}) to
     * confirm it stays positive and close to {@code +1.0}, and the value
     * just above (i.e. {@code 0x800001}) to confirm it stays negative
     * and near {@code -1.0}.
     */
    @Test
    @DisplayName("PCM24 LE: bit-23 boundary values have the correct sign")
    void pcm24LeBit23BoundarySigns() {
        // 0x7FFFFE → +8388606 → +8388606/8388608
        byte[] belowMidpoint = {(byte) 0xFE, (byte) 0xFF, 0x7F};
        double below = SampleConversion.decodePcm24LE(belowMidpoint, 0);
        assertTrue(below > 0, "0x7FFFFE must be positive");
        assertEquals(8388606.0 / 8388608.0, below, 0.0);

        // 0x800001 → -8388607 → -8388607/8388608
        byte[] aboveMidpoint = {0x01, 0x00, (byte) 0x80};
        double above = SampleConversion.decodePcm24LE(aboveMidpoint, 0);
        assertTrue(above < 0, "0x800001 must be negative (sign-extended)");
        assertEquals(-8388607.0 / 8388608.0, above, 0.0);
    }

    // ---------------------------------------------------------------------
    // PCM32 signed — boundaries
    // ---------------------------------------------------------------------

    /**
     * {@code Integer.MIN_VALUE} (bytes {@code {0x00, 0x00, 0x00, 0x80}}
     * little-endian) normalises to exactly {@code -1.0} because
     * {@code -2^31 / 2^31 == -1.0} is an exact IEEE-754 result.
     */
    @Test
    @DisplayName("PCM32 LE: Integer.MIN_VALUE bytes decode to -1.0 exactly")
    void pcm32LeMinValueIsExactlyNegativeOne() {
        byte[] bytes = {0x00, 0x00, 0x00, (byte) 0x80};
        double actual = SampleConversion.decodePcm32LE(bytes, 0);
        assertEquals(-1.0, actual, 0.0,
                "Integer.MIN_VALUE (-2^31) / 2^31 must be exactly -1.0");
    }

    /**
     * {@code Integer.MAX_VALUE} decodes to
     * {@code (2^31 - 1) / 2^31}, which is exactly representable (the
     * division is already in double precision with a 53-bit mantissa, so
     * {@code 2147483647.0 / 2147483648.0} rounds to the nearest double).
     */
    @Test
    @DisplayName("PCM32 LE: Integer.MAX_VALUE bytes decode just below 1.0")
    void pcm32LeMaxValueJustBelowOne() {
        byte[] bytes = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x7F};
        double actual = SampleConversion.decodePcm32LE(bytes, 0);
        double expected = 2147483647.0 / 2147483648.0;
        assertEquals(expected, actual, 0.0,
                "Integer.MAX_VALUE / 2^31 must equal (2^31 - 1) / 2^31 exactly");
        assertTrue(actual < 1.0, "Expected value strictly below 1.0");
    }

    /** Big-endian mirror of the {@code Integer.MIN_VALUE} boundary. */
    @Test
    @DisplayName("PCM32 BE: Integer.MIN_VALUE bytes decode to -1.0 exactly")
    void pcm32BeMinValueIsExactlyNegativeOne() {
        byte[] bytes = {(byte) 0x80, 0x00, 0x00, 0x00};
        double actual = SampleConversion.decodePcm32BE(bytes, 0);
        assertEquals(-1.0, actual, 0.0,
                "PCM32 BE Integer.MIN_VALUE bytes must decode to -1.0 exactly");
    }

    /** Zero bytes decode to exactly {@code 0.0} for both endiannesses. */
    @Test
    @DisplayName("PCM32: zero bytes decode to 0.0 for both endiannesses")
    void pcm32ZeroDecodesToZero() {
        byte[] bytes = {0x00, 0x00, 0x00, 0x00};
        assertEquals(0.0, SampleConversion.decodePcm32LE(bytes, 0), 0.0);
        assertEquals(0.0, SampleConversion.decodePcm32BE(bytes, 0), 0.0);
    }

    // ---------------------------------------------------------------------
    // PCM64 signed — boundaries
    // ---------------------------------------------------------------------

    /**
     * {@code Long.MIN_VALUE} normalises to exactly {@code -1.0} because
     * {@code -2^63 / 2^63 == -1.0} is exact in IEEE-754 double precision.
     */
    @Test
    @DisplayName("PCM64 LE: Long.MIN_VALUE bytes decode to -1.0 exactly")
    void pcm64LeMinValueIsExactlyNegativeOne() {
        byte[] bytes = {0, 0, 0, 0, 0, 0, 0, (byte) 0x80};
        double actual = SampleConversion.decodePcm64LE(bytes, 0);
        assertEquals(-1.0, actual, 0.0,
                "Long.MIN_VALUE (-2^63) / 2^63 must be exactly -1.0");
    }

    /** Zero bytes decode to exactly {@code 0.0} at 64-bit. */
    @Test
    @DisplayName("PCM64: zero bytes decode to 0.0 for both endiannesses")
    void pcm64ZeroDecodesToZero() {
        byte[] bytes = {0, 0, 0, 0, 0, 0, 0, 0};
        assertEquals(0.0, SampleConversion.decodePcm64LE(bytes, 0), 0.0);
        assertEquals(0.0, SampleConversion.decodePcm64BE(bytes, 0), 0.0);
    }

    // ---------------------------------------------------------------------
    // Unsigned integer boundaries
    // ---------------------------------------------------------------------

    /**
     * Unsigned PCM's midpoint {@code 2^(bitDepth - 1)} represents silence
     * ({@code 0.0}); the minimum {@code 0} represents {@code -1.0}; the
     * maximum {@code 2^bitDepth - 1} represents a value just below
     * {@code +1.0}. These three points are the universal boundary set for
     * unsigned PCM.
     */
    @Test
    @DisplayName("Unsigned PCM16 LE: midpoint is 0.0, min is -1.0, max is just below 1.0")
    void unsignedPcm16Boundaries() {
        // Unsigned 0 → -1.0
        double zero = SampleConversion.decodeUnsignedPcm16LE(new byte[] {0x00, 0x00}, 0);
        assertEquals(-1.0, zero, 0.0,
                "Unsigned PCM16 min (0) must decode to -1.0 exactly");

        // Unsigned 0x8000 (midpoint = 32768) → 0.0
        double mid = SampleConversion.decodeUnsignedPcm16LE(new byte[] {0x00, (byte) 0x80}, 0);
        assertEquals(0.0, mid, 0.0,
                "Unsigned PCM16 midpoint (2^15) must decode to 0.0 exactly");

        // Unsigned 0xFFFF (max = 65535) → (65535 - 32768) / 32768 = 32767/32768
        double max = SampleConversion.decodeUnsignedPcm16LE(new byte[] {(byte) 0xFF, (byte) 0xFF}, 0);
        assertEquals(32767.0 / 32768.0, max, 0.0,
                "Unsigned PCM16 max (2^16 - 1) must decode to (2^15 - 1) / 2^15 exactly");
    }

    // ---------------------------------------------------------------------
    // IEEE float — boundaries
    // ---------------------------------------------------------------------

    /**
     * IEEE float samples widen without scaling, so {@code 1.0f} decodes
     * bit-exact to {@code 1.0d} and {@code -1.0f} decodes bit-exact to
     * {@code -1.0d}.
     */
    @Test
    @DisplayName("Float32 LE: 1.0f decodes to 1.0, -1.0f decodes to -1.0")
    void float32BoundariesWidenWithoutScaling() {
        // 1.0f has IEEE-754 representation 0x3F800000.
        byte[] oneBytes = intToLeBytes(Float.floatToIntBits(1.0f));
        assertEquals(1.0, SampleConversion.decodeFloat32LE(oneBytes, 0), 0.0,
                "Float32 LE 1.0f must widen to 1.0 without scaling");

        byte[] negOneBytes = intToLeBytes(Float.floatToIntBits(-1.0f));
        assertEquals(-1.0, SampleConversion.decodeFloat32LE(negOneBytes, 0), 0.0,
                "Float32 LE -1.0f must widen to -1.0 without scaling");

        byte[] zeroBytes = intToLeBytes(Float.floatToIntBits(0.0f));
        assertEquals(0.0, SampleConversion.decodeFloat32LE(zeroBytes, 0), 0.0,
                "Float32 LE 0.0f must widen to 0.0");
    }

    /** Float64 decodes bit-exact to the same double. */
    @Test
    @DisplayName("Float64 LE: 1.0 and -1.0 round-trip bit-exact")
    void float64BoundariesRoundTrip() {
        byte[] oneBytes = longToLeBytes(Double.doubleToLongBits(1.0));
        assertEquals(1.0, SampleConversion.decodeFloat64LE(oneBytes, 0), 0.0);

        byte[] negOneBytes = longToLeBytes(Double.doubleToLongBits(-1.0));
        assertEquals(-1.0, SampleConversion.decodeFloat64LE(negOneBytes, 0), 0.0);
    }

    // ---------------------------------------------------------------------
    // Endianness swap
    // ---------------------------------------------------------------------

    /**
     * Endianness swap: the same non-palindromic 3-byte sequence decoded
     * little-endian versus big-endian produces different values, each
     * matching the per-byte-order formula.
     *
     * <p>Using bytes {@code {0x12, 0x34, 0x56}}:
     * <ul>
     *   <li>LE assembles to {@code 0x563412 = 5,649,426}; that's positive
     *       (bit 23 clear), so no sign extension, and it normalises to
     *       {@code 5649426 / 2^23}.</li>
     *   <li>BE assembles to {@code 0x123456 = 1,193,046}; normalises to
     *       {@code 1193046 / 2^23}.</li>
     * </ul>
     * The two values must differ — that's the core endianness contract.
     */
    @Test
    @DisplayName("PCM24: LE vs BE on {0x12, 0x34, 0x56} produce different, correctly-computed values")
    void pcm24EndiannessSwapProducesDifferentValues() {
        byte[] bytes = {0x12, 0x34, 0x56};

        double le = SampleConversion.decodePcm24LE(bytes, 0);
        double be = SampleConversion.decodePcm24BE(bytes, 0);

        // Contract: the two values must differ for a non-palindromic input.
        assertNotEquals(le, be,
                "PCM24 LE and BE decodes of a non-palindromic byte sequence must differ");

        // Additionally pin each side to the exact expected value so the
        // test doesn't silently accept "different, but both wrong".
        // LE assembly: low byte first.
        int leInt = (0x12) | (0x34 << 8) | (0x56 << 16); // 0x563412
        assertEquals(leInt / 8388608.0, le, 0.0,
                "PCM24 LE must assemble low byte first (0x563412)");

        // BE assembly: high byte first.
        int beInt = (0x12 << 16) | (0x34 << 8) | 0x56;   // 0x123456
        assertEquals(beInt / 8388608.0, be, 0.0,
                "PCM24 BE must assemble high byte first (0x123456)");
    }

    /**
     * A palindromic byte sequence (where swapping byte order produces the
     * same value) acts as a sanity check that the test's notion of
     * endianness swap is meaningful: LE and BE agree here because the
     * swap is a no-op.
     */
    @Test
    @DisplayName("PCM24: LE vs BE on a palindromic sequence agree (sanity check)")
    void pcm24PalindromicEndiannessSwapAgrees() {
        byte[] bytes = {0x12, 0x34, 0x12};
        assertEquals(
                SampleConversion.decodePcm24LE(bytes, 0),
                SampleConversion.decodePcm24BE(bytes, 0),
                0.0,
                "PCM24 LE and BE should agree on a palindromic byte sequence");
    }

    // ---------------------------------------------------------------------
    // Dispatcher — decoderFor(...)
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("decoderFor dispatcher")
    class DecoderForDispatcherTests {

        /**
         * The dispatcher MUST return a decoder that matches the direct
         * {@code decodePcm16LE} static method. Sampling one canonical tuple
         * with the {@code Short.MIN_VALUE} boundary is enough to prove
         * correct wiring — the per-tuple math is already covered above.
         */
        @Test
        @DisplayName("decoderFor returns matching decoders for every supported signed-int tuple")
        void signedIntTuples() {
            byte[] pcm16Le = {0x00, (byte) 0x80};
            SampleDecoder d16le = SampleConversion.decoderFor(16, ByteOrder.LITTLE_ENDIAN, PcmEncoding.SIGNED_INT);
            assertNotNull(d16le, "decoderFor(16, LE, SIGNED_INT) must not return null");
            assertEquals(SampleConversion.decodePcm16LE(pcm16Le, 0), d16le.decode(pcm16Le, 0), 0.0);

            byte[] pcm16Be = {(byte) 0x80, 0x00};
            SampleDecoder d16be = SampleConversion.decoderFor(16, ByteOrder.BIG_ENDIAN, PcmEncoding.SIGNED_INT);
            assertEquals(SampleConversion.decodePcm16BE(pcm16Be, 0), d16be.decode(pcm16Be, 0), 0.0);

            byte[] pcm24 = {0x00, 0x00, (byte) 0x80};
            SampleDecoder d24le = SampleConversion.decoderFor(24, ByteOrder.LITTLE_ENDIAN, PcmEncoding.SIGNED_INT);
            assertEquals(SampleConversion.decodePcm24LE(pcm24, 0), d24le.decode(pcm24, 0), 0.0);

            byte[] pcm24Be = {(byte) 0x80, 0x00, 0x00};
            SampleDecoder d24be = SampleConversion.decoderFor(24, ByteOrder.BIG_ENDIAN, PcmEncoding.SIGNED_INT);
            assertEquals(SampleConversion.decodePcm24BE(pcm24Be, 0), d24be.decode(pcm24Be, 0), 0.0);

            byte[] pcm32Le = {0x00, 0x00, 0x00, (byte) 0x80};
            SampleDecoder d32le = SampleConversion.decoderFor(32, ByteOrder.LITTLE_ENDIAN, PcmEncoding.SIGNED_INT);
            assertEquals(SampleConversion.decodePcm32LE(pcm32Le, 0), d32le.decode(pcm32Le, 0), 0.0);

            byte[] pcm32Be = {(byte) 0x80, 0x00, 0x00, 0x00};
            SampleDecoder d32be = SampleConversion.decoderFor(32, ByteOrder.BIG_ENDIAN, PcmEncoding.SIGNED_INT);
            assertEquals(SampleConversion.decodePcm32BE(pcm32Be, 0), d32be.decode(pcm32Be, 0), 0.0);

            byte[] pcm64Le = {0, 0, 0, 0, 0, 0, 0, (byte) 0x80};
            SampleDecoder d64le = SampleConversion.decoderFor(64, ByteOrder.LITTLE_ENDIAN, PcmEncoding.SIGNED_INT);
            assertEquals(SampleConversion.decodePcm64LE(pcm64Le, 0), d64le.decode(pcm64Le, 0), 0.0);

            byte[] pcm64Be = {(byte) 0x80, 0, 0, 0, 0, 0, 0, 0};
            SampleDecoder d64be = SampleConversion.decoderFor(64, ByteOrder.BIG_ENDIAN, PcmEncoding.SIGNED_INT);
            assertEquals(SampleConversion.decodePcm64BE(pcm64Be, 0), d64be.decode(pcm64Be, 0), 0.0);
        }

        /** Same check for every supported unsigned-int tuple. */
        @Test
        @DisplayName("decoderFor returns matching decoders for every supported unsigned-int tuple")
        void unsignedIntTuples() {
            byte[] bytes2 = {(byte) 0xFF, (byte) 0xFF};
            assertEquals(
                    SampleConversion.decodeUnsignedPcm16LE(bytes2, 0),
                    SampleConversion.decoderFor(16, ByteOrder.LITTLE_ENDIAN, PcmEncoding.UNSIGNED_INT).decode(bytes2, 0),
                    0.0);
            assertEquals(
                    SampleConversion.decodeUnsignedPcm16BE(bytes2, 0),
                    SampleConversion.decoderFor(16, ByteOrder.BIG_ENDIAN, PcmEncoding.UNSIGNED_INT).decode(bytes2, 0),
                    0.0);

            byte[] bytes3 = {0x12, 0x34, 0x56};
            assertEquals(
                    SampleConversion.decodeUnsignedPcm24LE(bytes3, 0),
                    SampleConversion.decoderFor(24, ByteOrder.LITTLE_ENDIAN, PcmEncoding.UNSIGNED_INT).decode(bytes3, 0),
                    0.0);
            assertEquals(
                    SampleConversion.decodeUnsignedPcm24BE(bytes3, 0),
                    SampleConversion.decoderFor(24, ByteOrder.BIG_ENDIAN, PcmEncoding.UNSIGNED_INT).decode(bytes3, 0),
                    0.0);

            byte[] bytes4 = {0x12, 0x34, 0x56, 0x78};
            assertEquals(
                    SampleConversion.decodeUnsignedPcm32LE(bytes4, 0),
                    SampleConversion.decoderFor(32, ByteOrder.LITTLE_ENDIAN, PcmEncoding.UNSIGNED_INT).decode(bytes4, 0),
                    0.0);
            assertEquals(
                    SampleConversion.decodeUnsignedPcm32BE(bytes4, 0),
                    SampleConversion.decoderFor(32, ByteOrder.BIG_ENDIAN, PcmEncoding.UNSIGNED_INT).decode(bytes4, 0),
                    0.0);

            byte[] bytes8 = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
            assertEquals(
                    SampleConversion.decodeUnsignedPcm64LE(bytes8, 0),
                    SampleConversion.decoderFor(64, ByteOrder.LITTLE_ENDIAN, PcmEncoding.UNSIGNED_INT).decode(bytes8, 0),
                    0.0);
            assertEquals(
                    SampleConversion.decodeUnsignedPcm64BE(bytes8, 0),
                    SampleConversion.decoderFor(64, ByteOrder.BIG_ENDIAN, PcmEncoding.UNSIGNED_INT).decode(bytes8, 0),
                    0.0);
        }

        /** Same check for every supported IEEE-float tuple. */
        @Test
        @DisplayName("decoderFor returns matching decoders for every supported IEEE_FLOAT tuple")
        void ieeeFloatTuples() {
            byte[] f32 = intToLeBytes(Float.floatToIntBits(0.5f));
            assertEquals(
                    SampleConversion.decodeFloat32LE(f32, 0),
                    SampleConversion.decoderFor(32, ByteOrder.LITTLE_ENDIAN, PcmEncoding.IEEE_FLOAT).decode(f32, 0),
                    0.0);

            byte[] f32Be = intToBeBytes(Float.floatToIntBits(0.5f));
            assertEquals(
                    SampleConversion.decodeFloat32BE(f32Be, 0),
                    SampleConversion.decoderFor(32, ByteOrder.BIG_ENDIAN, PcmEncoding.IEEE_FLOAT).decode(f32Be, 0),
                    0.0);

            byte[] f64 = longToLeBytes(Double.doubleToLongBits(0.5));
            assertEquals(
                    SampleConversion.decodeFloat64LE(f64, 0),
                    SampleConversion.decoderFor(64, ByteOrder.LITTLE_ENDIAN, PcmEncoding.IEEE_FLOAT).decode(f64, 0),
                    0.0);

            byte[] f64Be = longToBeBytes(Double.doubleToLongBits(0.5));
            assertEquals(
                    SampleConversion.decodeFloat64BE(f64Be, 0),
                    SampleConversion.decoderFor(64, ByteOrder.BIG_ENDIAN, PcmEncoding.IEEE_FLOAT).decode(f64Be, 0),
                    0.0);
        }

        /**
         * Unsupported tuples (wrong bit depth for an encoding) SHALL throw
         * {@link IllegalArgumentException} with a message that names the
         * offending tuple. The {@code IEEE_FLOAT}/{@code 16-bit}
         * combination is the canonical example: float PCM only exists at
         * 32 and 64 bits.
         */
        @Test
        @DisplayName("decoderFor(IEEE_FLOAT, 16 bits) throws IllegalArgumentException naming the tuple")
        void ieeeFloatAtUnsupportedBitDepthThrows() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> SampleConversion.decoderFor(16, ByteOrder.LITTLE_ENDIAN, PcmEncoding.IEEE_FLOAT));
            assertTrue(ex.getMessage().contains("16"), "Message should name the bit depth");
            assertTrue(ex.getMessage().contains("IEEE_FLOAT"), "Message should name the encoding");
        }

        /** Unknown signed bit depth (e.g., 8 or 48) is rejected. */
        @Test
        @DisplayName("decoderFor rejects signed-int 8-bit with IllegalArgumentException")
        void signedIntAtUnsupportedBitDepthThrows() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> SampleConversion.decoderFor(8, ByteOrder.LITTLE_ENDIAN, PcmEncoding.SIGNED_INT));
            assertTrue(ex.getMessage().contains("8"), "Message should name the bit depth");
        }

        /** Unknown unsigned bit depth is rejected. */
        @Test
        @DisplayName("decoderFor rejects unsigned-int 48-bit with IllegalArgumentException")
        void unsignedIntAtUnsupportedBitDepthThrows() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> SampleConversion.decoderFor(48, ByteOrder.LITTLE_ENDIAN, PcmEncoding.UNSIGNED_INT));
            assertTrue(ex.getMessage().contains("48"), "Message should name the bit depth");
        }

        /**
         * {@code null} byte order throws {@link NullPointerException}
         * naming the parameter — the dispatcher validates inputs before
         * dereferencing them.
         */
        @Test
        @DisplayName("decoderFor(null order, ...) throws NullPointerException")
        void nullByteOrderThrows() {
            assertThrows(NullPointerException.class,
                    () -> SampleConversion.decoderFor(16, null, PcmEncoding.SIGNED_INT));
        }

        /** {@code null} encoding throws {@link NullPointerException}. */
        @Test
        @DisplayName("decoderFor(..., null encoding) throws NullPointerException")
        void nullEncodingThrows() {
            assertThrows(NullPointerException.class,
                    () -> SampleConversion.decoderFor(16, ByteOrder.LITTLE_ENDIAN, null));
        }
    }

    // ---------------------------------------------------------------------
    // Test helpers
    // ---------------------------------------------------------------------

    /** Pack an {@code int} into a 4-byte little-endian array. */
    private static byte[] intToLeBytes(int v) {
        return new byte[] {
                (byte) (v & 0xFF),
                (byte) ((v >>> 8) & 0xFF),
                (byte) ((v >>> 16) & 0xFF),
                (byte) ((v >>> 24) & 0xFF)
        };
    }

    /** Pack an {@code int} into a 4-byte big-endian array. */
    private static byte[] intToBeBytes(int v) {
        return new byte[] {
                (byte) ((v >>> 24) & 0xFF),
                (byte) ((v >>> 16) & 0xFF),
                (byte) ((v >>> 8) & 0xFF),
                (byte) (v & 0xFF)
        };
    }

    /** Pack a {@code long} into an 8-byte little-endian array. */
    private static byte[] longToLeBytes(long v) {
        return new byte[] {
                (byte) (v & 0xFFL),
                (byte) ((v >>> 8) & 0xFFL),
                (byte) ((v >>> 16) & 0xFFL),
                (byte) ((v >>> 24) & 0xFFL),
                (byte) ((v >>> 32) & 0xFFL),
                (byte) ((v >>> 40) & 0xFFL),
                (byte) ((v >>> 48) & 0xFFL),
                (byte) ((v >>> 56) & 0xFFL)
        };
    }

    /** Pack a {@code long} into an 8-byte big-endian array. */
    private static byte[] longToBeBytes(long v) {
        return new byte[] {
                (byte) ((v >>> 56) & 0xFFL),
                (byte) ((v >>> 48) & 0xFFL),
                (byte) ((v >>> 40) & 0xFFL),
                (byte) ((v >>> 32) & 0xFFL),
                (byte) ((v >>> 24) & 0xFFL),
                (byte) ((v >>> 16) & 0xFFL),
                (byte) ((v >>> 8) & 0xFFL),
                (byte) (v & 0xFFL)
        };
    }
}
