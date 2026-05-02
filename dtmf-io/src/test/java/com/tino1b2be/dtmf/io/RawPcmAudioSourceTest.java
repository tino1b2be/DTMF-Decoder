package com.tino1b2be.dtmf.io;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RawPcmAudioSource} (Task 3.4).
 *
 * <p>Covers constructor validation ({@link NullPointerException} and
 * {@link IllegalArgumentException} paths), the
 * {@link RawPcmAudioSource#fromPcm16LittleEndian(byte[], int, int)} factory
 * round-trip, partial-read behavior on {@code length > remainingFrames},
 * {@link RawPcmAudioSource#seek(long)} validation, use-after-close
 * behavior, and {@link RawPcmAudioSource#close()} idempotence.
 *
 * <p>Validates: Requirements 3.14, 7.4, 7.5, 7.6, 7.7, 7.8, 7.9, 7.11,
 * 12.1, 12.2.
 */
class RawPcmAudioSourceTest {

    /** Convenience buffer size: 4 mono PCM16 frames = 8 bytes. */
    private static final byte[] VALID_PCM16_MONO_4FRAMES = new byte[8];

    // ---------------------------------------------------------------------
    // NullPointerException paths — Req 7.4
    // ---------------------------------------------------------------------

    @Test
    void constructorThrowsNpeWhenDataIsNull() {
        NullPointerException npe = assertThrows(
                NullPointerException.class,
                () -> new RawPcmAudioSource(
                        null, 8_000, 16, ByteOrder.LITTLE_ENDIAN, 1, PcmEncoding.SIGNED_INT));
        assertTrue(
                npe.getMessage() != null && npe.getMessage().contains("data"),
                "Expected NPE message to identify the 'data' parameter; was: " + npe.getMessage());
    }

    @Test
    void constructorThrowsNpeWhenByteOrderIsNull() {
        NullPointerException npe = assertThrows(
                NullPointerException.class,
                () -> new RawPcmAudioSource(
                        VALID_PCM16_MONO_4FRAMES, 8_000, 16, null, 1, PcmEncoding.SIGNED_INT));
        assertTrue(
                npe.getMessage() != null && npe.getMessage().contains("byteOrder"),
                "Expected NPE message to identify the 'byteOrder' parameter; was: " + npe.getMessage());
    }

    @Test
    void constructorThrowsNpeWhenEncodingIsNull() {
        NullPointerException npe = assertThrows(
                NullPointerException.class,
                () -> new RawPcmAudioSource(
                        VALID_PCM16_MONO_4FRAMES, 8_000, 16, ByteOrder.LITTLE_ENDIAN, 1, null));
        assertTrue(
                npe.getMessage() != null && npe.getMessage().contains("encoding"),
                "Expected NPE message to identify the 'encoding' parameter; was: " + npe.getMessage());
    }

    // ---------------------------------------------------------------------
    // IllegalArgumentException: sampleRate out of [1, 384000] — Req 7.5
    // ---------------------------------------------------------------------

    @Test
    void constructorRejectsSampleRateZero() {
        IllegalArgumentException iae = assertThrows(
                IllegalArgumentException.class,
                () -> new RawPcmAudioSource(
                        VALID_PCM16_MONO_4FRAMES, 0, 16, ByteOrder.LITTLE_ENDIAN, 1, PcmEncoding.SIGNED_INT));
        String msg = iae.getMessage();
        assertNotNull(msg, "IAE message must not be null");
        assertAll(
                () -> assertTrue(msg.contains("sampleRate"),
                        "IAE message must identify 'sampleRate'; was: " + msg),
                () -> assertTrue(msg.contains("0"),
                        "IAE message must contain the offending value '0'; was: " + msg),
                () -> assertTrue(msg.contains("1") && msg.contains("384000"),
                        "IAE message must identify the valid range [1, 384000]; was: " + msg));
    }

    @Test
    void constructorRejectsSampleRateNegative() {
        IllegalArgumentException iae = assertThrows(
                IllegalArgumentException.class,
                () -> new RawPcmAudioSource(
                        VALID_PCM16_MONO_4FRAMES, -1, 16, ByteOrder.LITTLE_ENDIAN, 1, PcmEncoding.SIGNED_INT));
        String msg = iae.getMessage();
        assertNotNull(msg, "IAE message must not be null");
        assertAll(
                () -> assertTrue(msg.contains("sampleRate"),
                        "IAE message must identify 'sampleRate'; was: " + msg),
                () -> assertTrue(msg.contains("-1"),
                        "IAE message must contain the offending value '-1'; was: " + msg),
                () -> assertTrue(msg.contains("1") && msg.contains("384000"),
                        "IAE message must identify the valid range [1, 384000]; was: " + msg));
    }

    @Test
    void constructorRejectsSampleRateAboveMax() {
        IllegalArgumentException iae = assertThrows(
                IllegalArgumentException.class,
                () -> new RawPcmAudioSource(
                        VALID_PCM16_MONO_4FRAMES, 384_001, 16, ByteOrder.LITTLE_ENDIAN, 1, PcmEncoding.SIGNED_INT));
        String msg = iae.getMessage();
        assertNotNull(msg, "IAE message must not be null");
        assertAll(
                () -> assertTrue(msg.contains("sampleRate"),
                        "IAE message must identify 'sampleRate'; was: " + msg),
                () -> assertTrue(msg.contains("384001"),
                        "IAE message must contain the offending value '384001'; was: " + msg),
                () -> assertTrue(msg.contains("1") && msg.contains("384000"),
                        "IAE message must identify the valid range [1, 384000]; was: " + msg));
    }

    // ---------------------------------------------------------------------
    // IllegalArgumentException: channelCount out of [1, 8] — Req 7.6
    // ---------------------------------------------------------------------

    @Test
    void constructorRejectsChannelCountZero() {
        IllegalArgumentException iae = assertThrows(
                IllegalArgumentException.class,
                () -> new RawPcmAudioSource(
                        VALID_PCM16_MONO_4FRAMES, 8_000, 16, ByteOrder.LITTLE_ENDIAN, 0, PcmEncoding.SIGNED_INT));
        String msg = iae.getMessage();
        assertNotNull(msg, "IAE message must not be null");
        assertAll(
                () -> assertTrue(msg.contains("channelCount"),
                        "IAE message must identify 'channelCount'; was: " + msg),
                () -> assertTrue(msg.contains("0"),
                        "IAE message must contain the offending value '0'; was: " + msg),
                () -> assertTrue(msg.contains("1") && msg.contains("8"),
                        "IAE message must identify the valid range [1, 8]; was: " + msg));
    }

    @Test
    void constructorRejectsChannelCountAboveMax() {
        IllegalArgumentException iae = assertThrows(
                IllegalArgumentException.class,
                () -> new RawPcmAudioSource(
                        VALID_PCM16_MONO_4FRAMES, 8_000, 16, ByteOrder.LITTLE_ENDIAN, 9, PcmEncoding.SIGNED_INT));
        String msg = iae.getMessage();
        assertNotNull(msg, "IAE message must not be null");
        assertAll(
                () -> assertTrue(msg.contains("channelCount"),
                        "IAE message must identify 'channelCount'; was: " + msg),
                () -> assertTrue(msg.contains("9"),
                        "IAE message must contain the offending value '9'; was: " + msg),
                () -> assertTrue(msg.contains("1") && msg.contains("8"),
                        "IAE message must identify the valid range [1, 8]; was: " + msg));
    }

    // ---------------------------------------------------------------------
    // IllegalArgumentException: bitDepth not in {16, 24, 32, 64} — Req 7.7
    // ---------------------------------------------------------------------

    @Test
    void constructorRejectsBitDepth8() {
        // data must be valid for the bytesPerFrame test to not short-circuit first;
        // bitDepth 8 is rejected regardless of the trailing frame check.
        IllegalArgumentException iae = assertThrows(
                IllegalArgumentException.class,
                () -> new RawPcmAudioSource(
                        new byte[8], 8_000, 8, ByteOrder.LITTLE_ENDIAN, 1, PcmEncoding.SIGNED_INT));
        String msg = iae.getMessage();
        assertNotNull(msg, "IAE message must not be null");
        assertAll(
                () -> assertTrue(msg.contains("bitDepth"),
                        "IAE message must identify 'bitDepth'; was: " + msg),
                () -> assertTrue(msg.contains("8"),
                        "IAE message must contain the offending value '8'; was: " + msg),
                () -> assertTrue(msg.contains("16") && msg.contains("24")
                                && msg.contains("32") && msg.contains("64"),
                        "IAE message must identify the valid set {16, 24, 32, 64}; was: " + msg));
    }

    @Test
    void constructorRejectsBitDepth20() {
        IllegalArgumentException iae = assertThrows(
                IllegalArgumentException.class,
                () -> new RawPcmAudioSource(
                        new byte[8], 8_000, 20, ByteOrder.LITTLE_ENDIAN, 1, PcmEncoding.SIGNED_INT));
        String msg = iae.getMessage();
        assertNotNull(msg, "IAE message must not be null");
        assertAll(
                () -> assertTrue(msg.contains("bitDepth"),
                        "IAE message must identify 'bitDepth'; was: " + msg),
                () -> assertTrue(msg.contains("20"),
                        "IAE message must contain the offending value '20'; was: " + msg),
                () -> assertTrue(msg.contains("16") && msg.contains("24")
                                && msg.contains("32") && msg.contains("64"),
                        "IAE message must identify the valid set {16, 24, 32, 64}; was: " + msg));
    }

    @Test
    void constructorRejectsBitDepth48() {
        IllegalArgumentException iae = assertThrows(
                IllegalArgumentException.class,
                () -> new RawPcmAudioSource(
                        new byte[8], 8_000, 48, ByteOrder.LITTLE_ENDIAN, 1, PcmEncoding.SIGNED_INT));
        String msg = iae.getMessage();
        assertNotNull(msg, "IAE message must not be null");
        assertAll(
                () -> assertTrue(msg.contains("bitDepth"),
                        "IAE message must identify 'bitDepth'; was: " + msg),
                () -> assertTrue(msg.contains("48"),
                        "IAE message must contain the offending value '48'; was: " + msg),
                () -> assertTrue(msg.contains("16") && msg.contains("24")
                                && msg.contains("32") && msg.contains("64"),
                        "IAE message must identify the valid set {16, 24, 32, 64}; was: " + msg));
    }

    // ---------------------------------------------------------------------
    // IllegalArgumentException: IEEE_FLOAT with 16- or 24-bit depth — Req 7.8
    // ---------------------------------------------------------------------

    @Test
    void constructorRejectsIeeeFloatWithBitDepth16() {
        IllegalArgumentException iae = assertThrows(
                IllegalArgumentException.class,
                () -> new RawPcmAudioSource(
                        new byte[8], 8_000, 16, ByteOrder.LITTLE_ENDIAN, 1, PcmEncoding.IEEE_FLOAT));
        String msg = iae.getMessage();
        assertNotNull(msg, "IAE message must not be null");
        assertAll(
                () -> assertTrue(msg.contains("IEEE_FLOAT"),
                        "IAE message must identify the 'IEEE_FLOAT' encoding; was: " + msg),
                () -> assertTrue(msg.contains("16"),
                        "IAE message must contain the offending bit depth '16'; was: " + msg),
                () -> assertTrue(msg.contains("32") && msg.contains("64"),
                        "IAE message must identify the valid bit depths {32, 64}; was: " + msg));
    }

    @Test
    void constructorRejectsIeeeFloatWithBitDepth24() {
        // 24-bit frame size is 3 bytes; use 6 bytes so the frame-size check passes
        // and the IEEE_FLOAT incompatibility check is reached.
        IllegalArgumentException iae = assertThrows(
                IllegalArgumentException.class,
                () -> new RawPcmAudioSource(
                        new byte[6], 8_000, 24, ByteOrder.LITTLE_ENDIAN, 1, PcmEncoding.IEEE_FLOAT));
        String msg = iae.getMessage();
        assertNotNull(msg, "IAE message must not be null");
        assertAll(
                () -> assertTrue(msg.contains("IEEE_FLOAT"),
                        "IAE message must identify the 'IEEE_FLOAT' encoding; was: " + msg),
                () -> assertTrue(msg.contains("24"),
                        "IAE message must contain the offending bit depth '24'; was: " + msg),
                () -> assertTrue(msg.contains("32") && msg.contains("64"),
                        "IAE message must identify the valid bit depths {32, 64}; was: " + msg));
    }

    // ---------------------------------------------------------------------
    // IllegalArgumentException: data.length not a multiple of bytesPerFrame — Req 7.9
    // ---------------------------------------------------------------------

    @Test
    void constructorRejectsDataLengthNotMultipleOfFrameSize() {
        // 16-bit mono = 2 bytes/frame; 5 bytes is not a valid multiple.
        IllegalArgumentException iae = assertThrows(
                IllegalArgumentException.class,
                () -> new RawPcmAudioSource(
                        new byte[5], 8_000, 16, ByteOrder.LITTLE_ENDIAN, 1, PcmEncoding.SIGNED_INT));
        String msg = iae.getMessage();
        assertNotNull(msg, "IAE message must not be null");
        assertAll(
                () -> assertTrue(msg.contains("5"),
                        "IAE message must contain the offending data length '5'; was: " + msg),
                () -> assertTrue(msg.contains("2"),
                        "IAE message must identify the frame size '2' bytes; was: " + msg));
    }

    // ---------------------------------------------------------------------
    // fromPcm16LittleEndian round-trip — Req 7.11
    // ---------------------------------------------------------------------

    @Test
    void fromPcm16LittleEndianRoundTripsShortValuedData() throws IOException {
        // Encode four known 16-bit signed little-endian samples, wrap them,
        // read them back through the AudioSource pipeline, and verify the
        // normalized doubles match the expected 16-bit normalisation.
        short[] samples = new short[] {
                0,
                1,
                -1,
                Short.MAX_VALUE,
                Short.MIN_VALUE,
                -12_345,
                12_345,
                4_096
        };
        byte[] data = encodeShortsLittleEndian(samples);

        RawPcmAudioSource source = RawPcmAudioSource.fromPcm16LittleEndian(data, 8_000, 1);
        try {
            assertAll(
                    () -> assertEquals(8_000, source.sampleRate()),
                    () -> assertEquals(1, source.channelCount()),
                    () -> assertEquals(16, source.bitDepth()),
                    () -> assertEquals(samples.length, source.totalFrames()),
                    () -> assertTrue(source.canSeek()),
                    () -> assertEquals(ByteOrder.LITTLE_ENDIAN, source.byteOrder()),
                    () -> assertEquals(PcmEncoding.SIGNED_INT, source.encoding()));

            double[] buffer = new double[samples.length];
            int framesRead = source.read(buffer, 0, samples.length);
            assertEquals(samples.length, framesRead, "Expected full read of " + samples.length + " frames");

            double[] expected = new double[samples.length];
            for (int i = 0; i < samples.length; i++) {
                expected[i] = samples[i] / 32768.0;
            }
            assertArrayEquals(expected, buffer, 0.0,
                    "Decoded samples must match expected normalized values");

            // Second call must return -1: the source is exhausted.
            assertEquals(-1, source.read(buffer, 0, 1),
                    "Exhausted source must return -1 on subsequent read");
        } finally {
            source.close();
        }
    }

    // ---------------------------------------------------------------------
    // Partial read: length > remainingFrames — Req 12.1, 12.2
    // ---------------------------------------------------------------------

    @Test
    void readWithLengthGreaterThanRemainingReturnsRemainingThenMinusOne() throws IOException {
        // 4 frames of mono PCM16 = 8 bytes.
        byte[] data = new byte[8];
        // Arbitrary non-zero content so we can verify all frames are produced;
        // the actual values do not matter for this test.
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i + 1);
        }

        RawPcmAudioSource source = RawPcmAudioSource.fromPcm16LittleEndian(data, 8_000, 1);
        try {
            int totalFrames = (int) source.totalFrames();
            assertEquals(4, totalFrames, "Sanity: expected 4 frames");

            // Request more than the source holds: it must return exactly
            // the remaining frame count.
            double[] buffer = new double[totalFrames + 5];
            int firstRead = source.read(buffer, 0, totalFrames + 5);
            assertEquals(totalFrames, firstRead,
                    "read(length > remaining) must return remaining frame count");
            assertEquals(totalFrames, source.currentFrame(),
                    "currentFrame() must advance by the returned frame count");

            // Subsequent call must return -1 (exhausted).
            int secondRead = source.read(buffer, 0, 1);
            assertEquals(-1, secondRead,
                    "Exhausted source must return -1 on subsequent read");
        } finally {
            source.close();
        }
    }

    // ---------------------------------------------------------------------
    // seek validation — Req 12.1, 12.2
    // ---------------------------------------------------------------------

    @Test
    void seekNegativeFrameIndexThrowsIaeIdentifyingValueAndRange() throws IOException {
        byte[] data = new byte[8]; // 4 frames of mono PCM16
        RawPcmAudioSource source = RawPcmAudioSource.fromPcm16LittleEndian(data, 8_000, 1);
        try {
            IllegalArgumentException iae = assertThrows(
                    IllegalArgumentException.class,
                    () -> source.seek(-1L));
            String msg = iae.getMessage();
            assertNotNull(msg, "IAE message must not be null");
            assertAll(
                    () -> assertTrue(msg.contains("-1"),
                            "IAE message must contain the offending value '-1'; was: " + msg),
                    () -> assertTrue(msg.contains("0") && msg.contains("4"),
                            "IAE message must identify the valid range [0, 4]; was: " + msg));
        } finally {
            source.close();
        }
    }

    @Test
    void seekBeyondTotalFramesThrowsIaeIdentifyingValueAndRange() throws IOException {
        byte[] data = new byte[8]; // 4 frames of mono PCM16
        RawPcmAudioSource source = RawPcmAudioSource.fromPcm16LittleEndian(data, 8_000, 1);
        try {
            IllegalArgumentException iae = assertThrows(
                    IllegalArgumentException.class,
                    () -> source.seek(5L));
            String msg = iae.getMessage();
            assertNotNull(msg, "IAE message must not be null");
            assertAll(
                    () -> assertTrue(msg.contains("5"),
                            "IAE message must contain the offending value '5'; was: " + msg),
                    () -> assertTrue(msg.contains("0") && msg.contains("4"),
                            "IAE message must identify the valid range [0, 4]; was: " + msg));
        } finally {
            source.close();
        }
    }

    @Test
    void seekToValidFrameUpdatesCurrentFrame() throws IOException {
        byte[] data = new byte[8]; // 4 frames of mono PCM16
        RawPcmAudioSource source = RawPcmAudioSource.fromPcm16LittleEndian(data, 8_000, 1);
        try {
            assertDoesNotThrow(() -> source.seek(2L));
            assertEquals(2L, source.currentFrame(),
                    "seek must reposition currentFrame()");

            // Seeking to totalFrames is allowed (end-of-stream cursor).
            assertDoesNotThrow(() -> source.seek(source.totalFrames()));
            assertEquals(source.totalFrames(), source.currentFrame(),
                    "seek(totalFrames) must position cursor at end-of-stream");

            // Back to zero also allowed.
            assertDoesNotThrow(() -> source.seek(0L));
            assertEquals(0L, source.currentFrame());
        } finally {
            source.close();
        }
    }

    // ---------------------------------------------------------------------
    // Use-after-close — Req 3.14
    // ---------------------------------------------------------------------

    @Test
    void readAfterCloseThrowsIoExceptionIdentifyingSourceAsClosed() throws IOException {
        byte[] data = new byte[8];
        RawPcmAudioSource source = RawPcmAudioSource.fromPcm16LittleEndian(data, 8_000, 1);
        source.close();

        IOException io = assertThrows(
                IOException.class,
                () -> source.read(new double[4], 0, 4));
        String msg = io.getMessage();
        assertNotNull(msg, "IOException message must not be null");
        assertTrue(msg.toLowerCase().contains("closed"),
                "IOException message must identify the source as closed; was: " + msg);
    }

    @Test
    void seekAfterCloseThrowsIoExceptionIdentifyingSourceAsClosed() throws IOException {
        byte[] data = new byte[8];
        RawPcmAudioSource source = RawPcmAudioSource.fromPcm16LittleEndian(data, 8_000, 1);
        source.close();

        IOException io = assertThrows(
                IOException.class,
                () -> source.seek(0L));
        String msg = io.getMessage();
        assertNotNull(msg, "IOException message must not be null");
        assertTrue(msg.toLowerCase().contains("closed"),
                "IOException message must identify the source as closed; was: " + msg);
    }

    // ---------------------------------------------------------------------
    // close() idempotence — Req 3.14
    // ---------------------------------------------------------------------

    @Test
    void closeIsIdempotent() throws IOException {
        byte[] data = new byte[8];
        RawPcmAudioSource source = RawPcmAudioSource.fromPcm16LittleEndian(data, 8_000, 1);

        // First close is the real transition.
        source.close();
        // Second call must be a no-op: no exception, no state change.
        assertDoesNotThrow(source::close,
                "close() must be idempotent: a second invocation is a no-op");

        // Behavior after repeated close remains "closed".
        IOException io = assertThrows(
                IOException.class,
                () -> source.read(new double[4], 0, 4));
        assertTrue(io.getMessage() != null && io.getMessage().toLowerCase().contains("closed"),
                "After repeated close(), read() must still throw IOException for a closed source");
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * Encode a {@code short[]} as little-endian signed PCM16 bytes.
     * Mirrors the layout {@code fromPcm16LittleEndian} decodes.
     */
    private static byte[] encodeShortsLittleEndian(short[] samples) {
        ByteBuffer bb = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short s : samples) {
            bb.putShort(s);
        }
        return bb.array();
    }
}
