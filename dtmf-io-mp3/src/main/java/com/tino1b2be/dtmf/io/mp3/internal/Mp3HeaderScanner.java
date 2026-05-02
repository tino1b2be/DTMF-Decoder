package com.tino1b2be.dtmf.io.mp3.internal;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * MPEG audio header detection helper for
 * {@code com.tino1b2be.dtmf.io.mp3.Mp3AudioSourceProvider}.
 *
 * <p>Decides, in two steps and without decoding a single audio frame,
 * whether the bytes flowing through a given {@link InputStream} plausibly
 * belong to an MPEG Layer III stream &mdash; i.e. whether the provider
 * should claim them in the SPI scoring round conducted by
 * {@code AudioSources}:
 *
 * <ol>
 *   <li><strong>Skip any leading ID3v2 tag.</strong> An MP3 on disk is
 *       almost always preceded by an ID3v2 tag that carries the track
 *       title, artist, and so on. The tag sits <em>before</em> the first
 *       audio frame and is trivial to identify: its first three bytes are
 *       the ASCII sequence {@code "ID3"}. The spec (ID3.org's
 *       {@code id3v2.4.0-structure}) lays the ten-byte tag header out as
 *       <blockquote>
 *       {@code bytes 0..2: "ID3"}<br>
 *       {@code byte 3   : major version}<br>
 *       {@code byte 4   : revision}<br>
 *       {@code byte 5   : flags (bit 4 = footer present)}<br>
 *       {@code bytes 6..9: synchsafe tag size}
 *       </blockquote>
 *       the "synchsafe integer" being the unusual part: each of the four
 *       bytes holds a clear top bit and only seven value bits, so
 *       {@code size = (b6 << 21) | (b7 << 14) | (b8 << 7) | b9}. A tag
 *       optionally repeats its ten-byte header as a trailing footer when
 *       bit&nbsp;4 of the flags byte is set, so the total number of bytes
 *       to skip before audio starts is
 *       {@code 10 (header) + size + (10 if footer else 0)}. Anything other
 *       than {@code "ID3"} in the first three bytes means no tag is
 *       present; those three bytes stay at the head of the scan and the
 *       scanner just starts looking for a sync word immediately.
 *   <li><strong>Scan up to {@code maxBytes} for an MPEG sync word.</strong>
 *       Every MPEG audio frame starts with an eleven-bit sync pattern of
 *       all ones &mdash; {@code 0xFFE} &mdash; laid out across the first
 *       two bytes of the four-byte frame header as {@code 0xFF} followed
 *       by a byte whose top three bits are {@code 111}. Finding
 *       {@code prev == 0xFF && (cur & 0xE0) == 0xE0} is therefore
 *       necessary for MPEG but not sufficient: the second byte also
 *       carries the two-bit {@code versionField} at bits&nbsp;4..3 and the
 *       two-bit {@code layerField} at bits&nbsp;2..1, and only the
 *       combination {@code versionField != 01} (00 = MPEG-2.5, 10 =
 *       MPEG-2, 11 = MPEG-1; 01 is reserved) with
 *       {@code layerField == 01} (Layer III; 00 = reserved, 10 = Layer II,
 *       11 = Layer I) indicates the content this provider can actually
 *       decode. Any other combination is either a spurious {@code 0xFF}
 *       byte inside an ID3v1 trailer, an unsupported layer, or a reserved
 *       field, and the scan keeps moving. The loop returns {@code true}
 *       on the first combination that does pass both checks and
 *       {@code false} if it walks {@code maxBytes} past the tag without
 *       finding one.
 * </ol>
 *
 * <p><strong>Why content-based detection, not extension-based?</strong>
 * {@code AudioSources} picks a provider by asking each one to score the
 * raw bytes, not the file name (see Requirements 4.4, 4.5, 5.6). A
 * {@code .mp3} file with a corrupted header must score low so the caller
 * gets an {@code UnsupportedAudioFormatException} rather than a confusing
 * decoder failure; a stream of MPEG Layer III bytes with no {@code .mp3}
 * extension (a URL ending in {@code /audio}, say) must still score high.
 * This scanner is the mechanism that makes both happen.
 *
 * <p><strong>What the scanner does not do.</strong> Finding a valid-looking
 * Layer III sync header is enough for {@code canOpen(...)} to return 90
 * (Requirement 10.5) &mdash; it does <em>not</em> guarantee that
 * {@code mp3spi} will be able to decode every subsequent frame. A
 * malformed or truncated file can still fall over at {@code open(...)}
 * time, at which point {@code Mp3AudioSourceProvider} translates the
 * underlying failure into {@code UnsupportedAudioFormatException}
 * (Requirement 10.8). The two-phase "score cheaply, decode carefully"
 * contract is deliberate.
 *
 * <p><strong>This class is not part of the published API.</strong> It lives
 * in {@code com.tino1b2be.dtmf.io.mp3.internal}, whose stability contract
 * (see the package Javadoc) explicitly allows breakage between any two
 * releases. It is {@code public} at the type level purely so
 * {@code Mp3AudioSourceProvider} in the parent package can reach it;
 * external callers MUST NOT depend on it.
 *
 * <p><strong>Thread safety.</strong> The class holds no mutable state.
 * The single exposed method is a pure function of its
 * {@link InputStream} argument, and threading concerns therefore reduce
 * entirely to whether the caller-supplied stream is safe to read from
 * concurrently &mdash; a question outside this scanner's scope.
 *
 * @since 2.1.0
 */
