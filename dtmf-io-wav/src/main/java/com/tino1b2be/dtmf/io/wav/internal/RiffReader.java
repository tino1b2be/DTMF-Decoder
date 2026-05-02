package com.tino1b2be.dtmf.io.wav.internal;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Low-level RIFF byte reader for the clean-room WAV parser in this
 * package.
 *
 * <p>WAV is a RIFF container: a flat sequence of length-prefixed,
 * ID-tagged chunks preceded by a 12-byte {@code RIFF | size | WAVE}
 * header. Every field inside a RIFF container is little-endian, four-byte
 * chunk IDs are ASCII, and &mdash; critically for the provider's streaming
 * model &mdash; a single zero-byte pad follows any chunk whose declared
 * {@code size} is odd, so that the next chunk always starts on an even
 * byte boundary. This class exposes exactly the byte-level primitives the
 * higher-level parser needs to walk that structure:
 * {@link #readAscii(int)} for four-character chunk IDs and the
 * {@code WAVE} form marker, {@link #readU32LE()} and {@link #readU64LE()}
 * for the (unsigned) size fields, {@link #skip(long)} for skipping chunks
 * the parser does not understand, {@link #skipPaddingIfNeeded(long)} for
 * the odd-chunk-size pad byte, and {@link #position()} for anchoring the
 * byte offset that {@code data}-chunk payloads are later seeked against.
 *
 * <p><strong>Two byte-source modes.</strong> A RIFF reader can be built
 * from either a {@link FileChannel} (random-access, backs the
 * {@code open(Path)} branch and keeps {@code canSeek()} on the returned
 * {@code WavAudioSource} {@code true}) or an {@link InputStream}
 * (forward-only, backs the {@code open(InputStream, String)} branch and
 * forces {@code canSeek()} to {@code false}). Both constructors expose
 * the same API; the difference is hidden behind the package-private
 * {@link ByteSource} strategy below. Position tracking for the channel
 * mode reads straight from {@link FileChannel#position()} so that
 * whatever offset the caller started at is preserved; the stream mode
 * maintains an internal counter that starts at zero.
 *
 * <p><strong>Non-negotiable invariants.</strong> Every primitive read
 * method throws {@link EOFException} if the underlying source does not
 * contain enough bytes to satisfy the request (this is the concrete
 * mechanism behind Requirement 9.11's "chunk size exceeding remaining
 * file size" clause &mdash; when the higher-level parser calls
 * {@link #skip(long)} with a chunk size that runs off the end of the
 * file, the {@code EOFException} bubbles up as the parser's
 * {@link IOException}). {@link #position()} always reports the number of
 * bytes successfully consumed so far; it never moves backward, and the
 * reader offers no general seek operation (the parser's random access
 * happens later on the {@code FileChannel} directly, after the headers
 * have been consumed sequentially).
 *
 * <p><strong>This class is not part of the published API.</strong> It
 * lives in {@code com.tino1b2be.dtmf.io.wav.internal}, whose stability
 * contract (see the package Javadoc) explicitly allows breakage between
 * any two releases. It is {@code public} at the type level purely so
 * classes in the parent {@code com.tino1b2be.dtmf.io.wav} package can
 * reach it; external callers MUST NOT depend on it.
 *
 * <p><strong>Not thread-safe.</strong> A single {@code RiffReader}
 * mediates mutable byte-source state and must be used by one thread at a
 * time. Concurrent calls are undefined behaviour.
 *
 * @since 2.1.0
 */
public final class RiffReader {

    /**
     * Scratch buffer sized to eight bytes so a single instance can serve
     * every primitive read &mdash; four-byte IDs, four-byte {@code u32}
     * fields, and eight-byte {@code u64} fields all share the same
     * backing array. Held at instance scope rather than allocated per
     * call to keep the read path allocation-free, which matters when the
     * WAV parser is walking a long list of tiny chunks before the
     * {@code data} payload.
     */
    private final byte[] scratch = new byte[8];

    /** The byte-source strategy this reader pulls from. Never null. */
    private final ByteSource source;

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    /**
     * Build a reader that pulls from a random-access {@link FileChannel}.
     *
     * <p>The channel's current position is taken as the reader's origin:
     * {@link #position()} after construction equals the channel's
     * position at the time the method is first called. Every primitive
     * read advances both the channel's position and the reader's view
     * in lockstep, so the higher-level parser can later ask the
     * {@link FileChannel} directly for the offset of the {@code data}
     * payload without doing its own bookkeeping.
     *
     * <p>The reader does <strong>not</strong> take ownership of the
     * channel: closing the reader does not close the channel, and there
     * is no {@code close} method because there is nothing to close. The
     * caller (the WAV provider's {@code open} method) owns the channel
     * and is responsible for closing it on the {@code WavAudioSource}
     * that eventually wraps it.
     *
     * @param channel the file channel to read from; must be non-null and
     *                open
     * @throws NullPointerException if {@code channel} is {@code null}
     */
    public RiffReader(FileChannel channel) {
        Objects.requireNonNull(channel, "channel");
        this.source = new ChannelSource(channel);
    }

    /**
     * Build a reader that pulls from a forward-only
     * {@link InputStream}.
     *
     * <p>The reader starts reporting {@link #position()} at zero and
     * increments it by every byte successfully consumed. The stream
     * itself is wrapped so that {@link InputStream#skip(long)}'s
     * documented "may skip fewer bytes than requested" caveat is
     * neutralised: {@link #skip(long)} on this reader always consumes
     * exactly the requested number of bytes or throws.
     *
     * <p>The reader does <strong>not</strong> take ownership of the
     * stream: there is no {@code close} method and closing the reader
     * (there isn't one) would not close the stream. The caller owns the
     * stream &mdash; consistent with {@code AudioSourceProvider}'s
     * "never close caller-supplied streams" rule (Requirement 4.10).
     *
     * @param stream the input stream to read from; must be non-null
     * @throws NullPointerException if {@code stream} is {@code null}
     */
    public RiffReader(InputStream stream) {
        Objects.requireNonNull(stream, "stream");
        this.source = new StreamSource(stream);
    }

    // ------------------------------------------------------------------
    // Primitive reads
    // ------------------------------------------------------------------

    /**
     * Read exactly {@code n} bytes and interpret them as US-ASCII.
     *
     * <p>Used for four-character chunk IDs ({@code "fmt "},
     * {@code "data"}, {@code "LIST"}, {@code "ds64"}&hellip;), the
     * twelve-byte outer form (broken into two {@code readAscii(4)}
     * calls around a {@link #readU32LE()} in the caller), and the
     * {@code "WAVE"} form marker.
     *
     * <p>The RIFF specification guarantees chunk IDs are drawn from
     * printable US-ASCII, so the fixed {@link StandardCharsets#US_ASCII}
     * decoding is intentional &mdash; a non-ASCII byte indicates a
     * malformed file, not a charset-coverage gap.
     *
     * @param n the number of bytes to read; must be non-negative
     * @return a {@code String} of length exactly {@code n}
     * @throws IllegalArgumentException if {@code n} is negative
     * @throws EOFException             if fewer than {@code n} bytes
     *                                  remain
     * @throws IOException              if the underlying source throws
     */
    public String readAscii(int n) throws IOException {
        if (n < 0) {
            throw new IllegalArgumentException("n must be >= 0, got " + n);
        }
        if (n == 0) {
            return "";
        }
        byte[] buf = (n <= scratch.length) ? scratch : new byte[n];
        source.readFully(buf, 0, n);
        return new String(buf, 0, n, StandardCharsets.US_ASCII);
    }

    /**
     * Read four bytes and interpret them as a little-endian unsigned
     * 32-bit integer widened to {@code long}.
     *
     * <p>Returning {@code long} (not {@code int}) is deliberate: RIFF
     * size fields are unsigned, and a direct-signed {@code int} would
     * wrap around at the 2&nbsp;GiB boundary &mdash; well within the
     * range of legitimate WAV files at high bit depths and sample rates.
     * The returned value is always in the range
     * {@code [0, 2^32 - 1] = [0, 4294967295L]}.
     *
     * @return the decoded value in {@code [0, 4294967295L]}
     * @throws EOFException if fewer than four bytes remain
     * @throws IOException  if the underlying source throws
     */
    public long readU32LE() throws IOException {
        source.readFully(scratch, 0, 4);
        return ByteBuffer.wrap(scratch, 0, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt() & 0xFFFF_FFFFL;
    }

    /**
     * Read eight bytes and interpret them as a little-endian 64-bit
     * integer.
     *
     * <p>Used for the three 64-bit fields of the {@code ds64} chunk in
     * RF64 files ({@code riffSize64}, {@code dataSize64},
     * {@code sampleCount64}). Returned as a signed {@code long} because
     * RF64's {@code riffSize64} and {@code dataSize64} are treated as
     * unsigned 64-bit by the spec but no real file on any filesystem we
     * care about exceeds {@code 2^63 - 1 = 8 EiB}, and keeping the
     * return type signed avoids every call-site {@code & ~0L} mask.
     *
     * @return the decoded value as a signed {@code long}
     * @throws EOFException if fewer than eight bytes remain
     * @throws IOException  if the underlying source throws
     */
    public long readU64LE() throws IOException {
        source.readFully(scratch, 0, 8);
        return ByteBuffer.wrap(scratch, 0, 8)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getLong();
    }

    /**
     * Skip exactly {@code n} bytes.
     *
     * <p>This is the mechanism behind the "chunk size exceeding
     * remaining file size" clause of Requirement 9.11: the higher-level
     * parser calls {@code skip(chunk.size())} for every chunk it does
     * not understand ({@code "LIST"}, {@code "bext"}, {@code "junk"},
     * {@code "PEAK"}&hellip;), and if the declared size runs off the
     * end of the underlying file or stream, the {@link EOFException}
     * raised here bubbles up as the {@link IOException} the requirement
     * mandates.
     *
     * <p>For the {@link InputStream} mode this method is implemented as
     * a loop over {@link InputStream#skip(long)} with a read-byte
     * fallback, so partial skips from the underlying stream are
     * transparently converted into a complete skip or a proper EOF.
     * For the {@link FileChannel} mode it moves the channel position
     * forward and then verifies against {@link FileChannel#size()} so
     * that seeking past the end reports EOF instead of silently
     * succeeding.
     *
     * @param n the number of bytes to skip; must be non-negative
     * @throws IllegalArgumentException if {@code n} is negative
     * @throws EOFException             if fewer than {@code n} bytes
     *                                  remain
     * @throws IOException              if the underlying source throws
     */
    public void skip(long n) throws IOException {
        if (n < 0L) {
            throw new IllegalArgumentException("n must be >= 0, got " + n);
        }
        if (n == 0L) {
            return;
        }
        source.skipFully(n);
    }

    /**
     * Skip a single pad byte when {@code chunkSize} is odd.
     *
     * <p>RIFF aligns every chunk to an even byte boundary: a chunk
     * whose declared {@code size} field is odd is followed by a single
     * zero-byte pad before the next chunk's ID starts. The parser calls
     * this method after consuming (or {@link #skip(long) skipping}) each
     * chunk's payload so the next {@link #readAscii(int) readAscii(4)}
     * lands on a real chunk ID rather than the pad byte.
     *
     * <p>This is a convenience wrapper over {@link #skip(long)} that
     * does nothing when {@code chunkSize} is even, so callers can
     * invoke it unconditionally.
     *
     * @param chunkSize the chunk's declared size field; must be
     *                  non-negative
     * @throws IllegalArgumentException if {@code chunkSize} is negative
     * @throws EOFException             if {@code chunkSize} is odd and
     *                                  no bytes remain
     * @throws IOException              if the underlying source throws
     */
    public void skipPaddingIfNeeded(long chunkSize) throws IOException {
        if (chunkSize < 0L) {
            throw new IllegalArgumentException(
                    "chunkSize must be >= 0, got " + chunkSize);
        }
        if ((chunkSize & 1L) != 0L) {
            skip(1L);
        }
    }

    /**
     * Read exactly {@code len} raw bytes from the underlying byte
     * source into {@code buf[off .. off + len)}.
     *
     * <p>This is the general binary-read primitive complementing
     * {@link #readAscii(int)} (which {@code US_ASCII}-decodes its
     * payload and therefore mangles non-ASCII bytes via the Unicode
     * Replacement character). Callers use {@code readBytes} for binary
     * payloads that must round-trip byte-for-byte &mdash; for example
     * the 16-byte {@code SubFormat} GUID inside a
     * {@code WAVEFORMATEXTENSIBLE} {@code fmt } chunk, or the small
     * {@code u16} field pairs in the same chunk.
     *
     * <p>Unlike {@link java.io.InputStream#read(byte[], int, int)},
     * this method is short-read intolerant: it either fills the
     * requested span completely or throws. The underlying byte source
     * is already wired to loop on short pulls, so the exception
     * semantics match {@link #readAscii(int)} and the {@code readU*}
     * primitives &mdash; {@link EOFException} when fewer than
     * {@code len} bytes remain, {@link IOException} on any other
     * failure.
     *
     * @param buf destination buffer; must be non-null
     * @param off starting index in {@code buf}; must be non-negative
     * @param len number of bytes to read; must be non-negative and
     *            satisfy {@code off + len <= buf.length}
     * @throws NullPointerException     if {@code buf} is {@code null}
     * @throws IllegalArgumentException if {@code off} or {@code len}
     *                                  is negative, or if
     *                                  {@code off + len > buf.length}
     * @throws EOFException             if fewer than {@code len} bytes
     *                                  remain
     * @throws IOException              if the underlying source throws
     */
    public void readBytes(byte[] buf, int off, int len) throws IOException {
        Objects.requireNonNull(buf, "buf");
        if (off < 0) {
            throw new IllegalArgumentException("off must be >= 0, got " + off);
        }
        if (len < 0) {
            throw new IllegalArgumentException("len must be >= 0, got " + len);
        }
        if ((long) off + (long) len > buf.length) {
            throw new IllegalArgumentException(
                    "off=" + off + " + len=" + len
                            + " exceeds buf.length=" + buf.length);
        }
        if (len == 0) {
            return;
        }
        source.readFully(buf, off, len);
    }

    /**
     * Read an unsigned 16-bit little-endian integer.
     *
     * <p>Complements {@link #readU32LE()} and {@link #readU64LE()} for
     * the {@code u16} fields that pepper the {@code fmt } chunk of a
     * WAV file ({@code wFormatTag}, {@code nChannels},
     * {@code nBlockAlign}, {@code wBitsPerSample}, {@code cbSize},
     * {@code wValidBitsPerSample}).
     *
     * @return the decoded value in {@code [0, 65535]}
     * @throws EOFException if fewer than two bytes remain
     * @throws IOException  if the underlying source throws
     */
    public int readU16LE() throws IOException {
        source.readFully(scratch, 0, 2);
        return (scratch[0] & 0xFF) | ((scratch[1] & 0xFF) << 8);
    }

    /**
     * Current byte position within the underlying source.
     *
     * <p>For the {@link FileChannel} mode this is
     * {@link FileChannel#position()}: the absolute byte offset in the
     * file. For the {@link InputStream} mode this is the number of
     * bytes successfully consumed since the reader was built (the
     * reader starts reporting zero and counts up from there).
     *
     * <p>The parser anchors the {@code data} chunk's
     * {@code dataStartByteOffset} to this value the instant it finishes
     * reading the chunk's 8-byte header, so that
     * {@code WavAudioSource.seek(frameIndex)} can later compute
     * {@code dataStartByteOffset + frameIndex * bytesPerFrame} as an
     * absolute channel position.
     *
     * @return the current byte position
     * @throws IOException if querying the underlying source throws
     */
    public long position() throws IOException {
        return source.position();
    }

    // ------------------------------------------------------------------
    // Internal byte-source strategy
    // ------------------------------------------------------------------

    /**
     * Minimal abstraction over the two supported byte sources
     * ({@link FileChannel} and {@link InputStream}). Kept
     * package-private so tests in this package can exercise the reader
     * against lightweight in-memory fixtures without reaching for
     * reflection or a real file.
     */
    interface ByteSource {
        /**
         * Read exactly {@code len} bytes into
         * {@code buf[off .. off + len)}.
         *
         * @throws EOFException if fewer than {@code len} bytes remain
         * @throws IOException  on underlying-source failure
         */
        void readFully(byte[] buf, int off, int len) throws IOException;

        /**
         * Advance the source forward by exactly {@code n} bytes.
         *
         * @throws EOFException if fewer than {@code n} bytes remain
         * @throws IOException  on underlying-source failure
         */
        void skipFully(long n) throws IOException;

        /**
         * Current byte position, measured from whatever origin the
         * concrete source was constructed with ({@link FileChannel}'s
         * current position for the channel mode, or zero for the stream
         * mode).
         *
         * @throws IOException if querying the source throws
         */
        long position() throws IOException;
    }

    /**
     * {@link FileChannel}-backed byte source. Position tracking is
     * delegated to {@link FileChannel#position()} so the reader's view
     * and the channel's view stay in lockstep &mdash; important because
     * the higher-level WAV parser reads {@code data}'s start offset via
     * {@link #position()} and then later seeks on the <em>channel</em>
     * (not the reader) when {@code WavAudioSource.seek(long)} runs.
     */
    private static final class ChannelSource implements ByteSource {

        private final FileChannel channel;

        ChannelSource(FileChannel channel) {
            this.channel = channel;
        }

        @Override
        public void readFully(byte[] buf, int off, int len) throws IOException {
            ByteBuffer target = ByteBuffer.wrap(buf, off, len);
            int totalRead = 0;
            while (totalRead < len) {
                int n = channel.read(target);
                if (n < 0) {
                    throw new EOFException(
                            "Unexpected end of file after " + totalRead
                                    + " bytes (requested " + len + ")");
                }
                totalRead += n;
            }
        }

        @Override
        public void skipFully(long n) throws IOException {
            long current = channel.position();
            long target = current + n;
            long size = channel.size();
            if (target > size) {
                long available = size - current;
                throw new EOFException(
                        "Unexpected end of file: requested to skip " + n
                                + " bytes but only " + available + " remain");
            }
            channel.position(target);
        }

        @Override
        public long position() throws IOException {
            return channel.position();
        }
    }

    /**
     * {@link InputStream}-backed byte source.
     *
     * <p>{@link #skipFully(long)} loops over {@link InputStream#skip(long)}
     * and falls back to {@link InputStream#read()} whenever {@code skip}
     * returns zero, so a partial skip from the underlying stream (as
     * {@link InputStream} documents is allowed, notably for sockets) is
     * transparently converted into a complete skip or a proper
     * {@link EOFException}.
     *
     * <p>Position tracking is a simple long counter that increments by
     * every byte successfully consumed. It starts at zero, so
     * stream-mode callers who need to correlate the reader's position
     * with an absolute file offset must add their own baseline.
     */
    private static final class StreamSource implements ByteSource {

        private final InputStream stream;
        private long position;

        StreamSource(InputStream stream) {
            this.stream = stream;
            this.position = 0L;
        }

        @Override
        public void readFully(byte[] buf, int off, int len) throws IOException {
            int totalRead = 0;
            while (totalRead < len) {
                int n = stream.read(buf, off + totalRead, len - totalRead);
                if (n < 0) {
                    throw new EOFException(
                            "Unexpected end of stream after " + totalRead
                                    + " bytes (requested " + len + ")");
                }
                totalRead += n;
            }
            position += len;
        }

        @Override
        public void skipFully(long n) throws IOException {
            long remaining = n;
            while (remaining > 0L) {
                long skipped = stream.skip(remaining);
                if (skipped > 0L) {
                    remaining -= skipped;
                    continue;
                }
                // `skip` returned 0: either EOF or the stream chose not
                // to skip (some InputStream implementations do this for
                // e.g. network sockets). Fall back to a one-byte read
                // to disambiguate.
                int b = stream.read();
                if (b < 0) {
                    long consumed = n - remaining;
                    throw new EOFException(
                            "Unexpected end of stream: requested to skip "
                                    + n + " bytes but only " + consumed
                                    + " were available");
                }
                remaining -= 1L;
            }
            position += n;
        }

        @Override
        public long position() {
            return position;
        }
    }
}
