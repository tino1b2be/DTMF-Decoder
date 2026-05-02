package com.tino1b2be.dtmf.io;

// Feature: dtmf-io, Property 3: RawPcmAudioSource sample normalization

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

/**
 * Property-based test for {@link RawPcmAudioSource} sample normalisation.
 *
 * <p><strong>Property 3: {@code RawPcmAudioSource} sample
 * normalisation.</strong> <strong>Validates: Requirements 7.12, 9.14,
 * 3.6.</strong>
 *
 * <p>For any supported PCM tuple
 * {@code (bitDepth, byteOrder, encoding)} from the set
 * {@link com.tino1b2be.dtmf.io.internal.SampleConversion#decoderFor(int,
 * ByteOrder, PcmEncoding)} accepts, for any sample rate in
 * {@code [1, 384000]}, any channel count in {@code [1, 8]}, and any raw
 * byte buffer trimmed to a whole-frame length, every {@code double}
 * produced by {@link RawPcmAudioSource#read(double[], int, int)} must
 * match an independently computed reference hand-decode of the same
 * bytes at the same offset, to within {@code 1e-12}.
 *
 * <p>The reference decoder in this test file deliberately does not call
 * into {@link com.tino1b2be.dtmf.io.internal.SampleConversion}: it
 * assembles multi-byte values using plain bit shifts (for signed/unsigned
 * integer PCM) and {@link ByteBuffer} reads (for IEEE floats), then
 * applies the normalisation formulas from the
 * {@code SampleConversion} Javadoc. That way the property can catch a
 * drift between the production decoder and the documented contract;
 * testing {@code SampleConversion} against itself would just be
 * reflexivity.
 */
class RawPcmAudioSourcePropertyTest {

    /** Tolerance for the per-sample comparison. */
    private static final double EPSILON = 1e-12;

    /** PCM configuration: bit depth, byte order, and numeric encoding. */
    record PcmTuple(int bitDepth, ByteOrder byteOrder, PcmEncoding encoding) { }

    @Property(tries = 100)
    void rawPcmNormalizesSamplesCorrectly(
            @ForAll("pcmTuples") PcmTuple tuple,
            @ForAll @IntRange(min = 1, max = 384_000) int sampleRate,
            @ForAll @IntRange(min = 1, max = 8) int channelCount,
            @ForAll @Size(min = 1, max = 4096) byte[] rawBytes) throws IOException {

        int bytesPerSample = tuple.bitDepth() / 8;
        int bytesPerFrame = bytesPerSample * channelCount;

        // Trim to a whole-frame length; guarantee at least one frame so
        // the assertion loop is non-empty. If the randomly drawn buffer
        // is shorter than one frame, pad up to one frame deterministically
        // (we still cover many byte patterns because @Size yields wide
        // variety; the padding just rescues the rare short-draw case).
        byte[] data = trimOrPadToFrameBoundary(rawBytes, bytesPerFrame);
        int totalFrames = data.length / bytesPerFrame;

        RawPcmAudioSource source = new RawPcmAudioSource(
                data, sampleRate,
                tuple.bitDepth(), tuple.byteOrder(),
                channelCount, tuple.encoding());

        try {
            assertEquals(totalFrames, source.totalFrames(),
                    "totalFrames() must equal data.length / bytesPerFrame");
            assertEquals(sampleRate, source.sampleRate());
            assertEquals(channelCount, source.channelCount());
            assertEquals(tuple.bitDepth(), source.bitDepth());

            double[] buffer = new double[totalFrames * channelCount];
            int framesRead = source.read(buffer, 0, totalFrames);
            assertEquals(totalFrames, framesRead,
                    "read(...) must return every frame in one call when"
                            + " buffer is large enough");

            for (int frame = 0; frame < totalFrames; frame++) {
                for (int channel = 0; channel < channelCount; channel++) {
                    int sampleByteOffset =
                            frame * bytesPerFrame + channel * bytesPerSample;
                    double expected = referenceDecode(data, sampleByteOffset, tuple);
                    double actual = buffer[frame * channelCount + channel];
                    final int f = frame;
                    final int c = channel;
                    assertTrue(
                            closeEnough(actual, expected),
                            () -> "Sample mismatch at frame=" + f
                                    + ", channel=" + c
                                    + ", tuple=" + tuple
                                    + ": expected=" + expected
                                    + ", actual=" + actual
                                    + ", delta=" + (actual - expected));
                }
            }
        } finally {
            source.close();
        }
    }

