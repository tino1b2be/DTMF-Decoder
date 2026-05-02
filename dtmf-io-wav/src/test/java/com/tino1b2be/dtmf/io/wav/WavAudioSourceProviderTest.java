package com.tino1b2be.dtmf.io.wav;

import com.tino1b2be.dtmf.io.AudioSource;
import com.tino1b2be.dtmf.io.UnsupportedAudioFormatException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link WavAudioSourceProvider} covering the RIFF/WAV
 * edge cases called out in Requirements 9.7&ndash;9.11 (Task 6.8).
 *
 * <p>The tests fall into three groups:
 *
 * <ol>
 *   <li><strong>Chunk-walking edge cases</strong> &mdash; odd-size
 *       chunks with their trailing pad byte, {@code LIST} chunks before
 *       {@code data}, and extra chunks after {@code data}. These hit
 *       the parser's chunk-walk loop in
 *       {@link WavAudioSourceProvider} and verify that the parser lands
 *       on the expected {@code data} payload even through the RIFF
 *       structure's more unusual (but entirely legal) shapes.</li>
 *   <li><strong>RF64 handling</strong> &mdash; the {@code ds64}-first
 *       invariant, the {@code 0xFFFFFFFF} 32-bit overflow marker, and
 *       the absence-of-{@code ds64} failure case.</li>
 *   <li><strong>Format-tag and
 *       {@code WAVEFORMATEXTENSIBLE} dispatch</strong> &mdash; mu-law,
 *       the classic PCM GUID, the A-law GUID, and the
 *       {@code wValidBitsPerSample < wBitsPerSample} case where the
 *       parser must defer to the container bit depth.</li>
 * </ol>
 *
 * <p>Well-formed fixtures use {@link WavEncoder} (Task 6.7); edge-case
 * fixtures are hand-crafted via {@link ByteBuffer} in
 * {@link ByteOrder#LITTLE_ENDIAN} order, which mirrors the RIFF
 * specification's mandated byte ordering. Fixtures never rely on
 * javax.sound.sampled (Req 9.12) and never check pre-built binary
 * samples into the repository.
 *
 * <p><strong>Open the input via</strong>
 * {@link WavAudioSourceProvider#open(java.io.InputStream, String)}
 * for byte-array fixtures and
 * {@link WavAudioSourceProvider#open(Path)} for on-disk fixtures.
 * Stream-backed sources do not support {@code seek}; channel-backed
 * sources do. Tests pick whichever mode matches the assertion they
 * need.
 */
class WavAudioSourceProviderTest {

    // ---------------------------------------------------------------------
    // Constants mirroring the provider-side spec
    // ---------------------------------------------------------------------

    private static final int WAVE_FORMAT_PCM = 0x0001;
    private static final int WAVE_FORMAT_MULAW = 0x0007;
    private static final int WAVE_FORMAT_EXTENSIBLE = 0xFFFE;

    /** {@code KSDATAFORMAT_SUBTYPE_PCM} on-disk layout. */
    private static final byte[] SUBTYPE_PCM_GUID = {
            0x01, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x10, 0x00,
            (byte) 0x80, 0x00,
            0x00, (byte) 0xAA, 0x00, 0x38, (byte) 0x9B, 0x71
    };

    /**
     * {@code KSDATAFORMAT_SUBTYPE_ALAW} on-disk layout
     * ({@code 00000006-0000-0010-8000-00AA00389B71}). A-law is
     * explicitly not supported by this module.
     */
    private static final byte[] SUBTYPE_ALAW_GUID = {
            0x06, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x10, 0x00,
            (byte) 0x80, 0x00,
            0x00, (byte) 0xAA, 0x00, 0x38, (byte) 0x9B, 0x71
    };

    // ---------------------------------------------------------------------
    // Fixture builders
    // ---------------------------------------------------------------------

    /**
     * Write the outer {@code RIFF | size | WAVE} header into {@code buf}.
     *
     * @param buf            destination (little-endian)
     * @param riffPayloadSize the 32-bit {@code chunkSize} field: total
     *                       file size minus 8 bytes
     */
    private static void putRiffHeader(ByteBuffer buf, int riffPayloadSize) {
        putAscii(buf, "RIFF");
        buf.putInt(riffPayloadSize);
        putAscii(buf, "WAVE");
    }

    /** Write an RF64 outer header (size field pinned to {@code 0xFFFFFFFF}). */
    private static void putRf64Header(ByteBuffer buf) {
        putAscii(buf, "RF64");
        buf.putInt(0xFFFF_FFFF);
        putAscii(buf, "WAVE");
    }

    /** Classic 16-byte {@code fmt } chunk payload (no extension). */
    private static void putClassicFmt(
            ByteBuffer buf,
            int formatTag,
            int channels,
            int sampleRate,
            int bitsPerSample) {
        int bytesPerSample = bitsPerSample / 8;
        int blockAlign = channels * bytesPerSample;
        int avgBytesPerSec = sampleRate * blockAlign;
        putAscii(buf, "fmt ");
        buf.putInt(16);
        buf.putShort((short) formatTag);
        buf.putShort((short) channels);
        buf.putInt(sampleRate);
        buf.putInt(avgBytesPerSec);
        buf.putShort((short) blockAlign);
        buf.putShort((short) bitsPerSample);
    }

    /** Write a {@code data} chunk header followed by {@code payload}. */
    private static void putDataChunk(ByteBuffer buf, byte[] payload) {
        putAscii(buf, "data");
        buf.putInt(payload.length);
        buf.put(payload);
    }

    /** Four-byte ASCII chunk ID or form type. */
    private static void putAscii(ByteBuffer buf, String id) {
        for (int i = 0; i < id.length(); i++) {
            buf.put((byte) id.charAt(i));
        }
    }

    private static ByteBuffer allocateLE(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }

    /** Open a byte-array fixture via the provider's stream entry point. */
    private static AudioSource openBytes(byte[] wav) throws IOException {
        WavAudioSourceProvider provider = new WavAudioSourceProvider();
        return provider.open(new ByteArrayInputStream(wav), /* hint */ null);
    }

    // =====================================================================
    // Chunk-walking edge cases
    // =====================================================================

    @Nested
    @DisplayName("Chunk-walking edge cases (Req 9.11)")
    class ChunkWalking {

        @Test
        @DisplayName("Odd-size chunk with trailing pad byte parses correctly")
        void oddSizeChunkWithPadByte() throws IOException {
            // Layout:
            //   RIFF | size | WAVE
            //   fmt  | 16   | <PCM16 mono @ 8000 Hz>
            //   JUNK | 3    | 'a' 'b' 'c' | pad(0x00)        <-- odd-size chunk + pad
            //   data | 4    | 0x01 0x00 0x02 0x00            <-- 2 frames
            int fmtBlockSize = 8 + 16;        // "fmt " + u32 + 16-byte payload
            int junkBlockSize = 8 + 3 + 1;    // "JUNK" + u32 + 3 bytes + pad
            int dataBlockSize = 8 + 4;        // "data" + u32 + 4 bytes
            int total = 12 + fmtBlockSize + junkBlockSize + dataBlockSize;

            ByteBuffer buf = allocateLE(total);
            putRiffHeader(buf, total - 8);
            putClassicFmt(buf, WAVE_FORMAT_PCM, 1, 8000, 16);
            // JUNK chunk with odd size.
            putAscii(buf, "JUNK");
            buf.putInt(3);
            buf.put((byte) 'a').put((byte) 'b').put((byte) 'c');
            buf.put((byte) 0x00);  // pad byte
            // data chunk: two PCM16 frames with distinctive values.
            byte[] samples = new byte[] {
                    0x01, 0x00,   // frame 0: +1
                    0x02, 0x00    // frame 1: +2
            };
            putDataChunk(buf, samples);

            try (AudioSource source = openBytes(buf.array())) {
                assertEquals(8000, source.sampleRate());
                assertEquals(1, source.channelCount());
                assertEquals(16, source.bitDepth());
                assertEquals(2L, source.totalFrames(),
                        "Parser must land on the 4-byte data payload "
                                + "after skipping the odd JUNK chunk + pad byte");

                double[] out = new double[4];
                int n = source.read(out, 0, 4);
                assertEquals(2, n, "Expected both frames to be decoded");
                // 1/32768 and 2/32768 — verify frame ordering.
                assertTrue(out[0] > 0.0 && out[0] < 1e-3,
                        "Frame 0 must decode as +1/32768, got " + out[0]);
                assertTrue(out[1] > out[0],
                        "Frame 1 must be larger than frame 0");
            }
        }

        @Test
        @DisplayName("LIST chunk before data is skipped")
        void listChunkBeforeDataIsSkipped() throws IOException {
            // LIST chunk with an 8-byte INFO-style payload between fmt
            // and data; parser must skip it and land on data.
            byte[] listPayload = new byte[] {
                    'I', 'N', 'F', 'O',                      // list type
                    'I', 'N', 'A', 'M',                      // sub-chunk id
                };
            int fmtBlockSize = 8 + 16;
            int listBlockSize = 8 + listPayload.length;
            byte[] samples = new byte[] { 0x05, 0x00 };      // one PCM16 frame
            int dataBlockSize = 8 + samples.length;
            int total = 12 + fmtBlockSize + listBlockSize + dataBlockSize;

            ByteBuffer buf = allocateLE(total);
            putRiffHeader(buf, total - 8);
            putClassicFmt(buf, WAVE_FORMAT_PCM, 1, 8000, 16);
            putAscii(buf, "LIST");
            buf.putInt(listPayload.length);
            buf.put(listPayload);
            putDataChunk(buf, samples);

            try (AudioSource source = openBytes(buf.array())) {
                assertEquals(1L, source.totalFrames(),
                        "Parser must land on the single-frame data payload "
                                + "after skipping the LIST chunk");
                double[] out = new double[1];
                int n = source.read(out, 0, 1);
                assertEquals(1, n);
            }
        }

        @Test
        @DisplayName("Extra chunks after data do not affect decoding (parser stops at data)")
        void extraChunksAfterDataAreIgnored() throws IOException {
            // RIFF | size | WAVE | fmt  | data(2 frames) | JUNK(8 bytes) | fact(4)
            byte[] samples = new byte[] { 0x0A, 0x00, 0x0B, 0x00 };
            int fmtBlockSize = 8 + 16;
            int dataBlockSize = 8 + samples.length;
            int junkBlockSize = 8 + 8;
            int factBlockSize = 8 + 4;
            int total = 12 + fmtBlockSize + dataBlockSize + junkBlockSize + factBlockSize;

            ByteBuffer buf = allocateLE(total);
            putRiffHeader(buf, total - 8);
            putClassicFmt(buf, WAVE_FORMAT_PCM, 1, 8000, 16);
            putDataChunk(buf, samples);
            // JUNK after data — must never be read.
            putAscii(buf, "JUNK");
            buf.putInt(8);
            for (int i = 0; i < 8; i++) buf.put((byte) 0xEE);
            // fact after data — also must never be read.
            putAscii(buf, "fact");
            buf.putInt(4);
            buf.putInt(2);

            try (AudioSource source = openBytes(buf.array())) {
                assertEquals(2L, source.totalFrames(),
                        "Parser must use the data chunk's own size (4 bytes = 2 frames)"
                                + " and ignore everything that follows");
                double[] out = new double[4];
                int n = source.read(out, 0, 4);
                assertEquals(2, n,
                        "Read must stop at the declared data size, not walk into JUNK");
                int end = source.read(out, 0, 4);
                assertEquals(-1, end, "Second read must signal EOS");
            }
        }
    }

    // =====================================================================
    // RF64 edge cases
    // =====================================================================

    @Nested
    @DisplayName("RF64 handling (Req 9.11)")
    class Rf64 {

        @Test
        @DisplayName("RF64 with ds64 first uses the 64-bit dataSize64, not the 0xFFFFFFFF marker")
        void rf64WithDs64UsesDataSize64() throws IOException {
            // Hand-craft a tiny RF64 where the on-disk data chunk carries
            // a realistic payload (4 bytes = 2 PCM16 frames), but the
            // data chunk's 32-bit size field is pinned to 0xFFFFFFFF so
            // the parser must consult ds64.dataSize64 instead.
            byte[] samples = new byte[] { 0x0C, 0x00, 0x0D, 0x00 };

            // ds64 payload: riffSize64(8) + dataSize64(8) + sampleCount64(8) + tableLength(4) = 28
            int ds64PayloadSize = 28;
            int ds64BlockSize = 8 + ds64PayloadSize;
            int fmtBlockSize = 8 + 16;
            int dataBlockSize = 8 + samples.length;
            int total = 12 + ds64BlockSize + fmtBlockSize + dataBlockSize;

            ByteBuffer buf = allocateLE(total);
            putRf64Header(buf);
            // ds64 chunk — must be first.
            putAscii(buf, "ds64");
            buf.putInt(ds64PayloadSize);
            buf.putLong(total - 8);           // riffSize64
            buf.putLong(samples.length);      // dataSize64 (the real value)
            buf.putLong(samples.length / 2);  // sampleCount64 (frames, informational)
            buf.putInt(0);                    // table length
            // fmt chunk.
            putClassicFmt(buf, WAVE_FORMAT_PCM, 1, 8000, 16);
            // data chunk with 32-bit size pinned to the overflow marker.
            putAscii(buf, "data");
            buf.putInt(0xFFFF_FFFF);          // 32-bit size = overflow marker
            buf.put(samples);

            try (AudioSource source = openBytes(buf.array())) {
                assertEquals(2L, source.totalFrames(),
                        "Parser must resolve totalFrames via ds64.dataSize64, "
                                + "not the 0xFFFFFFFF overflow marker");
                double[] out = new double[2];
                int n = source.read(out, 0, 2);
                assertEquals(2, n);
            }
        }

        @Test
        @DisplayName("RF64 without ds64 throws IOException")
        void rf64WithoutDs64IsRejected() {
            // RF64 outer magic but NO ds64 chunk anywhere — parser must
            // bail out with an IOException rather than silently falling
            // back to the 32-bit size field.
            byte[] samples = new byte[] { 0x01, 0x00 };
            int fmtBlockSize = 8 + 16;
            int dataBlockSize = 8 + samples.length;
            int total = 12 + fmtBlockSize + dataBlockSize;

            ByteBuffer buf = allocateLE(total);
            putRf64Header(buf);
            putClassicFmt(buf, WAVE_FORMAT_PCM, 1, 8000, 16);
            // data chunk with size = overflow marker and no ds64 to
            // resolve it.
            putAscii(buf, "data");
            buf.putInt(0xFFFF_FFFF);
            buf.put(samples);

            IOException ex = assertThrows(IOException.class,
                    () -> openBytes(buf.array()));
            // Don't assert on a UnsupportedAudioFormatException: this is a
            // structural defect (missing ds64), not an unsupported
            // encoding, so a plain IOException per Req 9.11 is correct.
            assertFalse(ex instanceof UnsupportedAudioFormatException,
                    "Missing ds64 is a structural defect, not an unsupported encoding");
            assertTrue(ex.getMessage() != null
                            && ex.getMessage().toLowerCase().contains("ds64"),
                    "Expected the message to mention 'ds64', got: " + ex.getMessage());
        }
    }

    // =====================================================================
    // Missing-chunk defects
    // =====================================================================

    @Nested
    @DisplayName("Structural defects (Req 9.11)")
    class StructuralDefects {

        @Test
        @DisplayName("Missing fmt chunk throws IOException")
        void missingFmtChunkIsRejected() {
            // Valid RIFF/WAVE header, data chunk present, but no fmt.
            byte[] samples = new byte[] { 0x00, 0x00, 0x00, 0x00 };
            int dataBlockSize = 8 + samples.length;
            int total = 12 + dataBlockSize;

            ByteBuffer buf = allocateLE(total);
            putRiffHeader(buf, total - 8);
            putDataChunk(buf, samples);

            IOException ex = assertThrows(IOException.class,
                    () -> openBytes(buf.array()));
            assertFalse(ex instanceof UnsupportedAudioFormatException,
                    "Missing fmt is a structural defect");
            assertTrue(ex.getMessage() != null
                            && ex.getMessage().contains("fmt"),
                    "Expected the message to mention 'fmt', got: " + ex.getMessage());
        }

        @Test
        @DisplayName("Missing data chunk throws IOException")
        void missingDataChunkIsRejected() {
            // Valid RIFF/WAVE header, fmt chunk present, but no data.
            int fmtBlockSize = 8 + 16;
            int total = 12 + fmtBlockSize;

            ByteBuffer buf = allocateLE(total);
            putRiffHeader(buf, total - 8);
            putClassicFmt(buf, WAVE_FORMAT_PCM, 1, 8000, 16);

            IOException ex = assertThrows(IOException.class,
                    () -> openBytes(buf.array()));
            assertFalse(ex instanceof UnsupportedAudioFormatException,
                    "Missing data is a structural defect");
            assertTrue(ex.getMessage() != null
                            && ex.getMessage().contains("data"),
                    "Expected the message to mention 'data', got: " + ex.getMessage());
        }
    }

    // =====================================================================
    // wFormatTag dispatch
    // =====================================================================

    @Nested
    @DisplayName("Unsupported wFormatTag (Req 9.10)")
    class UnsupportedFormatTag {

        @Test
        @DisplayName("wFormatTag 0x0007 (mu-law) throws UnsupportedAudioFormatException identifying the compression")
        void mulawFormatTagIsRejected() {
            // Build a file that would otherwise be well-formed, with
            // wFormatTag = 0x0007 (mu-law) and 8-bit samples (mu-law's
            // native container). The fmt chunk also advertises 8-bit
            // samples so the block-align check passes on the mu-law path
            // before the wFormatTag check rejects it... except the
            // wFormatTag check is evaluated BEFORE the bit-depth check
            // in parseFmtChunk, so even an "obviously wrong" bit depth
            // like 16 would also trigger the unsupported-format branch
            // first. Use 8 bits per sample here to match the natural
            // mu-law layout.
            byte[] samples = new byte[] { 0x00, 0x00 };
            int fmtBlockSize = 8 + 16;
            int dataBlockSize = 8 + samples.length;
            int total = 12 + fmtBlockSize + dataBlockSize;

            ByteBuffer buf = allocateLE(total);
            putRiffHeader(buf, total - 8);
            putClassicFmt(buf, WAVE_FORMAT_MULAW, 1, 8000, 8);
            putDataChunk(buf, samples);

            UnsupportedAudioFormatException ex = assertThrows(
                    UnsupportedAudioFormatException.class,
                    () -> openBytes(buf.array()));
            assertTrue(ex.getMessage() != null
                            && ex.getMessage().contains("0x0007"),
                    "Expected the message to contain '0x0007', got: "
                            + ex.getMessage());
            assertTrue(ex.getMessage().toLowerCase().contains("mu-law"),
                    "Expected the message to identify the compression as mu-law, got: "
                            + ex.getMessage());
        }
    }

    // =====================================================================
    // WAVEFORMATEXTENSIBLE dispatch
    // =====================================================================

    @Nested
    @DisplayName("WAVEFORMATEXTENSIBLE (Req 9.9)")
    class Extensible {

        /**
         * Write a 40-byte {@code WAVEFORMATEXTENSIBLE fmt } chunk.
         *
         * <p>Layout ({@code fmt } header included):
         * <pre>
         *   "fmt " | size=40 | wFormatTag=0xFFFE | nChannels |
         *   nSamplesPerSec | nAvgBytesPerSec | nBlockAlign |
         *   wBitsPerSample (container) | cbSize=22 |
         *   wValidBitsPerSample | dwChannelMask | SubFormat GUID(16)
         * </pre>
         */
        private void putExtensibleFmt(
                ByteBuffer buf,
                int channels,
                int sampleRate,
                int containerBits,
                int validBits,
                byte[] subFormatGuid) {
            int bytesPerSample = containerBits / 8;
            int blockAlign = channels * bytesPerSample;
            int avgBytesPerSec = sampleRate * blockAlign;
            putAscii(buf, "fmt ");
            buf.putInt(40);
            buf.putShort((short) WAVE_FORMAT_EXTENSIBLE);
            buf.putShort((short) channels);
            buf.putInt(sampleRate);
            buf.putInt(avgBytesPerSec);
            buf.putShort((short) blockAlign);
            buf.putShort((short) containerBits);
            // cbSize: 22 bytes of extension (validBits + channelMask + GUID)
            buf.putShort((short) 22);
            buf.putShort((short) validBits);
            buf.putInt(0);                    // dwChannelMask = 0 (unused)
            buf.put(subFormatGuid);
        }

        @Test
        @DisplayName("WAVEFORMATEXTENSIBLE with SUBTYPE_PCM GUID opens as PCM")
        void extensibleWithPcmGuidOpensAsPcm() throws IOException {
            // Mono, 16-bit PCM, 8 kHz, delivered via the EXTENSIBLE path.
            byte[] samples = new byte[] { 0x00, 0x10, 0x00, 0x20 };   // 2 frames
            int fmtBlockSize = 8 + 40;
            int dataBlockSize = 8 + samples.length;
            int total = 12 + fmtBlockSize + dataBlockSize;

            ByteBuffer buf = allocateLE(total);
            putRiffHeader(buf, total - 8);
            putExtensibleFmt(buf, 1, 8000, 16, 16, SUBTYPE_PCM_GUID);
            putDataChunk(buf, samples);

            try (AudioSource source = openBytes(buf.array())) {
                assertEquals(8000, source.sampleRate());
                assertEquals(1, source.channelCount());
                assertEquals(16, source.bitDepth(),
                        "EXTENSIBLE+SUBTYPE_PCM at container=16/valid=16 "
                                + "must decode as 16-bit PCM");
                assertEquals(2L, source.totalFrames());

                double[] out = new double[2];
                int n = source.read(out, 0, 2);
                assertEquals(2, n);
                // 0x1000 / 32768 ≈ 0.125, 0x2000 / 32768 ≈ 0.25
                assertEquals(0x1000 / 32768.0, out[0], 1e-12);
                assertEquals(0x2000 / 32768.0, out[1], 1e-12);
            }
        }

        @Test
        @DisplayName("WAVEFORMATEXTENSIBLE with A-law GUID throws UnsupportedAudioFormatException")
        void extensibleWithAlawGuidIsRejected() {
            // EXTENSIBLE path dispatches on the SubFormat GUID; A-law is
            // not one of the two accepted GUIDs.
            byte[] samples = new byte[] { 0x00, 0x10 };
            int fmtBlockSize = 8 + 40;
            int dataBlockSize = 8 + samples.length;
            int total = 12 + fmtBlockSize + dataBlockSize;

            ByteBuffer buf = allocateLE(total);
            putRiffHeader(buf, total - 8);
            putExtensibleFmt(buf, 1, 8000, 16, 16, SUBTYPE_ALAW_GUID);
            putDataChunk(buf, samples);

            UnsupportedAudioFormatException ex = assertThrows(
                    UnsupportedAudioFormatException.class,
                    () -> openBytes(buf.array()));
            // The GUID is identified by its canonical dashed-hex form;
            // 0x00000006 is the low-uint32 of the A-law GUID.
            assertTrue(ex.getMessage() != null
                            && ex.getMessage().toUpperCase().contains("00000006"),
                    "Expected the message to mention the A-law GUID 00000006, "
                            + "got: " + ex.getMessage());
            assertTrue(ex.getMessage().toLowerCase().contains("subformat")
                            || ex.getMessage().toLowerCase().contains("guid"),
                    "Expected the message to identify the offending SubFormat GUID, "
                            + "got: " + ex.getMessage());
        }

        @Test
        @DisplayName("wValidBitsPerSample=24 in a 32-bit container decodes as PCM32 (top 24 bits carry signal)")
        void extensibleValidBits24InContainer32DecodesAsPcm32() throws IOException {
            // Build a file with containerBits=32 and validBits=24. Each
            // frame holds a 32-bit integer with its low 8 bits set to
            // zero; the top 24 bits carry a known signal value. The
            // decoder treats the container as 32-bit PCM and divides
            // by 2^31, so the normalized result equals
            //    signalValue24 << 8 / 2^31
            //  = signalValue24 / 2^23
            // i.e. identical to straight 24-bit PCM decoding.
            //
            // Pack frame 0 as the integer 0x00000100 — that is, the
            // 24-bit signal value 1, shifted 8 bits into the 32-bit
            // container's high bits.
            //
            // Expected normalized value: 0x00000100 / 2^31
            //                          = 256 / 2147483648
            //                          = 1 / 8388608 ≈ 1.1920929e-7
            ByteBuffer sampleBuf = allocateLE(4);
            sampleBuf.putInt(0x00000100);
            byte[] samples = sampleBuf.array();

            int fmtBlockSize = 8 + 40;
            int dataBlockSize = 8 + samples.length;
            int total = 12 + fmtBlockSize + dataBlockSize;

            ByteBuffer buf = allocateLE(total);
            putRiffHeader(buf, total - 8);
            putExtensibleFmt(buf, 1, 8000, /* containerBits */ 32,
                    /* validBits */ 24, SUBTYPE_PCM_GUID);
            putDataChunk(buf, samples);

            try (AudioSource source = openBytes(buf.array())) {
                assertEquals(32, source.bitDepth(),
                        "Container bit depth drives the decoder; validBits "
                                + "only controls how many bits carry signal");
                assertEquals(4, source.channelCount() * (source.bitDepth() / 8),
                        "Bytes per frame must equal container bytes for mono");
                assertEquals(1L, source.totalFrames());

                double[] out = new double[1];
                int n = source.read(out, 0, 1);
                assertEquals(1, n);
                // 0x00000100 as a little-endian 32-bit signed int is
                // 256; divided by 2^31 gives 256 / 2_147_483_648.
                double expected = 256.0 / 2147483648.0;
                assertEquals(expected, out[0], 1e-18,
                        "A 24-bit signal value of 1 in the top bits of a "
                                + "32-bit container must normalize to 1/2^23 "
                                + "after the 2^31 divisor");
            }
        }
    }

    // =====================================================================
    // Stereo interleaving round-trip
    // =====================================================================

    @Nested
    @DisplayName("Stereo PCM16 interleaving (Req 3.5, 9.7)")
    class StereoInterleaving {

        @Test
        @DisplayName("Stereo PCM16 round-trip: L at even indices, R at odd indices")
        void stereoPcm16RoundTripInterleavesLeftAndRight(@TempDir Path dir) throws IOException {
            // Use deliberately asymmetric left/right envelopes so any
            // channel-swap bug or off-by-one interleave slip is obvious
            // from the numeric assertions.
            double[] left = new double[] {
                    0.25, 0.50, -0.25, -0.50
            };
            double[] right = new double[] {
                    -0.10, 0.10, -0.40, 0.40
            };
            byte[] wav = WavEncoder.encodePcm16Stereo(left, right, 8000);

            // Route through open(Path) so we exercise the channel-backed
            // source in addition to the stream-backed one other tests
            // lean on; seekability is Req 9.13 territory.
            Path file = Files.createTempFile(dir, "stereo", ".wav");
            Files.write(file, wav);

            WavAudioSourceProvider provider = new WavAudioSourceProvider();
            try (AudioSource source = provider.open(file)) {
                assertEquals(2, source.channelCount());
                assertEquals(16, source.bitDepth());
                assertEquals(4L, source.totalFrames());

                double[] interleaved = new double[2 * 4];
                int n = source.read(interleaved, 0, 4);
                assertEquals(4, n, "All four frames must decode");

                // Tolerance matches PCM16 quantization: worst-case
                // 1/32768 round-trip error per sample.
                double tol = 1.0 / 32768.0 + 1e-12;

                // L channel lives at even indices; R at odd indices.
                for (int frame = 0; frame < 4; frame++) {
                    double actualL = interleaved[2 * frame];
                    double actualR = interleaved[2 * frame + 1];
                    assertEquals(left[frame], actualL, tol,
                            "Left channel mismatch at frame " + frame
                                    + " (expected at even index " + (2 * frame) + ")");
                    assertEquals(right[frame], actualR, tol,
                            "Right channel mismatch at frame " + frame
                                    + " (expected at odd index " + (2 * frame + 1) + ")");
                }

                // Extra guard: the signed sign pattern of L and R in the
                // first frame differs, so if any implementation accidentally
                // swapped L and R, the numerical closeness checks above
                // already flagged it — but pin the sign too so a future
                // "fix" cannot accidentally restore the interleaving bug
                // while keeping magnitudes correct.
                assertTrue(interleaved[0] > 0.0,
                        "L[0] must decode to a positive sample (+0.25)");
                assertTrue(interleaved[1] < 0.0,
                        "R[0] must decode to a negative sample (-0.10)");
            }
        }

        @Test
        @DisplayName("Stereo PCM16 round-trip via byte[] array matches the on-disk layout")
        void stereoPcm16RoundTripMatchesRawByteLayout() throws IOException {
            // Second flavour of the interleaving check: inspect the raw
            // data payload directly and confirm that the PCM16 little-
            // endian layout is L0, R0, L1, R1, ... That test exists to
            // pin the encoder side too, in case a refactor ever
            // transposes channels at encode time and decodes also flip
            // them back symmetrically so the higher-level test above
            // would still pass.
            double[] left = new double[] { 0.25 };
            double[] right = new double[] { -0.50 };
            byte[] wav = WavEncoder.encodePcm16Stereo(left, right, 8000);

            // Payload starts at byte 44 (12-byte outer + 24-byte fmt +
            // 8-byte data header). Each frame is 4 bytes (2 channels ×
            // 2 bytes/sample).
            byte[] payload = new byte[4];
            System.arraycopy(wav, 44, payload, 0, 4);

            // Expected PCM16 little-endian:
            //   L: round(0.25 * 32768) = 8192 = 0x2000 → {0x00, 0x20}
            //   R: round(-0.50 * 32768) = -16384 = 0xC000 → {0x00, 0xC0}
            byte[] expected = new byte[] {
                    0x00, 0x20,     // L at even frame offset
                    0x00, (byte) 0xC0  // R at odd frame offset
            };
            assertArrayEquals(expected, payload,
                    "PCM16 stereo data payload must be laid out L, R per frame");
        }
    }
}