public final class Mp3HeaderScanner {

    /** ID3v2 tag header size in bytes: {@code "ID3" + version + revision + flags + synchsafe size}. */
    private static final int ID3V2_HEADER_SIZE = 10;

    /** ID3v2 optional footer size in bytes (identical layout to the header). */
    private static final int ID3V2_FOOTER_SIZE = 10;

    /**
     * Bit mask for the "footer present" flag in ID3v2 header byte&nbsp;5.
     * The ID3v2.4 spec defines bits 7..4 as flag bits (unsync, extended
     * header, experimental, footer); the footer flag is bit&nbsp;4, i.e.
     * {@code 0x10}.
     */
    private static final int ID3V2_FLAG_FOOTER = 0x10;

    /**
     * Upper-byte of the eleven-bit MPEG sync pattern. The first byte of a
     * frame header is always exactly {@code 0xFF}.
     */
    private static final int SYNC_FIRST_BYTE = 0xFF;

    /**
     * Mask for the top three bits of the second byte of an MPEG frame
     * header. The eleven-bit sync word {@code 0xFFE} means the second
     * byte's top three bits are all ones; masking with {@code 0xE0} and
     * comparing to {@code 0xE0} tests exactly that.
     */
    private static final int SYNC_SECOND_BYTE_MASK = 0xE0;

    /**
     * Encoded MPEG Layer III value in the two-bit {@code layerField}.
     * The layer bits are {@code 00 = reserved}, {@code 01 = Layer III},
     * {@code 10 = Layer II}, {@code 11 = Layer I}; this provider handles
     * Layer III only (Requirement 10.5).
     */
    private static final int LAYER_III = 0x01;

    /**
     * Encoded "reserved" value in the two-bit {@code versionField}. The
     * version bits are {@code 00 = MPEG-2.5}, {@code 01 = reserved},
     * {@code 10 = MPEG-2}, {@code 11 = MPEG-1}; any value other than
     * {@code 01} is a real MPEG version this provider supports.
     */
    private static final int VERSION_RESERVED = 0x01;

    /** Non-instantiable. */
    private Mp3HeaderScanner() {
        throw new AssertionError("Mp3HeaderScanner is a static helper; do not instantiate");
    }

    /**
     * Decide whether {@code in} starts with content that looks like an
     * MPEG Layer III stream.
     *
     * <p>Consumes bytes from {@code in} up to the first Layer III sync
     * word or up to {@code 10 + tagSize + (10 if footer else 0) + maxBytes}
     * bytes total, whichever comes first. Returns {@code true} the moment
     * a valid Layer III sync word is located; returns {@code false} if
     * the byte budget runs out or the stream ends without finding one.
     * Does not close or reset the stream; the caller owns the stream and
     * is expected to have wrapped it in a {@code mark}/{@code reset}-capable
     * buffer (see {@code Mp3AudioSourceProvider.canOpen(InputStream, String)})
     * when non-destructive probing is required.
     *
     * <p>The scan resumes immediately after any leading ID3v2 tag, so the
     * {@code maxBytes} budget applies to the <em>post-tag</em> portion of
     * the stream &mdash; matching Requirement 10.5's wording and
     * preventing a pathologically large ID3v2 block from starving the
     * sync-word search.
     *
     * @param in       the stream to probe; must be non-null and positioned
     *                 at the start of the candidate MP3 content
     * @param maxBytes the maximum number of post-tag bytes to scan for
     *                 a sync word; must be non-negative. A value of zero
     *                 always returns {@code false} without reading past
     *                 the tag
     * @return {@code true} iff a valid MPEG Layer III sync word (non-reserved
     *         version, Layer III) is found within the first {@code maxBytes}
     *         bytes after any leading ID3v2 tag; {@code false} otherwise
     * @throws NullPointerException     if {@code in} is {@code null}
     * @throws IllegalArgumentException if {@code maxBytes} is negative
     * @throws IOException              if reading from {@code in} throws
     */
    public static boolean scanForSyncLayer3(InputStream in, int maxBytes) throws IOException {
        Objects.requireNonNull(in, "in");
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes must be >= 0, got " + maxBytes);
        }

        // Step 1: skip any leading ID3v2 tag so the byte budget below
        // applies to the actual MPEG payload rather than to the tag.
        skipId3v2IfPresent(in);

