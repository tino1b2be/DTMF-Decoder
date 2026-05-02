package com.tino1b2be.dtmf.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SampleConverter}.
 *
 * <p>Covers boundary values for each input format, the sign-extension corners
 * for packed PCM24, the null-input contract for both allocating and
 * {@code *Into} variants, and the destination-too-short contract for the
 * {@code *Into} variants.
 *
 * <p>Every conversion formula asserted here is the exact formula from
 * {@code design.md} (Requirements 4.5, 4.6, 4.7) — there is no fuzz or
 * tolerance, the outputs are expected to match the divisor math bit-exactly.
 */
class SampleConverterTest {

    // 2^15, 2^23, 2^31 — locally mirrored for clarity in the assertions below.
    private static final double PCM16_DIVISOR = 32768.0;
    private static final double PCM24_DIVISOR = 8388608.0;
    private static final double PCM32_DIVISOR = 2147483648.0;

    @Nested
    class FromShort {

        @Test
        void emptyArrayProducesEmptyResult() {
            assertArrayEquals(new double[0], SampleConverter.fromShort(new short[0]));
        }

        @Test
        void boundaryValuesDivideBy32768Exactly() {
            short[] src = {
                    Short.MIN_VALUE,       // -32768 / 32768  = -1.0 exactly
                    Short.MAX_VALUE,       //  32767 / 32768  = 0.99996948...
                    0,
                    (short) 1,
                    (short) -1,
                    (short) 16384,         //  16384 / 32768  =  0.5 exactly
                    (short) -16384         // -16384 / 32768  = -0.5 exactly
            };
            double[] expected = {
                    -1.0,
                    32767.0 / PCM16_DIVISOR,
                    0.0,
                    1.0 / PCM16_DIVISOR,
                    -1.0 / PCM16_DIVISOR,
                    0.5,
                    -0.5
            };
            assertArrayEquals(expected, SampleConverter.fromShort(src));
        }

        @Test
        void shortMinValueMapsToNegativeOneExactly() {
            double[] out = SampleConverter.fromShort(new short[]{Short.MIN_VALUE});
            assertEquals(-1.0, out[0]);
        }

        @Test
        void shortMaxValueDoesNotReachOneByDesign() {
            double out = SampleConverter.fromShort(new short[]{Short.MAX_VALUE})[0];
            // Divisor is 2^15 = 32768, not 32767. Max positive value is just
            // short of 1.0 so that MIN_VALUE → -1.0 is exact and symmetric.
            assertEquals(32767.0 / PCM16_DIVISOR, out);
            assertTrue(out < 1.0);
        }

        @Test
        void nullInputThrowsNpeWithParameterName() {
            NullPointerException e = assertThrows(NullPointerException.class,
                    () -> SampleConverter.fromShort(null));
            assertEquals("src", e.getMessage());
        }
    }

    @Nested
    class FromFloat {

        @Test
        void emptyArrayProducesEmptyResult() {
            assertArrayEquals(new double[0], SampleConverter.fromFloat(new float[0]));
        }

        @Test
        void boundaryValuesWidenWithoutScaling() {
            float[] src = {-1.0f, 0.0f, 1.0f, 0.5f, -0.5f};
            double[] expected = {
                    (double) -1.0f,
                    (double) 0.0f,
                    (double) 1.0f,
                    (double) 0.5f,
                    (double) -0.5f
            };
            assertArrayEquals(expected, SampleConverter.fromFloat(src));
        }

        @Test
        void naNAndInfinityAreWidenedAsIs() {
            float[] src = {
                    Float.NaN,
                    Float.POSITIVE_INFINITY,
                    Float.NEGATIVE_INFINITY
            };
            double[] out = SampleConverter.fromFloat(src);
            // Compare the raw bit patterns so NaN-equals-NaN holds. Also
            // confirms the NaN payload is preserved by the widening conversion.
            assertEquals(Double.doubleToRawLongBits((double) Float.NaN),
                    Double.doubleToRawLongBits(out[0]));
            assertEquals(Double.POSITIVE_INFINITY, out[1]);
            assertEquals(Double.NEGATIVE_INFINITY, out[2]);
        }

        @Test
        void nullInputThrowsNpeWithParameterName() {
            NullPointerException e = assertThrows(NullPointerException.class,
                    () -> SampleConverter.fromFloat(null));
            assertEquals("src", e.getMessage());
        }
    }