    // ------------------------------------------------------------------
    // Arbitraries
    // ------------------------------------------------------------------

    /**
     * The supported {@code (bitDepth, byteOrder, encoding)} tuples, matching
     * every combination {@code SampleConversion.decoderFor} accepts
     * (signed int 16/24/32/64 in both orders; unsigned int 16/24/32/64 in
     * both orders; IEEE float 32/64 in both orders).
     */
    @Provide
    Arbitrary<PcmTuple> pcmTuples() {
        Arbitrary<ByteOrder> order = Arbitraries.of(
                ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN);

        Arbitrary<PcmTuple> integerTuples = Combinators.combine(
                Arbitraries.of(16, 24, 32, 64),
                Arbitraries.of(PcmEncoding.SIGNED_INT, PcmEncoding.UNSIGNED_INT),
                order
        ).as((bitDepth, encoding, byteOrder) -> new PcmTuple(bitDepth, byteOrder, encoding));

        Arbitrary<PcmTuple> floatTuples = Combinators.combine(
                Arbitraries.of(32, 64),
                order
        ).as((bitDepth, byteOrder) ->
                new PcmTuple(bitDepth, byteOrder, PcmEncoding.IEEE_FLOAT));

        return Arbitraries.oneOf(integerTuples, floatTuples);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Compare two decoded doubles within {@link #EPSILON}. Handles the
     * non-finite cases explicitly: NaN values compare equal when their
     * raw {@code long} bit patterns agree (so that a random IEEE float
     * byte draw whose bits happen to form a NaN still passes as long as
     * production and reference produce bit-identical outputs), and
     * positive/negative infinities compare equal to themselves.
     */
    private static boolean closeEnough(double actual, double expected) {
        if (Double.isNaN(actual) || Double.isNaN(expected)) {
            return Double.doubleToRawLongBits(actual)
                    == Double.doubleToRawLongBits(expected);
        }
        if (Double.isInfinite(actual) || Double.isInfinite(expected)) {
            return Double.compare(actual, expected) == 0;
        }
        return Math.abs(actual - expected) < EPSILON;
    }

    /**
     * Return a new byte array whose length is a positive multiple of
     * {@code bytesPerFrame}. If {@code raw} already carries at least one
     * frame, trim to the largest whole-frame prefix; otherwise return a
     * single zero-filled frame so the property still exercises the decode
     * path even on a near-empty draw.
     */
    private static byte[] trimOrPadToFrameBoundary(byte[] raw, int bytesPerFrame) {
        int frames = raw.length / bytesPerFrame;
        if (frames == 0) {
            return new byte[bytesPerFrame];
        }
        int len = frames * bytesPerFrame;
        byte[] trimmed = new byte[len];
        System.arraycopy(raw, 0, trimmed, 0, len);
        return trimmed;
    }

    /**
     * Independent reference decoder. Reads {@code bitDepth / 8} bytes of
     * {@code data} starting at {@code offset}, assembles them per the
     * tuple's byte order, and returns the normalised {@code double}.
     *
     * <p>Integer PCM is decoded by manual bit assembly so the reference
     * path shares no code with {@code SampleConversion}. IEEE float PCM
     * is decoded through {@link ByteBuffer} — a different route than the
     * {@code Float.intBitsToFloat}/{@code Double.longBitsToDouble} path
     * used in production — which still gives bit-exact results for
     * finite values and NaN-by-bits for non-finite ones (a {@code float}
     * widens to {@code double} losslessly, so the equality holds).
     */
    private static double referenceDecode(byte[] data, int offset, PcmTuple tuple) {
        int bitDepth = tuple.bitDepth();
        ByteOrder order = tuple.byteOrder();
        PcmEncoding encoding = tuple.encoding();

        switch (encoding) {
            case SIGNED_INT:
                return signedIntRef(data, offset, bitDepth, order);
            case UNSIGNED_INT:
                return unsignedIntRef(data, offset, bitDepth, order);
            case IEEE_FLOAT:
                return ieeeFloatRef(data, offset, bitDepth, order);
            default:
                throw new AssertionError("Unhandled encoding: " + encoding);
        }
    }

    /**
     * Reference decoder for signed integer PCM: assemble an unsigned
     * magnitude, sign-extend from bit {@code bitDepth - 1}, then divide
     * by {@code 2^(bitDepth - 1)}.
     */
    private static double signedIntRef(byte[] data, int offset, int bitDepth, ByteOrder order) {
        long unsigned = assembleUnsigned(data, offset, bitDepth, order);
        long signed;
        if (bitDepth == 64) {
            // A 64-bit unsigned value already sign-extends correctly when
            // reinterpreted as signed long (wraparound gives the right
            // two's-complement interpretation).
            signed = unsigned;
        } else {
            long signBit = 1L << (bitDepth - 1);
            if ((unsigned & signBit) != 0L) {
                // Extend the sign bit into the high bits.
                long mask = -1L << bitDepth;
                signed = unsigned | mask;
            } else {
                signed = unsigned;
            }
        }
        double divisor = Math.pow(2.0, bitDepth - 1);
        if (bitDepth == 64) {
            // 2^63 is not representable as a signed long, so do the
            // division in double space directly from the long value.
            return ((double) signed) / divisor;
        }
        return signed / divisor;
    }

    /**
     * Reference decoder for unsigned integer PCM: assemble the unsigned
     * magnitude, subtract the midpoint, then divide by
     * {@code 2^(bitDepth - 1)}.
     */
    private static double unsignedIntRef(byte[] data, int offset, int bitDepth, ByteOrder order) {
        long unsigned = assembleUnsigned(data, offset, bitDepth, order);
        double midpoint = Math.pow(2.0, bitDepth - 1);
        double divisor = midpoint;
        double asDouble;
        if (bitDepth == 64) {
            // The 64-bit assembler stores the raw unsigned 64-bit value
            // in a long; if negative (high bit set) it represents an
            // unsigned value >= 2^63.
            if (unsigned >= 0L) {
                asDouble = (double) unsigned;
            } else {
                // Clear the top bit and add 2^63 in double space.
                asDouble = ((double) (unsigned & Long.MAX_VALUE)) + midpoint;
            }
        } else {
            asDouble = (double) unsigned;
        }
        return (asDouble - midpoint) / divisor;
    }

    /**
     * Reference decoder for IEEE float PCM. 32-bit uses
     * {@link ByteBuffer#getFloat()}, 64-bit uses
     * {@link ByteBuffer#getDouble()} — a different assembly route than
     * production, so the property checks more than reflexivity.
     */
    private static double ieeeFloatRef(byte[] data, int offset, int bitDepth, ByteOrder order) {
        ByteBuffer bb = ByteBuffer.wrap(data, offset, bitDepth / 8).order(order);
        if (bitDepth == 32) {
            return bb.getFloat();
        }
        return bb.getDouble();
    }

    /**
     * Assemble {@code bitDepth / 8} bytes starting at {@code offset} into
     * the low-order bits of a {@code long}, per {@code order}. No sign
     * extension is applied; callers interpret the result as signed or
     * unsigned as needed.
     */
    private static long assembleUnsigned(byte[] data, int offset, int bitDepth, ByteOrder order) {
        int numBytes = bitDepth / 8;
        long result = 0L;
        if (order == ByteOrder.LITTLE_ENDIAN) {
            for (int i = 0; i < numBytes; i++) {
                result |= ((long) (data[offset + i] & 0xFF)) << (i * 8);
            }
        } else {
            for (int i = 0; i < numBytes; i++) {
                result |= ((long) (data[offset + i] & 0xFF)) << ((numBytes - 1 - i) * 8);
            }
        }
        return result;
    }
}