        // Step 2: walk forward looking for the 11-bit sync word. The loop
        // carries the previous byte so each iteration can test the
        // two-byte condition `prev == 0xFF && (cur & 0xE0) == 0xE0`
        // without peeking or rewinding.
        int prev = in.read();
        if (prev < 0) {
            return false;
        }
        int scanned = 0;
        while (scanned < maxBytes) {
            int cur = in.read();
            if (cur < 0) {
                return false;
            }
            if (prev == SYNC_FIRST_BYTE && (cur & SYNC_SECOND_BYTE_MASK) == SYNC_SECOND_BYTE_MASK) {
                int versionField = (cur >> 3) & 0x03;
                int layerField = (cur >> 1) & 0x03;
                if (versionField != VERSION_RESERVED && layerField == LAYER_III) {
                    return true;
                }
            }
            prev = cur;
            scanned++;
        }
        return false;
    }

    /**
     * Read and discard any ID3v2 tag that sits at the current position.
     *
     * <p>If the next three bytes are not {@code "ID3"}, returns
     * immediately without consuming anything beyond those three bytes
     * (see below). Otherwise parses the ten-byte ID3v2 header, decodes
     * the synchsafe size field, checks the footer flag, and skips the
     * remainder of the tag body plus any footer so that the stream is
     * positioned at the first byte <em>after</em> the tag on return.
     *
     * <p><strong>Detection sentinel consumed.</strong> When the first
     * three bytes are not {@code "ID3"} this method has already read
     * them off the stream; they are thrown away because the MPEG sync
     * word cannot begin earlier than byte&nbsp;1 of any well-formed MP3
     * file that lacks a tag, and a file whose first byte is already
     * {@code 0xFF} followed by a sync-candidate byte would be almost
     * certainly a bare-frame recording which the scanner can still pick
     * up via the loop below by giving it a single byte of history. In
     * practice the scanner's caller &mdash;
     * {@code Mp3AudioSourceProvider.canOpen(...)} &mdash; reserves
     * {@code 10_240 + 10} bytes of {@code mark}/{@code reset} budget so
     * the three probe bytes are unobservable to subsequent providers.
     *
     * <p>A stream that ends partway through the tag header or tag body
     * returns early and leaves the sync-word search in the caller to
     * terminate with {@code false} via the usual EOF-at-read path.
     *
     * @param in the stream to inspect; never {@code null}
     * @throws IOException if reading from {@code in} throws
     */
    private static void skipId3v2IfPresent(InputStream in) throws IOException {
        int b0 = in.read();
        if (b0 != 'I') {
            return;
        }
        int b1 = in.read();
        if (b1 != 'D') {
            return;
        }
        int b2 = in.read();
        if (b2 != '3') {
            return;
        }

        // Consume the remaining 7 bytes of the 10-byte ID3v2 header:
        // major version (1), revision (1), flags (1), synchsafe size (4).
        int majorVersion = in.read();
        int revision = in.read();
        int flags = in.read();
        int s6 = in.read();
        int s7 = in.read();
        int s8 = in.read();
        int s9 = in.read();
        // A short read here means the stream is too small to be a valid
        // MP3 anyway; bail out and let the sync-word scan's first read
        // return -1.
        if ((majorVersion | revision | flags | s6 | s7 | s8 | s9) < 0) {
            return;
        }

        // Synchsafe decode: each of the four size bytes contributes seven
        // bits; the top bit of each byte is reserved as zero. The mask
        // with 0x7F is defensive in case a malformed file has the
        // reserved top bit set.
        long tagSize = ((long) (s6 & 0x7F) << 21)
                | ((long) (s7 & 0x7F) << 14)
                | ((long) (s8 & 0x7F) << 7)
                | (long) (s9 & 0x7F);

        long bytesToSkip = tagSize;
        if ((flags & ID3V2_FLAG_FOOTER) != 0) {
            bytesToSkip += ID3V2_FOOTER_SIZE;
        }

        skipFully(in, bytesToSkip);
    }

    /**
     * Advance {@code in} forward by exactly {@code n} bytes, or as close
     * as the stream allows before EOF.
     *
     * <p>{@link InputStream#skip(long)} is documented to possibly skip
     * fewer bytes than requested, notably on socket-backed streams and
     * on some buffered implementations near EOF. This helper loops over
     * {@code skip} and falls back to {@link InputStream#read()} when
     * {@code skip} returns zero so that a partial skip is transparently
     * converted into either a complete skip or a clean EOF (in which
     * case the method simply returns; the subsequent sync-word scan in
     * {@link #scanForSyncLayer3(InputStream, int)} will terminate with
     * {@code false} because its first {@code read()} returns {@code -1}).
     *
     * @param in the stream to advance
     * @param n  the number of bytes to skip; must be non-negative. A
     *           value of zero is a no-op
     * @throws IOException if reading from {@code in} throws
     */
    private static void skipFully(InputStream in, long n) throws IOException {
        long remaining = n;
        while (remaining > 0L) {
            long skipped = in.skip(remaining);
            if (skipped > 0L) {
                remaining -= skipped;
                continue;
            }
            int b = in.read();
            if (b < 0) {
                return;
            }
            remaining -= 1L;
        }
    }
}