    @Nested
    class FromInt {

        @Test
        void emptyArrayProducesEmptyResult() {
            assertArrayEquals(new double[0], SampleConverter.fromInt(new int[0]));
        }

        @Test
        void integerMinValueMapsToNegativeOneExactly() {
            double[] out = SampleConverter.fromInt(new int[]{Integer.MIN_VALUE});
            // Divisor is 2^31, not 2^31 - 1. Integer.MIN_VALUE / 2^31 == -1.0
            // exactly in double precision.
            assertEquals(-1.0, out[0]);
        }

        @Test
        void integerMaxValueDoesNotReachOne() {
            double out = SampleConverter.fromInt(new int[]{Integer.MAX_VALUE})[0];
            assertEquals(Integer.MAX_VALUE / PCM32_DIVISOR, out);
            assertTrue(out < 1.0);
        }

        @Test
        void boundaryValuesDivideBy2Pow31Exactly() {
            int[] src = {
                    Integer.MIN_VALUE,
                    Integer.MAX_VALUE,
                    0,
                    1,
                    -1
            };
            double[] expected = {
                    -1.0,
                    Integer.MAX_VALUE / PCM32_DIVISOR,
                    0.0,
                    1.0 / PCM32_DIVISOR,
                    -1.0 / PCM32_DIVISOR
            };
            assertArrayEquals(expected, SampleConverter.fromInt(src));
        }

        @Test
        void nullInputThrowsNpeWithParameterName() {
            NullPointerException e = assertThrows(NullPointerException.class,
                    () -> SampleConverter.fromInt(null));
            assertEquals("src", e.getMessage());
        }
    }

    @Nested
    class FromPcm24 {

        @Test
        void emptyArrayProducesEmptyResult() {
            assertArrayEquals(new double[0], SampleConverter.fromPcm24(new int[0]));
        }

        @Test
        void maxPositive24BitMapsToJustBelowOne() {
            // 0x7FFFFF = 8388607, which is the max positive signed 24-bit
            // value. After sign-extension it stays 8388607 and divides by
            // 2^23 = 8388608 to give 8388607/8388608 ≈ 0.99999988.
            double out = SampleConverter.fromPcm24(new int[]{0x7FFFFF})[0];
            assertEquals(8388607.0 / PCM24_DIVISOR, out);
            assertTrue(out < 1.0);
        }

        @Test
        void minNegative24BitMapsToNegativeOneExactly() {
            // 0x800000 has bit 23 set, so after sign-extension it becomes
            // -8388608 and divides by 2^23 to give exactly -1.0.
            double out = SampleConverter.fromPcm24(new int[]{0x800000})[0];
            assertEquals(-1.0, out);
        }

        @Test
        void zeroMapsToZero() {
            double[] out = SampleConverter.fromPcm24(new int[]{0});
            assertEquals(0.0, out[0]);
        }

        @Test
        void allOnes24BitMapsToNegativeOneOverDivisor() {
            // 0xFFFFFF has all 24 bits set → signed -1 after extension,
            // divided by 2^23 = -1/8388608.
            double out = SampleConverter.fromPcm24(new int[]{0xFFFFFF})[0];
            assertEquals(-1.0 / PCM24_DIVISOR, out);
        }

        @Test
        void highBitsAboveBit23AreIgnoredViaSignExtension() {
            // The PCM24 helper is documented to carry the signed 24-bit
            // value in the low 24 bits of each int. Bits above 23 should be
            // discarded by the sign-extension shift. Verify by injecting
            // garbage into the high byte and confirming the output matches
            // the low-24-bit interpretation.
            int packedMinusOne = 0x12FFFFFF;             // low 24 bits = all ones = -1
            int packedPlusOne = 0xAB000001;              // low 24 bits = 1
            double[] out = SampleConverter.fromPcm24(new int[]{packedMinusOne, packedPlusOne});
            assertEquals(-1.0 / PCM24_DIVISOR, out[0]);
            assertEquals(1.0 / PCM24_DIVISOR, out[1]);
        }

        @Test
        void nullInputThrowsNpeWithParameterName() {
            NullPointerException e = assertThrows(NullPointerException.class,
                    () -> SampleConverter.fromPcm24(null));
            assertEquals("src", e.getMessage());
        }
    }

    @Nested
    class FromShortInto {

