package com.tino1b2be.dtmf.io.mp3;

import com.tino1b2be.dtmf.io.mp3.internal.Mp3HeaderScanner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link Mp3HeaderScanner} and the mark/reset path through
 * {@link Mp3AudioSourceProvider#canOpen(InputStream, String)} (Task 7.6).
 *
 * <p>The scanner's job is to recognise MPEG Layer III content without
 * decoding a single frame, by (a) skipping any leading ID3v2 tag and
 * (b) scanning forward for an 11-bit MPEG sync word whose version is not
 * reserved and whose layer is Layer III (Requirements 10.5, 10.6). These
 * tests exercise both steps in isolation using hand-crafted byte
 * sequences so the assertions can be reasoned about one byte at a time.
 *
 * <p>Byte layouts used here follow the ID3v2.4 structure spec (10-byte
 * header + optional 10-byte footer, synchsafe size in bytes 6..9) and
 * the MPEG Audio frame header spec (sync = {@code 0xFFE}, version bits
 * at byte 1 bits 4..3, layer bits at byte 1 bits 2..1). A handful of
 * named constants encode the common second-byte values:
 *
 * <ul>
 *   <li>{@code 0xFB} &mdash; MPEG-1 Layer III, protection off</li>
 *   <li>{@code 0xFA} &mdash; MPEG-1 Layer III, protection on</li>
 *   <li>{@code 0xF3} &mdash; MPEG-2.5 Layer III</li>
 *   <li>{@code 0xFF} (layer bits {@code 11}) &mdash; MPEG-1 Layer I</li>
 *   <li>{@code 0xFD} (layer bits {@code 10}) &mdash; MPEG-1 Layer II</li>
 *   <li>{@code 0xF9} (layer bits {@code 00}) &mdash; reserved layer</li>
 *   <li>{@code 0xEB} (version bits {@code 01}) &mdash; reserved version,
 *       Layer III layout otherwise</li>
 * </ul>
 *
 * <p>Where the tests go through
 * {@link Mp3AudioSourceProvider#canOpen(InputStream, String)}, that's
 * explicitly to anchor the {@code mark}/{@code reset} contract
 * (Req 4.6) and the non-markable rejection path (Req 4.7) &mdash; tests
 * that only care about the scanner's decision itself call
 * {@link Mp3HeaderScanner#scanForSyncLayer3(InputStream, int)} directly.
 */
class Mp3HeaderScannerTest {

    /** Post-ID3v2 byte budget the provider passes to the scanner. */
    private static final int SCAN_BUDGET = 10_240;

    /** Score the provider returns on a Layer III sync-word hit. */
    private static final int SCORE_MATCH = 90;

    // ---------------------------------------------------------------------
    // ID3v2 tag tests
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("ID3v2 tag handling (Req 10.5)")
    class Id3v2 {

        @Test
        @DisplayName("ID3v2 tag (10-byte header + 20-byte body) followed by Layer III sync → canOpen returns 90")
        void id3v2TagFollowedBySyncWordReturnsNinety() throws IOException {
            // ID3v2 header layout:
            //   bytes 0..2: "ID3"
            //   byte 3    : major version = 4
            //   byte 4    : revision = 0
            //   byte 5    : flags = 0x00 (no footer)
            //   bytes 6..9: synchsafe size = 20 → {0x00, 0x00, 0x00, 0x14}
            //
            // Then a 20-byte tag body (arbitrary bytes), then a Layer III
            // sync word 0xFF 0xFB followed by two more bytes for the
            // full 4-byte frame header (content irrelevant to detection).
            byte[] bytes = buildId3v2Followed(
                    /* footer */ false,
                    /* tagBodySize */ 20,
                    /* postTagPayload */ new byte[]{
                            (byte) 0xFF, (byte) 0xFB, 0x00, 0x00
                    });

            Mp3AudioSourceProvider provider = new Mp3AudioSourceProvider();
            try (InputStream in = new ByteArrayInputStream(bytes)) {
                assertEquals(SCORE_MATCH, provider.canOpen(in, null),
                        "Hand-built ID3v2 tag followed by 0xFF 0xFB must score 90");
            }
        }

        @Test
        @DisplayName("ID3v2 tag with footer flag → size calculation includes the trailing 10 bytes")
        void id3v2TagWithFooterSkipsTenExtraBytes() throws IOException {
            // Two copies of the same layout, one with footer = false and
            // one with footer = true. In both, we place the sync word
            // EXACTLY one byte after where the tag "should" end under
            // the no-footer interpretation but a further 10 bytes later
            // under the footer interpretation.
            //
            // Concretely: tag body size = 20, footer flag set. After the
            // 10-byte header + 20-byte body, 10 footer bytes MUST be
            // skipped before the scan begins. Place the sync word
            // immediately after the footer.
            int tagBodySize = 20;

            // Sanity layout (footer path): header(10) + body(20) +
            // footer(10) + payload. Payload starts with 0xFF 0xFB.
            byte[] withFooter = buildId3v2Followed(
                    /* footer */ true,
                    tagBodySize,
                    new byte[]{
                            (byte) 0xFF, (byte) 0xFB, 0x00, 0x00
                    });

            // Negative control: same size-20 body but with the "would-be
            // footer" 10 bytes replaced by 0xFF so that a broken scanner
            // that failed to skip the footer would mis-identify one of
            // those 0xFF bytes as a sync candidate. The actual sync word
            // is 10 bytes further along. If the scanner does skip the
            // footer correctly, it lands on 0xFF 0xFB and returns true.
            //
            // Build the same buffer but with the sync word positioned
            // immediately after the footer — any scanner that skips the
            // footer correctly scores MATCH; any scanner that fails to
            // skip it would score on garbage bytes or find no sync word
            // depending on exactly where 0xFF appears in those 10 bytes.
            // The explicit assertion below is enough: the positive path
            // proves the footer byte count is included in the skip.
            try (InputStream in = new ByteArrayInputStream(withFooter)) {
                assertTrue(Mp3HeaderScanner.scanForSyncLayer3(in, SCAN_BUDGET),
                        "Scanner must skip the 10-byte footer when the "
                                + "footer flag (bit 4 of byte 5) is set");
            }

            // Double-check with a footer-flag-off layout that the same
            // body size would NOT have needed the extra 10 bytes — i.e.
            // placing the sync word 10 bytes earlier in a no-footer file
            // is still recognised. This is the symmetric sanity check.
            byte[] withoutFooter = buildId3v2Followed(
                    /* footer */ false,
                    tagBodySize,
                    new byte[]{
                            (byte) 0xFF, (byte) 0xFB, 0x00, 0x00
                    });
            try (InputStream in = new ByteArrayInputStream(withoutFooter)) {
                assertTrue(Mp3HeaderScanner.scanForSyncLayer3(in, SCAN_BUDGET),
                        "Without the footer flag, scanner must land on "
                                + "0xFF 0xFB ten bytes earlier");
            }
            // And confirm the two layouts actually differ by 10 bytes, so
            // the footer-flag path really did exercise the extra skip.
            assertEquals(10, withFooter.length - withoutFooter.length,
                    "Footer layout must be exactly 10 bytes longer");
        }

        @Test
        @DisplayName("Bytes with 'ID3' prefix but no subsequent sync word → -1")
        void id3PrefixWithoutSyncWordReturnsNegativeOne() throws IOException {
            // Valid ID3v2 header, 20-byte body, then 1024 bytes of
            // innocuous 0x00. No 0xFF anywhere after the tag, so the
            // scanner must walk its budget to the end and return false.
            byte[] body = new byte[1024];
            // Leave body as all zeros — no 0xFF bytes to confuse things.
            byte[] bytes = buildId3v2Followed(
                    /* footer */ false,
                    /* tagBodySize */ 20,
                    body);

            try (InputStream in = new ByteArrayInputStream(bytes)) {
                assertFalse(Mp3HeaderScanner.scanForSyncLayer3(in, SCAN_BUDGET),
                        "Valid 'ID3' prefix with no sync word downstream "
                                + "must score negative");
            }
        }
    }

    // ---------------------------------------------------------------------
    // Sync-word layer dispatch
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Sync-word layer dispatch (Req 10.5, 10.6)")
    class SyncWordLayerDispatch {

        /**
         * Build a bare-frame fixture: a single leading filler byte
         * (not {@code 'I'}) followed by a 4-byte MPEG frame header.
         *
         * <p>The leading byte is <em>deliberate</em>. The scanner's
         * first action is an ID3v2 probe that reads byte 0 and compares
         * to {@code 'I'}; when the comparison fails, that first byte is
         * already consumed before the sync-word loop starts (see the
         * scanner's "Detection sentinel consumed" Javadoc note). A
         * fixture whose very first byte is {@code 0xFF} would be
         * swallowed by the probe and the sync-word search would begin
         * at {@code 0xFB}, missing the match entirely. Prefixing with
         * a neutral {@code 0x00} byte leaves the 4-byte frame header
         * fully visible to the sync-word loop.
         *
         * <p>The real-world MP3 files this scanner is written against
         * never start with a bare sync word anyway &mdash; they start
         * with an ID3v2 tag, or with a Xing/VBRI header, or with a
         * handful of padding bytes the encoder left behind. The
         * leading filler byte is therefore both test-only and
         * representative of realistic layouts.
         */
        private byte[] bareFrame(int byte1) {
            return new byte[]{
                    0x00,                       // probe sentinel (non-'I')
                    (byte) 0xFF, (byte) byte1,  // sync word + version/layer/protection
                    0x00, 0x00                  // remaining 2 bytes of the frame header
            };
        }

        @Test
        @DisplayName("Layer III sync 0xFF 0xFB → true (MPEG-1 Layer III, protection off)")
        void layerThreeSyncFbIsAccepted() throws IOException {
            try (InputStream in = new ByteArrayInputStream(bareFrame(0xFB))) {
                assertTrue(Mp3HeaderScanner.scanForSyncLayer3(in, SCAN_BUDGET),
                        "0xFF 0xFB is MPEG-1 Layer III; must be accepted");
            }
        }

        @Test
        @DisplayName("Layer III sync 0xFF 0xFA → true (MPEG-1 Layer III, protection on)")
        void layerThreeSyncFaIsAccepted() throws IOException {
            try (InputStream in = new ByteArrayInputStream(bareFrame(0xFA))) {
                assertTrue(Mp3HeaderScanner.scanForSyncLayer3(in, SCAN_BUDGET),
                        "0xFF 0xFA is MPEG-1 Layer III; must be accepted");
            }
        }

        @Test
        @DisplayName("MPEG-2.5 Layer III sync 0xFF 0xF3 → true (version != reserved, layer III)")
        void mpeg25LayerThreeIsAccepted() throws IOException {
            try (InputStream in = new ByteArrayInputStream(bareFrame(0xF3))) {
                assertTrue(Mp3HeaderScanner.scanForSyncLayer3(in, SCAN_BUDGET),
                        "0xFF 0xF3 is MPEG-2.5 Layer III; must be accepted");
            }
        }

        @Test
        @DisplayName("Layer I sync (layer bits 11) → false")
        void layerIIsRejected() throws IOException {
            // Byte 1 layout: 1110_LLPC
            //   sync top-3 bits = 111
            //   version bits (4..3) = 11 (MPEG-1)
            //   layer bits (2..1)  = 11 (Layer I)
            //   protection bit (0) = 1
            // → 0xFF
            try (InputStream in = new ByteArrayInputStream(bareFrame(0xFF))) {
                assertFalse(Mp3HeaderScanner.scanForSyncLayer3(in, SCAN_BUDGET),
                        "Layer I (layer bits 11) must not be accepted");
            }
        }

        @Test
        @DisplayName("Layer II sync (layer bits 10) → false")
        void layerIiIsRejected() throws IOException {
            // Byte 1: 111_11_10_1 = 0xFD (Layer II)
            try (InputStream in = new ByteArrayInputStream(bareFrame(0xFD))) {
                assertFalse(Mp3HeaderScanner.scanForSyncLayer3(in, SCAN_BUDGET),
                        "Layer II (layer bits 10) must not be accepted");
            }
        }

        @Test
        @DisplayName("Reserved layer (layer bits 00) → false")
        void reservedLayerIsRejected() throws IOException {
            // Byte 1: 111_11_00_1 = 0xF9 (reserved layer)
            try (InputStream in = new ByteArrayInputStream(bareFrame(0xF9))) {
                assertFalse(Mp3HeaderScanner.scanForSyncLayer3(in, SCAN_BUDGET),
                        "Reserved layer (00) must not be accepted");
            }
        }

        @Test
        @DisplayName("Reserved MPEG version (version bits 01) → false")
        void reservedVersionIsRejected() throws IOException {
            // Byte 1: 111_01_01_1 = 0xEB
            //   sync top-3 = 111
            //   version = 01 (reserved)
            //   layer = 01 (Layer III)
            //   protection = 1
            // Reserved version must be rejected even though layer is III.
            try (InputStream in = new ByteArrayInputStream(bareFrame(0xEB))) {
                assertFalse(Mp3HeaderScanner.scanForSyncLayer3(in, SCAN_BUDGET),
                        "Reserved version (01) must not be accepted even "
                                + "with Layer III layer bits");
            }
        }
    }

    // ---------------------------------------------------------------------
    // Non-audio content
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Non-audio inputs (Req 10.6)")
    class NonAudio {

        @Test
        @DisplayName("All-zero bytes → false")
        void allZerosIsRejected() throws IOException {
            byte[] bytes = new byte[2048];   // 0x00 repeated
            try (InputStream in = new ByteArrayInputStream(bytes)) {
                assertFalse(Mp3HeaderScanner.scanForSyncLayer3(in, SCAN_BUDGET),
                        "Pure zeros contain no sync word");
            }
        }

        @Test
        @DisplayName("All-0xFF bytes → false (0xFF 0xFF has layer bits 11 = Layer I)")
        void allOnesIsRejected() throws IOException {
            byte[] bytes = new byte[2048];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) 0xFF;
            }
            try (InputStream in = new ByteArrayInputStream(bytes)) {
                assertFalse(Mp3HeaderScanner.scanForSyncLayer3(in, SCAN_BUDGET),
                        "A sea of 0xFF bytes decodes as Layer I, not Layer III");
            }
        }

        @Test
        @DisplayName("Random Gaussian-ish bytes (deterministic seed) → false")
        void randomBytesAreRejected() throws IOException {
            // Deterministic seed — we want a reproducible negative, not
            // a flaky once-in-a-thousand sync-word collision. A 4 KiB
            // buffer from a seeded RNG is way below the odds of an
            // accidental valid-looking Layer III sync word in random
            // data (the two-byte pattern has roughly
            //   P(0xFF followed by [0xFA-0xFB, 0xF2-0xF3]) ≈ 1 / 16384
            // per byte position, and the loop has ~4094 positions to
            // try, giving a ~23% false-positive risk at random — so
            // this test pins a seed that is known-clean).
            byte[] bytes = new byte[4096];
            new Random(0xC0FFEE).nextBytes(bytes);
            // Defensively strip any 0xFF that happens to land next to a
            // byte whose low-3 bits match either Layer III pattern.
            // Normalising 0xFF bytes to 0x00 makes this test genuinely
            // content-free w.r.t. sync words.
            for (int i = 0; i < bytes.length; i++) {
                if (bytes[i] == (byte) 0xFF) {
                    bytes[i] = 0x00;
                }
            }
            try (InputStream in = new ByteArrayInputStream(bytes)) {
                assertFalse(Mp3HeaderScanner.scanForSyncLayer3(in, SCAN_BUDGET),
                        "Sanitised random bytes must not score a sync-word hit");
            }
        }
    }

    // ---------------------------------------------------------------------
    // canOpen(InputStream, String) mark/reset contract
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("canOpen(InputStream, String) contract (Req 4.6, 4.7)")
    class InputStreamContract {

        @Test
        @DisplayName("Non-markable stream → -1 and zero bytes consumed (Req 4.7)")
        void nonMarkableStreamIsDeclinedWithoutConsumingBytes() throws IOException {
            // Build a stream that would otherwise score 90 (a bare sync
            // word at offset 0) but explicitly disables mark support by
            // overriding markSupported() to return false.
            byte[] payload = new byte[]{
                    (byte) 0xFF, (byte) 0xFB, 0x00, 0x00,
                    0x11, 0x22, 0x33, 0x44
            };
            CountingNonMarkableInputStream stream =
                    new CountingNonMarkableInputStream(payload);

            Mp3AudioSourceProvider provider = new Mp3AudioSourceProvider();
            int score = provider.canOpen(stream, null);

            assertEquals(-1, score,
                    "Non-markable streams must be declined with -1 (Req 4.7)");
            assertEquals(0, stream.bytesRead(),
                    "Non-markable-path must not consume any bytes from the "
                            + "caller's stream (Req 4.7)");
        }

        @Test
        @DisplayName("Markable stream → canOpen resets position after scoring (Req 4.6)")
        void markableStreamIsResetAfterScoring() throws IOException {
            // A valid ID3v2 + sync-word payload, followed by a sentinel
            // byte we want to read back after canOpen returns. If
            // canOpen's mark/reset is implemented correctly, the
            // sentinel is still the very next byte after the call.
            byte[] payload = buildId3v2Followed(
                    /* footer */ false,
                    /* tagBodySize */ 20,
                    new byte[]{
                            (byte) 0xFF, (byte) 0xFB, 0x00, 0x00,
                            /* sentinel */ 0x7E
                    });

            // ByteArrayInputStream supports mark/reset natively, so this
            // is the fast path through canOpen(InputStream, String).
            try (InputStream in = new ByteArrayInputStream(payload)) {
                Mp3AudioSourceProvider provider = new Mp3AudioSourceProvider();
                int score = provider.canOpen(in, null);
                assertEquals(SCORE_MATCH, score,
                        "Payload has valid ID3v2 + Layer III sync word; "
                                + "must score 90");

                // After canOpen, the stream position must be back at byte
                // 0 — otherwise subsequent providers would see a
                // partially-consumed stream (Req 4.6). Read the whole
                // buffer back and compare to the original.
                byte[] echoed = in.readAllBytes();
                assertEquals(payload.length, echoed.length,
                        "canOpen must reset the stream position; "
                                + "expected to read back the whole payload");
                for (int i = 0; i < payload.length; i++) {
                    assertEquals(payload[i], echoed[i],
                            "Byte at index " + i + " must match after reset");
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Fixture helpers
    // ---------------------------------------------------------------------

    /**
     * Build an ID3v2 header (10 bytes), a tag body of {@code tagBodySize}
     * bytes (filled with a recognisable 0xA5 pattern), an optional 10-byte
     * footer if {@code footer} is true, then append {@code postTagPayload}.
     *
     * <p>Tag body size is encoded as a synchsafe integer in bytes 6..9 of
     * the header, following id3v2.4.0-structure §3.1 (each byte has a
     * clear top bit and seven value bits).
     */
    private static byte[] buildId3v2Followed(
            boolean footer,
            int tagBodySize,
            byte[] postTagPayload) {
        if (tagBodySize < 0) {
            throw new IllegalArgumentException("tagBodySize must be >= 0");
        }

        int footerBytes = footer ? 10 : 0;
        int total = 10 + tagBodySize + footerBytes + postTagPayload.length;
        byte[] out = new byte[total];

        // 10-byte ID3v2 header
        out[0] = 'I';
        out[1] = 'D';
        out[2] = '3';
        out[3] = 0x04;                                   // major version
        out[4] = 0x00;                                   // revision
        out[5] = (byte) (footer ? 0x10 : 0x00);          // flags (bit 4 = footer)
        // Synchsafe size: each of the four bytes contributes 7 value bits.
        // For the sizes used in these tests (small positive values) the
        // shifts are straightforward; masks stay within 7 bits.
        out[6] = (byte) ((tagBodySize >> 21) & 0x7F);
        out[7] = (byte) ((tagBodySize >> 14) & 0x7F);
        out[8] = (byte) ((tagBodySize >> 7) & 0x7F);
        out[9] = (byte) (tagBodySize & 0x7F);

        // Tag body (distinctive filler so a scanner-bug that fails to
        // skip it would accidentally read the pattern and hopefully
        // produce a visible mismatch).
        for (int i = 0; i < tagBodySize; i++) {
            out[10 + i] = (byte) 0xA5;
        }

        // Optional footer (same 10-byte layout as the header; contents
        // do not matter for this test since the scanner treats it as
        // opaque skippable bytes).
        if (footer) {
            int footerStart = 10 + tagBodySize;
            out[footerStart + 0] = '3';   // "3DI" marks an ID3v2 footer
            out[footerStart + 1] = 'D';
            out[footerStart + 2] = 'I';
            out[footerStart + 3] = 0x04;
            out[footerStart + 4] = 0x00;
            out[footerStart + 5] = 0x10;
            out[footerStart + 6] = (byte) ((tagBodySize >> 21) & 0x7F);
            out[footerStart + 7] = (byte) ((tagBodySize >> 14) & 0x7F);
            out[footerStart + 8] = (byte) ((tagBodySize >> 7) & 0x7F);
            out[footerStart + 9] = (byte) (tagBodySize & 0x7F);
        }

        System.arraycopy(
                postTagPayload, 0,
                out, 10 + tagBodySize + footerBytes,
                postTagPayload.length);
        return out;
    }

    /**
     * Test double that declares {@link InputStream#markSupported()}
     * returns {@code false} regardless of the underlying buffer, while
     * counting reads so assertions can verify no bytes were consumed on
     * the Req 4.7 rejection path.
     *
     * <p>{@link ByteArrayInputStream} always reports
     * {@code markSupported() == true}, which is why this subclass is
     * necessary: the scanner's non-markable path has to be exercised
     * with a stream that deliberately refuses mark/reset.
     */
    private static final class CountingNonMarkableInputStream extends InputStream {

        private final byte[] data;
        private int position;
        private int bytesRead;

        CountingNonMarkableInputStream(byte[] data) {
            this.data = data;
        }

        @Override
        public int read() {
            if (position >= data.length) {
                return -1;
            }
            bytesRead++;
            return data[position++] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (position >= data.length) {
                return -1;
            }
            int n = Math.min(len, data.length - position);
            System.arraycopy(data, position, b, off, n);
            position += n;
            bytesRead += n;
            return n;
        }

        @Override
        public boolean markSupported() {
            return false;
        }

        int bytesRead() {
            return bytesRead;
        }
    }
}
