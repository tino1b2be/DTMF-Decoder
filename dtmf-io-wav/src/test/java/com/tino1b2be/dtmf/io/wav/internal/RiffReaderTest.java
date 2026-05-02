package com.tino1b2be.dtmf.io.wav.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link RiffReader} (Task 6.2, Requirement 9.11).
 *
 * <p>Each primitive is exercised against both byte-source modes
 * ({@link FileChannel} and {@link InputStream}) so the interface-level
 * contract documented on the reader holds regardless of which constructor
 * a caller picks. The EOF path for every read primitive is tested under
 * both modes too because the exception mechanism differs internally
 * (channel mode checks {@link FileChannel#size()}, stream mode checks
 * {@link InputStream#read} returning {@code -1}) yet must be indistinguishable
 * to callers.
 *
 * <p>Requirement 9.11's "chunk size exceeding remaining file size" clause
 * is pinned by driving {@link RiffReader#skip(long)} with a count that
 * runs past the end of a fixture and asserting an {@link EOFException}
 * (a subclass of {@link IOException}) surfaces for both modes.
 */
class RiffReaderTest {

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * Build a little-endian byte buffer mirroring what a well-formed RIFF
     * file would contain, so tests can drive the reader against byte
     * sequences that actually look like RIFF data.
     */
    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    /** Writes the payload to a temp file and returns a read-only {@link FileChannel}. */
    private static FileChannel channelOf(Path dir, byte[] payload) throws IOException {
        Path file = Files.createTempFile(dir, "riff", ".bin");
        Files.write(file, payload);
        return FileChannel.open(file, StandardOpenOption.READ);
    }

    // =====================================================================
    // readAscii
    // =====================================================================

    @Test
    @DisplayName("readAscii(4) from stream returns the ASCII string and advances position")
    void readAsciiStreamHappyPath() throws IOException {
        byte[] payload = "RIFFxxxxWAVE".getBytes();
        RiffReader reader = new RiffReader(new ByteArrayInputStream(payload));

        assertEquals("RIFF", reader.readAscii(4));
        assertEquals(4L, reader.position(),
                "Stream-mode position starts at 0 and increments by bytes consumed");
    }

    @Test
    @DisplayName("readAscii(4) from channel returns the ASCII string and advances channel position")
    void readAsciiChannelHappyPath(@TempDir Path dir) throws IOException {
        byte[] payload = "RIFFxxxxWAVE".getBytes();
        try (FileChannel channel = channelOf(dir, payload)) {
            RiffReader reader = new RiffReader(channel);
            assertEquals("RIFF", reader.readAscii(4));
            assertEquals(4L, reader.position(),
                    "Channel-mode position mirrors FileChannel.position()");
            assertEquals(4L, channel.position(),
                    "Reader reads advance the underlying channel position");
        }
    }

    @Test
    @DisplayName("readAscii(0) returns the empty string without consuming bytes")
    void readAsciiZeroLengthIsNoOp() throws IOException {
        byte[] payload = "RIFF".getBytes();
        RiffReader reader = new RiffReader(new ByteArrayInputStream(payload));
        assertEquals("", reader.readAscii(0));
        assertEquals(0L, reader.position());
    }

    @Test
    @DisplayName("readAscii with n longer than scratch buffer still works")
    void readAsciiLongerThanScratch() throws IOException {
        // scratch is 8 bytes; ask for 12 to force the alternate path.
        byte[] payload = "RIFFxxxxWAVE".getBytes();
        RiffReader reader = new RiffReader(new ByteArrayInputStream(payload));
        assertEquals("RIFFxxxxWAVE", reader.readAscii(12));
        assertEquals(12L, reader.position());
    }

    @Test
    @DisplayName("readAscii with negative n throws IllegalArgumentException")
    void readAsciiNegativeNRejected() {
        RiffReader reader = new RiffReader(new ByteArrayInputStream(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> reader.readAscii(-1));
    }

    @Test
    @DisplayName("readAscii past end-of-stream throws EOFException")
    void readAsciiBeyondEndThrowsEofStream() {
        RiffReader reader = new RiffReader(new ByteArrayInputStream("RIF".getBytes()));
        assertThrows(EOFException.class, () -> reader.readAscii(4));
    }

    @Test
    @DisplayName("readAscii past end-of-channel throws EOFException")
    void readAsciiBeyondEndThrowsEofChannel(@TempDir Path dir) throws IOException {
        try (FileChannel channel = channelOf(dir, "RIF".getBytes())) {
            RiffReader reader = new RiffReader(channel);
            assertThrows(EOFException.class, () -> reader.readAscii(4));
        }
    }

    // =====================================================================
    // readU32LE — little-endian unsigned 32-bit returned as long
    // =====================================================================

    @Test
    @DisplayName("readU32LE decodes little-endian bytes into a long")
    void readU32LESmallValue() throws IOException {
        // 0x12345678 little-endian is {0x78, 0x56, 0x34, 0x12}
        byte[] payload = bytes(0x78, 0x56, 0x34, 0x12);
        RiffReader reader = new RiffReader(new ByteArrayInputStream(payload));
        assertEquals(0x1234_5678L, reader.readU32LE());
        assertEquals(4L, reader.position());
    }

    @Test
    @DisplayName("readU32LE returns an unsigned value (no sign flip at the 2 GiB boundary)")
    void readU32LEAboveInt31IsUnsigned() throws IOException {
        // Payload size 0xFFFFFFFE little-endian: {0xFE, 0xFF, 0xFF, 0xFF}
        byte[] payload = bytes(0xFE, 0xFF, 0xFF, 0xFF);
        RiffReader reader = new RiffReader(new ByteArrayInputStream(payload));
        long actual = reader.readU32LE();
        assertEquals(0xFFFF_FFFEL, actual,
                "readU32LE must return an unsigned 32-bit value as a long");
        assertTrue(actual > 0L,
                "Returned value must be strictly positive (no sign extension into long)");
    }

    @Test
    @DisplayName("readU32LE decodes 0xFFFFFFFF (the RF64 size-overflow marker) as 4294967295L")
    void readU32LEMaxValueIsRf64Marker() throws IOException {
        byte[] payload = bytes(0xFF, 0xFF, 0xFF, 0xFF);
        RiffReader reader = new RiffReader(new ByteArrayInputStream(payload));
        assertEquals(0xFFFF_FFFFL, reader.readU32LE());
    }

    @Test
    @DisplayName("readU32LE past end-of-stream throws EOFException")
    void readU32LEShortInputThrowsEof() {
        RiffReader reader = new RiffReader(new ByteArrayInputStream(bytes(0x01, 0x02, 0x03)));
        assertThrows(EOFException.class, reader::readU32LE);
    }

    @Test
    @DisplayName("readU32LE past end-of-channel throws EOFException")
    void readU32LEShortInputThrowsEofChannel(@TempDir Path dir) throws IOException {
        try (FileChannel channel = channelOf(dir, bytes(0x01, 0x02, 0x03))) {
            RiffReader reader = new RiffReader(channel);
            assertThrows(EOFException.class, reader::readU32LE);
        }
    }

    // =====================================================================
    // readU64LE — little-endian 64-bit
    // =====================================================================

    @Test
    @DisplayName("readU64LE decodes eight little-endian bytes into a long")
    void readU64LESmallValue() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        buf.putLong(0x0102_0304_0506_0708L);
        RiffReader reader = new RiffReader(new ByteArrayInputStream(buf.array()));
        assertEquals(0x0102_0304_0506_0708L, reader.readU64LE());
        assertEquals(8L, reader.position());
    }

    @Test
    @DisplayName("readU64LE decodes zero correctly")
    void readU64LEZero() throws IOException {
        byte[] payload = new byte[8];
        RiffReader reader = new RiffReader(new ByteArrayInputStream(payload));
        assertEquals(0L, reader.readU64LE());
    }

    @Test
    @DisplayName("readU64LE past end-of-stream throws EOFException")
    void readU64LEShortInputThrowsEof() {
        RiffReader reader = new RiffReader(new ByteArrayInputStream(new byte[7]));
        assertThrows(EOFException.class, reader::readU64LE);
    }

    // =====================================================================
    // skip and position
    // =====================================================================

    @Test
    @DisplayName("skip(n) advances stream position by exactly n")
    void skipStream() throws IOException {
        byte[] payload = "ABCDEFGHIJKL".getBytes();
        RiffReader reader = new RiffReader(new ByteArrayInputStream(payload));
        reader.skip(5L);
        assertEquals(5L, reader.position());
        assertEquals("FG", reader.readAscii(2));
        assertEquals(7L, reader.position());
    }

    @Test
    @DisplayName("skip(n) advances channel position by exactly n")
    void skipChannel(@TempDir Path dir) throws IOException {
        byte[] payload = "ABCDEFGHIJKL".getBytes();
        try (FileChannel channel = channelOf(dir, payload)) {
            RiffReader reader = new RiffReader(channel);
            reader.skip(5L);
            assertEquals(5L, reader.position());
            assertEquals(5L, channel.position(),
                    "Channel-mode skip updates the underlying channel");
            assertEquals("FG", reader.readAscii(2));
            assertEquals(7L, reader.position());
        }
    }

    @Test
    @DisplayName("skip(0) is a no-op")
    void skipZeroIsNoOp() throws IOException {
        RiffReader reader = new RiffReader(new ByteArrayInputStream("ABCD".getBytes()));
        reader.skip(0L);
        assertEquals(0L, reader.position());
        assertEquals("ABCD", reader.readAscii(4));
    }

    @Test
    @DisplayName("skip with negative n throws IllegalArgumentException")
    void skipNegativeRejected() {
        RiffReader reader = new RiffReader(new ByteArrayInputStream(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> reader.skip(-1L));
    }

    @Test
    @DisplayName("skip past end-of-stream throws EOFException (Requirement 9.11)")
    void skipBeyondEndStream() {
        // This is the core of Requirement 9.11: if a chunk's declared size
        // runs past the end of the underlying source, the skip that would
        // consume it must fail with an EOF (translated by the caller into
        // an IOException identifying the defect).
        RiffReader reader = new RiffReader(new ByteArrayInputStream("ABCD".getBytes()));
        assertThrows(EOFException.class, () -> reader.skip(100L));
    }

    @Test
    @DisplayName("skip past end-of-channel throws EOFException (Requirement 9.11)")
    void skipBeyondEndChannel(@TempDir Path dir) throws IOException {
        try (FileChannel channel = channelOf(dir, "ABCD".getBytes())) {
            RiffReader reader = new RiffReader(channel);
            assertThrows(EOFException.class, () -> reader.skip(100L));
            // Channel position must NOT have advanced past end-of-file.
            assertTrue(channel.position() <= channel.size(),
                    "EOF skip must not move channel position past EOF");
        }
    }

    @Test
    @DisplayName("skip handles partial underlying-stream skips via read fallback")
    void skipHandlesPartialStreamSkip() throws IOException {
        // A stream whose skip returns 0 forces the read-byte fallback path.
        byte[] payload = "ABCDEFGH".getBytes();
        InputStream adversarial = new InputStream() {
            private final ByteArrayInputStream delegate = new ByteArrayInputStream(payload);

            @Override
            public int read() {
                return delegate.read();
            }

            @Override
            public int read(byte[] b, int off, int len) {
                return delegate.read(b, off, len);
            }

            @Override
            public long skip(long n) {
                // Never skips; forces the reader into the read-byte fallback.
                return 0L;
            }
        };
        RiffReader reader = new RiffReader(adversarial);
        reader.skip(4L);
        assertEquals(4L, reader.position());
        assertEquals("EFGH", reader.readAscii(4));
    }

    // =====================================================================
    // skipPaddingIfNeeded
    // =====================================================================

    @Test
    @DisplayName("skipPaddingIfNeeded skips one byte for odd chunkSize")
    void paddingSkippedForOddChunk() throws IOException {
        // Layout: id "LIST" | u32 size=3 | 3 bytes payload | 1 pad byte | id "data"
        byte[] payload = bytes(
                'L', 'I', 'S', 'T',
                0x03, 0x00, 0x00, 0x00,
                'X', 'Y', 'Z',
                0x00,              // pad byte because size is odd
                'd', 'a', 't', 'a'
        );
        RiffReader reader = new RiffReader(new ByteArrayInputStream(payload));
        assertEquals("LIST", reader.readAscii(4));
        long size = reader.readU32LE();
        assertEquals(3L, size);
        reader.skip(size);          // consume the payload
        reader.skipPaddingIfNeeded(size);
        assertEquals("data", reader.readAscii(4),
                "Parser must land on the next chunk's ID after pad handling");
    }

    @Test
    @DisplayName("skipPaddingIfNeeded is a no-op for even chunkSize")
    void noPaddingForEvenChunk() throws IOException {
        // Layout: 4 bytes id | 4 bytes u32=4 | 4 bytes payload | next chunk id
        byte[] payload = bytes(
                'L', 'I', 'S', 'T',
                0x04, 0x00, 0x00, 0x00,
                'i', 'n', 'f', 'o',
                'd', 'a', 't', 'a'
        );
        RiffReader reader = new RiffReader(new ByteArrayInputStream(payload));
        assertEquals("LIST", reader.readAscii(4));
        long size = reader.readU32LE();
        reader.skip(size);
        reader.skipPaddingIfNeeded(size);
        assertEquals(12L, reader.position(),
                "Even chunk size must not consume a pad byte");
        assertEquals("data", reader.readAscii(4));
    }

    @Test
    @DisplayName("skipPaddingIfNeeded with negative chunkSize throws IllegalArgumentException")
    void paddingNegativeChunkSizeRejected() {
        RiffReader reader = new RiffReader(new ByteArrayInputStream(new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> reader.skipPaddingIfNeeded(-1L));
    }

    @Test
    @DisplayName("skipPaddingIfNeeded at end-of-input with odd chunkSize throws EOFException")
    void paddingEofOnOddChunkAtEnd() throws IOException {
        // Reader positioned right at EOF: odd size must still try to skip
        // the pad byte and fail.
        RiffReader reader = new RiffReader(new ByteArrayInputStream(new byte[0]));
        assertThrows(EOFException.class, () -> reader.skipPaddingIfNeeded(1L));
    }

    // =====================================================================
    // Constructors
    // =====================================================================

    @Test
    @DisplayName("Null FileChannel is rejected")
    void nullChannelRejected() {
        assertThrows(NullPointerException.class, () -> new RiffReader((FileChannel) null));
    }

    @Test
    @DisplayName("Null InputStream is rejected")
    void nullStreamRejected() {
        assertThrows(NullPointerException.class, () -> new RiffReader((InputStream) null));
    }

    // =====================================================================
    // End-to-end RIFF-walk scenarios
    // =====================================================================

    /**
     * Simulate the first few steps the WAV parser takes: read
     * {@code "RIFF" | size | "WAVE"}, then walk two chunks including a
     * pad-byte boundary, and confirm position tracking is consistent
     * across the whole walk.
     */
    @Nested
    class RiffWalkScenario {

        @Test
        @DisplayName("Full RIFF header + fmt/data walk tracks position correctly")
        void fullRiffWalkStream() throws IOException {
            // Construct a minimal RIFF/WAVE layout:
            //   0..3   "RIFF"
            //   4..7   u32 size (unused by reader here)
            //   8..11  "WAVE"
            //   12..15 "fmt "
            //   16..19 u32 size=16
            //   20..35 16 bytes of fmt payload
            //   36..39 "data"
            //   40..43 u32 size=8
            //   44..51 8 bytes of PCM data
            byte[] payload = new byte[52];
            ByteBuffer b = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
            b.put("RIFF".getBytes());
            b.putInt(44);                         // size
            b.put("WAVE".getBytes());
            b.put("fmt ".getBytes());
            b.putInt(16);
            for (int i = 0; i < 16; i++) b.put((byte) (0x10 + i));
            b.put("data".getBytes());
            b.putInt(8);
            for (int i = 0; i < 8; i++) b.put((byte) (0x20 + i));

            RiffReader reader = new RiffReader(new ByteArrayInputStream(payload));
            assertEquals("RIFF", reader.readAscii(4));
            assertEquals(44L, reader.readU32LE());
            assertEquals("WAVE", reader.readAscii(4));
            assertEquals(12L, reader.position(),
                    "After the 12-byte outer header, position must be 12");

            // fmt chunk
            assertEquals("fmt ", reader.readAscii(4));
            long fmtSize = reader.readU32LE();
            assertEquals(16L, fmtSize);
            long fmtStart = reader.position();
            assertEquals(20L, fmtStart);
            reader.skip(fmtSize);
            reader.skipPaddingIfNeeded(fmtSize);      // no-op, even size
            assertEquals(36L, reader.position());

            // data chunk
            assertEquals("data", reader.readAscii(4));
            long dataSize = reader.readU32LE();
            assertEquals(8L, dataSize);
            long dataStart = reader.position();
            assertEquals(44L, dataStart,
                    "data payload starts at byte 44 in this fixture");
        }

        @Test
        @DisplayName("Odd LIST chunk between fmt and data is skipped with pad byte")
        void oddListChunkBetweenFmtAndData() throws IOException {
            // Layout:
            //   "fmt " | size=16 | 16 bytes fmt body |
            //   "LIST" | size=3  | 3 bytes body | 1 pad byte |
            //   "data" | size=4  | 4 bytes payload
            byte[] payload = new byte[8 + 16 + 8 + 3 + 1 + 8 + 4];
            ByteBuffer b = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
            b.put("fmt ".getBytes());
            b.putInt(16);
            for (int i = 0; i < 16; i++) b.put((byte) i);
            b.put("LIST".getBytes());
            b.putInt(3);
            b.put((byte) 'a').put((byte) 'b').put((byte) 'c');
            b.put((byte) 0);                       // pad
            b.put("data".getBytes());
            b.putInt(4);
            b.put((byte) 1).put((byte) 2).put((byte) 3).put((byte) 4);

            RiffReader reader = new RiffReader(new ByteArrayInputStream(payload));
            assertEquals("fmt ", reader.readAscii(4));
            long fmtSize = reader.readU32LE();
            reader.skip(fmtSize);
            reader.skipPaddingIfNeeded(fmtSize);

            assertEquals("LIST", reader.readAscii(4));
            long listSize = reader.readU32LE();
            reader.skip(listSize);
            reader.skipPaddingIfNeeded(listSize);

            assertEquals("data", reader.readAscii(4),
                    "Parser must land on 'data' after the odd LIST chunk is skipped");
        }
    }

    // =====================================================================
    // Cross-mode equivalence: channel and stream read identical byte streams
    // =====================================================================

    @Test
    @DisplayName("Channel and stream modes read the same bytes identically")
    void channelAndStreamModesAreEquivalent(@TempDir Path dir) throws IOException {
        byte[] payload = new byte[32];
        ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".getBytes())
                .putInt(24)
                .put("WAVE".getBytes())
                .put("fmt ".getBytes())
                .putInt(8)
                .putInt(0x1234_5678)
                .putInt(0x9ABC_DEF0);

        RiffReader streamReader = new RiffReader(new ByteArrayInputStream(payload));
        try (FileChannel channel = channelOf(dir, payload)) {
            RiffReader channelReader = new RiffReader(channel);
            assertEquals(streamReader.readAscii(4), channelReader.readAscii(4));
            assertEquals(streamReader.readU32LE(), channelReader.readU32LE());
            assertEquals(streamReader.readAscii(4), channelReader.readAscii(4));
            assertEquals(streamReader.readAscii(4), channelReader.readAscii(4));
            assertEquals(streamReader.readU32LE(), channelReader.readU32LE());
            assertEquals(streamReader.position(), channelReader.position());
        }
    }

    /** Anchor: direct byte-equality check on the read buffer used internally. */
    @Test
    @DisplayName("Consecutive reads return the correct bytes in order")
    void consecutiveReadsReturnBytesInOrder() throws IOException {
        byte[] payload = {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'};
        RiffReader reader = new RiffReader(new ByteArrayInputStream(payload));
        assertEquals("AB", reader.readAscii(2));
        assertEquals("CD", reader.readAscii(2));
        assertEquals("EFGH", reader.readAscii(4));

        // Re-run with byte-level assertions to double-check no byte was
        // lost between the public method and the internal scratch buffer.
        RiffReader reader2 = new RiffReader(new ByteArrayInputStream(payload));
        assertArrayEquals(new byte[] {'A', 'B'},
                reader2.readAscii(2).getBytes());
        assertArrayEquals(new byte[] {'C', 'D', 'E', 'F'},
                reader2.readAscii(4).getBytes());
    }
}