        @Test
        void writesSamplesIntoProvidedBuffer() {
            short[] src = {(short) -16384, 0, (short) 16384};
            double[] dst = new double[3];
            SampleConverter.fromShortInto(src, dst);
            assertArrayEquals(new double[]{-0.5, 0.0, 0.5}, dst);
        }

        @Test
        void acceptsLargerDestination() {
            short[] src = {(short) 16384};
            double[] dst = new double[4];        // oversized — valid
            dst[1] = 42.0;                        // sentinel past source length
            SampleConverter.fromShortInto(src, dst);
            assertEquals(0.5, dst[0]);
            // Only indices [0, src.length) are written.
            assertEquals(42.0, dst[1]);
        }

        @Test
        void nullSrcThrowsNpeWithParameterName() {
            NullPointerException e = assertThrows(NullPointerException.class,
                    () -> SampleConverter.fromShortInto(null, new double[1]));
            assertEquals("src", e.getMessage());
        }

        @Test
        void nullDstThrowsNpeWithParameterName() {
            NullPointerException e = assertThrows(NullPointerException.class,
                    () -> SampleConverter.fromShortInto(new short[1], null));
            assertEquals("dst", e.getMessage());
        }

        @Test
        void tooShortDestinationThrowsIae() {
            short[] src = new short[5];
            double[] dst = new double[4];
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> SampleConverter.fromShortInto(src, dst));
            assertTrue(e.getMessage().contains("4"), e.getMessage());
            assertTrue(e.getMessage().contains("5"), e.getMessage());
        }
    }

    @Nested
    class FromFloatInto {

        @Test
        void writesSamplesIntoProvidedBuffer() {
            float[] src = {-0.25f, 0.0f, 0.25f};
            double[] dst = new double[3];
            SampleConverter.fromFloatInto(src, dst);
            assertArrayEquals(new double[]{(double) -0.25f, 0.0, (double) 0.25f}, dst);
        }

        @Test
        void preservesInfinityAndNaN() {
            float[] src = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
            double[] dst = new double[3];
            SampleConverter.fromFloatInto(src, dst);
            assertEquals(Double.doubleToRawLongBits((double) Float.NaN),
                    Double.doubleToRawLongBits(dst[0]));
            assertEquals(Double.POSITIVE_INFINITY, dst[1]);
            assertEquals(Double.NEGATIVE_INFINITY, dst[2]);
        }

        @Test
        void nullSrcThrowsNpeWithParameterName() {
            NullPointerException e = assertThrows(NullPointerException.class,
                    () -> SampleConverter.fromFloatInto(null, new double[1]));
            assertEquals("src", e.getMessage());
        }

        @Test
        void nullDstThrowsNpeWithParameterName() {
            NullPointerException e = assertThrows(NullPointerException.class,
                    () -> SampleConverter.fromFloatInto(new float[1], null));
            assertEquals("dst", e.getMessage());
        }

        @Test
        void tooShortDestinationThrowsIae() {
            float[] src = new float[3];
            double[] dst = new double[2];
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> SampleConverter.fromFloatInto(src, dst));
            assertTrue(e.getMessage().contains("2"), e.getMessage());
            assertTrue(e.getMessage().contains("3"), e.getMessage());
        }
    }

    @Nested
    class FromIntInto {

        @Test
        void writesSamplesIntoProvidedBuffer() {
            int[] src = {Integer.MIN_VALUE, 0, Integer.MAX_VALUE};
            double[] dst = new double[3];
            SampleConverter.fromIntInto(src, dst);
            assertArrayEquals(
                    new double[]{-1.0, 0.0, Integer.MAX_VALUE / PCM32_DIVISOR}, dst);
        }

        @Test
        void nullSrcThrowsNpeWithParameterName() {
            NullPointerException e = assertThrows(NullPointerException.class,
                    () -> SampleConverter.fromIntInto(null, new double[1]));
            assertEquals("src", e.getMessage());
        }

        @Test
        void nullDstThrowsNpeWithParameterName() {
            NullPointerException e = assertThrows(NullPointerException.class,
                    () -> SampleConverter.fromIntInto(new int[1], null));
            assertEquals("dst", e.getMessage());
        }

        @Test
        void tooShortDestinationThrowsIae() {
            int[] src = new int[10];
            double[] dst = new double[0];
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> SampleConverter.fromIntInto(src, dst));
            assertTrue(e.getMessage().contains("0"), e.getMessage());
            assertTrue(e.getMessage().contains("10"), e.getMessage());
        }
    }
}
