package com.tino1b2be.dtmf.io.wav.internal;

import com.tino1b2be.dtmf.io.PcmEncoding;
import com.tino1b2be.dtmf.io.internal.SampleConversion;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Objects;

/**
 * Frame-by-frame decoder for the {@code data} payload of a validated WAV
 * stream. Given a {@link WaveFormat} describing the stream and a byte
 * source (either a {@link FileChannel} or an {@link InputStream})
 * positioned at the first byte of the payload, this reader produces
 * normalised {@code double} frames on demand.
 *
 * <p>The reader is the "inner engine" of
 * {@code com.tino1b2be.dtmf.io.wav.WavAudioSource} &mdash; the public
 * {@code AudioSource} methods delegate straight through here. The
 * separation matters for two reasons:
 * <ol>
 *   <li>Decode arithmetic is tuple-driven ({@code bitDepth} &times;
 *       {@code encoding} &times; endianness) and the actual
 *       byte-&raquo;-{@code double} formulas live exactly once in
 *       {@link SampleConversion}. Keeping that dispatch here, rather
 *       than duplicating it in {@code WavAudioSource}, means the
 *       {@code RawPcmAudioSource} / WAV / MP3 providers all walk
 *       through the same normalised code path.</li>
 *   <li>The two byte-source modes ({@code FileChannel} vs.
 *       {@code InputStream}) differ only in how raw bytes are pulled
 *       &mdash; never in how samples are decoded &mdash; so the
 *       polymorphism lives at the reader layer and {@code WavAudioSource}
 *       does not have to branch on source type per read.</li>
 * </ol>
 *
 * <h2>Endianness</h2>
 *
 * WAV is always little-endian (the RIFF specification fixes this). The
 * reader hard-wires {@link ByteOrder#LITTLE_ENDIAN} when asking
 * {@link SampleConversion#decoderFor(int, ByteOrder, PcmEncoding)} for a
 * decoder; callers do not specify byte order.
 *
 * <h2>Encoding mapping</h2>
 *
 * The {@link WaveFormat.Encoding} enum carried by {@code WaveFormat} is
 * mapped to the shared {@link PcmEncoding} as follows:
 * <ul>
 *   <li>{@link WaveFormat.Encoding#PCM_SIGNED} &rarr;
 *       {@link PcmEncoding#SIGNED_INT}, valid at bit depths
 *       {@code {16, 24, 32}}.</li>
 *   <li>{@link WaveFormat.Encoding#IEEE_FLOAT} &rarr;
 *       {@link PcmEncoding#IEEE_FLOAT}, valid at bit depths
 *       {@code {32, 64}}.</li>
 * </ul>
 * {@code WaveFormat}'s compact constructor has already validated every
 * tuple the parser produces, so
 * {@link SampleConversion#decoderFor(int, ByteOrder, PcmEncoding)} is
 * called with arguments that cannot possibly fall into its
 * {@code IllegalArgumentException} branch &mdash; but the dispatch keeps
 * the fallback intact anyway, so a bug in the parser upstream fails
 * loudly rather than silently producing garbled samples.
 *
 * <h2>Read loop</h2>
 *
 * Each call to
 * {@link #readFrames(double[], int, int) readFrames(buffer, offset, framesToRead)}:
 * <ol>
 *   <li>Computes how many frames remain in the payload
 *       ({@code totalFrames - frameCursor}).</li>
 *   <li>Returns {@code -1} if no frames remain and none were requested
 *       (i.e. already at EOS).</li>
 *   <li>Otherwise caps {@code framesToRead} at the remaining count,
 *       pulls {@code framesActuallyRead * bytesPerFrame} raw bytes from
 *       the byte source into a scratch buffer, and walks the scratch
 *       buffer one sample at a time, writing
 *       {@code buffer[offset + frameIndex * channelCount + channelIndex]}
 *       using the pre-selected {@link SampleConversion.SampleDecoder}.</li>
 *   <li>Advances {@link #frameCursor()} and returns
 *       {@code framesActuallyRead}.</li>
 * </ol>
 *
 * <p>Zero is a valid return value when the caller asks for zero frames;
 * the {@code -1} sentinel only appears when the caller asks for a
 * positive frame count after the payload has been fully consumed. This
 * matches the {@code read()} contract of {@link java.io.InputStream} and
 * of {@code AudioSource} itself (Requirement 3.6).
 *
 * <h2>Seek</h2>
 *
 * The reader itself does not seek &mdash; it tracks {@link #frameCursor}
 * strictly forward as {@link #readFrames(double[], int, int)} consumes
 * bytes. Random-access seeking is a property of the enclosing
 * {@code WavAudioSource}, which (when backed by a {@code FileChannel})
 * moves the channel's position and then calls
 * {@link #seekToFrame(long)} to keep this reader's internal counter in
 * sync. {@code InputStream}-backed sources do not expose seek to callers
 * (Requirement 3.9, 3.11), so the reader's counter on the stream path
 * only ever moves forward through {@code readFrames}.
 *
 * <h2>Closing</h2>
 *
 * The reader does <strong>not</strong> own the byte source. Closing the
 * underlying {@code FileChannel} or {@code InputStream} is the caller's
 * responsibility (and the caller's responsibility alone &mdash; per
 * Requirement 4.10, caller-supplied streams are never closed by the
 * provider). This class deliberately offers no {@code close} method.
 *
 * <p><strong>Not thread-safe.</strong> A single reader mediates mutable
 * byte-source state and a mutable frame cursor; concurrent
 * {@link #readFrames(double[], int, int)} calls are undefined behaviour.
 *
 * <p><strong>This class is not part of the published API.</strong> It
 * lives in {@code com.tino1b2be.dtmf.io.wav.internal}, whose stability
 * contract (see the package Javadoc) explicitly allows breakage between
 * any two releases. It is {@code public} at the type level purely so
 * classes in the parent {@code com.tino1b2be.dtmf.io.wav} package can
 * reach it; external callers MUST NOT depend on it.
 *
 * @since 2.0.0
 */
public final class WavSampleReader {

    /**
     * Upper bound on the scratch-buffer size used by a single
     * {@link #readFrames(double[], int, int)} call. Sized so that the
     * reader allocates at most one megabyte of temporary bytes per read
     * regardless of how many frames the caller asks for; larger reads
     * are chunked internally. One megabyte comfortably fits an entire
     * analysis block at 192 kHz stereo 64-bit float (roughly 640 KiB of
     * frame bytes) without any fragmentation of a typical caller's
     * request, and small enough that the JVM will reuse the allocation
     * via the young generation rather than promoting it.
     */
    private static final int MAX_SCRATCH_BYTES = 1 << 20;

    /** Parsed WAV metadata. Never null. */
    private final WaveFormat format;

    /** Byte source that backs this reader. Never null. */
    private final ByteSource source;

    /** Pre-selected decoder for the fixed WAV tuple. Never null. */
    private final SampleConversion.SampleDecoder decoder;

    /** Cached {@code bytesPerSample = bitDepth / 8}, in {@code [2, 8]}. */
    private final int bytesPerSample;

    /** Cached {@code bytesPerFrame = bytesPerSample * channelCount}. */
    private final int bytesPerFrame;

    /** Cached {@code channelCount}. */
    private final int channelCount;

    /** Cached {@code totalFrames}. */
    private final long totalFrames;

    /** Zero-based index of the next frame the reader will decode. */
    private long frameCursor;

    /**
     * Scratch byte buffer reused across read calls. Sized lazily on the
     * first read to
     * {@code min(MAX_SCRATCH_BYTES, initialFrames * bytesPerFrame)} and
     * grown on subsequent reads up to {@link #MAX_SCRATCH_BYTES}. Held
     * as a field so the allocation is amortised over the whole
     * decoding run.
     */
    private byte[] scratch;

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    /**
     * Build a reader backed by a random-access {@link FileChannel}.
     *
     * <p>The channel MUST be positioned at the first byte of the
     * {@code data} chunk's payload. The RIFF parser in this package
     * leaves the channel at that position after reading the 8-byte
     * {@code "data" | size} header, and the resulting
     * {@link #readFrames(double[], int, int)} calls advance the channel
     * position in lockstep.
     *
     * <p>The reader does not take ownership of the channel: closing the
     * reader does not close the channel, and there is no close method.
     * The caller (the WAV provider's {@code open(Path)} branch via
     * {@code WavAudioSource}) owns the channel.
     *
     * @param format  validated WAV metadata; must be non-null
     * @param channel open file channel positioned at the start of the
     *                {@code data} payload; must be non-null
     * @throws NullPointerException if either argument is {@code null}
     */
    public WavSampleReader(WaveFormat format, FileChannel channel) {
        this(format, new ChannelSource(Objects.requireNonNull(channel, "channel")));
    }

    /**
     * Build a reader backed by a forward-only {@link InputStream}.
     *
     * <p>The stream MUST be positioned at the first byte of the
     * {@code data} chunk's payload. The RIFF parser in this package
     * leaves the stream at that position after reading the 8-byte
     * {@code "data" | size} header; subsequent
     * {@link #readFrames(double[], int, int)} calls consume bytes from
     * the stream strictly in order.
     *
     * <p>The reader does not take ownership of the stream: closing the
     * reader does not close the stream, and there is no close method.
     * Caller-supplied streams are never closed by the provider
     * (Requirement 4.10); the caller is responsible for closing whatever
     * it passed in.
     *
     * @param format validated WAV metadata; must be non-null
     * @param stream open input stream positioned at the start of the
     *               {@code data} payload; must be non-null
     * @throws NullPointerException if either argument is {@code null}
     */
    public WavSampleReader(WaveFormat format, InputStream stream) {
        this(format, new StreamSource(Objects.requireNonNull(stream, "stream")));
    }

    /**
     * Shared private constructor. Centralises the decoder lookup and
     * field initialisation so the two public constructors above only
     * differ in how they wrap their byte source.
     */
    private WavSampleReader(WaveFormat format, ByteSource source) {
        this.format = Objects.requireNonNull(format, "format");
        this.source = source;
        this.channelCount = format.channelCount();
        this.bytesPerSample = format.bitDepth() / 8;
        this.bytesPerFrame = format.bytesPerFrame();
        this.totalFrames = format.totalFrames();
        this.decoder = SampleConversion.decoderFor(
                format.bitDepth(),
                ByteOrder.LITTLE_ENDIAN,
                mapEncoding(format.encoding()));
        this.frameCursor = 0L;
    }

    /**
     * Map the WAV-specific {@link WaveFormat.Encoding} onto the shared
     * {@link PcmEncoding} used by {@link SampleConversion}. WAV only
     * speaks two encodings (PCM signed integer and IEEE float), so the
     * mapping is total and deterministic.
     */
    private static PcmEncoding mapEncoding(WaveFormat.Encoding encoding) {
        switch (encoding) {
            case PCM_SIGNED: return PcmEncoding.SIGNED_INT;
            case IEEE_FLOAT: return PcmEncoding.IEEE_FLOAT;
            default:
                // WaveFormat.Encoding is a closed enum, but leave the
                // branch so a future new value fails loudly.
                throw new IllegalArgumentException(
                        "Unsupported WaveFormat.Encoding: " + encoding);
        }
    }

    // ------------------------------------------------------------------
    // Read path
    // ------------------------------------------------------------------

    /**
     * Read up to {@code framesToRead} frames from the payload into
     * {@code buffer} starting at {@code offset}.
     *
     * <p>Frames are interleaved in the buffer as
     * {@code [offset + i * channelCount + c]} for frame {@code i} and
     * channel {@code c} (left at {@code c = 0}, right at {@code c = 1}
     * for stereo; mono has a single channel per frame). Samples are
     * normalised to {@code [-1.0, 1.0]} via
     * {@link SampleConversion.SampleDecoder}, matching the
     * {@code AudioSource.read(...)} contract (Requirements 3.6, 9.14).
     *
     * @param buffer       destination buffer; must be non-null and
     *                     large enough to hold
     *                     {@code framesToRead * channelCount} samples
     *                     starting at {@code offset}
     * @param offset       zero-based index of the first sample slot to
     *                     write; must be non-negative
     * @param framesToRead maximum number of frames to read; must be
     *                     non-negative
     * @return the number of frames actually read (in
     *         {@code [0, framesToRead]}), or {@code -1} when the reader
     *         has reached end-of-stream (i.e. {@code frameCursor} has
     *         already advanced to {@code totalFrames}) and the caller
     *         asked for a positive frame count
     * @throws NullPointerException     if {@code buffer} is {@code null}
     * @throws IllegalArgumentException if {@code offset} or
     *                                  {@code framesToRead} is
     *                                  negative, or if
     *                                  {@code offset + framesToRead * channelCount}
     *                                  exceeds {@code buffer.length}
     * @throws IOException              if the underlying byte source
     *                                  throws during the pull, including
     *                                  {@link EOFException} when the
     *                                  payload is shorter than the
     *                                  declared {@code dataSizeBytes}
     */
    public int readFrames(double[] buffer, int offset, int framesToRead) throws IOException {
        Objects.requireNonNull(buffer, "buffer");
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0, got " + offset);
        }
        if (framesToRead < 0) {
            throw new IllegalArgumentException(
                    "framesToRead must be >= 0, got " + framesToRead);
        }
        // Overflow-safe bounds check: framesToRead * channelCount could
        // wrap a 32-bit multiply, so widen to long before comparing.
        long samplesRequestedLong = (long) framesToRead * (long) channelCount;
        if ((long) offset + samplesRequestedLong > buffer.length) {
            throw new IllegalArgumentException(
                    "offset=" + offset + " + framesToRead=" + framesToRead
                            + " * channelCount=" + channelCount
                            + " exceeds buffer.length=" + buffer.length);
        }

        long framesRemaining = totalFrames - frameCursor;
        if (framesRemaining <= 0L) {
            // Already at EOS: -1 on a positive request, 0 when the
            // caller asked for zero frames (mirrors AudioSource.read).
            return framesToRead == 0 ? 0 : -1;
        }
        if (framesToRead == 0) {
            return 0;
        }

        int framesActuallyRead = (int) Math.min((long) framesToRead, framesRemaining);
        int bytesToRead = framesActuallyRead * bytesPerFrame;
        ensureScratch(bytesToRead);

        // Pull the raw bytes in one shot. `framesActuallyRead` is
        // already capped at both `framesToRead` (caller's request) and
        // `framesRemaining` (declared payload), so the resulting byte
        // count is bounded by `MAX_SCRATCH_BYTES + bytesPerFrame` at
        // worst &mdash; callers requesting more than a scratch buffer's
        // worth of frames loop via repeated `readFrames` calls from
        // `WavAudioSource`.
        int cappedBytesToRead = Math.min(bytesToRead, scratch.length);
        int cappedFramesToRead = cappedBytesToRead / bytesPerFrame;
        int cappedByteCount = cappedFramesToRead * bytesPerFrame;
        source.readFully(scratch, 0, cappedByteCount);

        // Decode one sample at a time, walking the scratch buffer in
        // lockstep with the destination buffer. The decoder is a
        // pre-selected functional interface fixed at construction time,
        // so this loop has no per-sample dispatch cost.
        int samplesToDecode = cappedFramesToRead * channelCount;
        int byteIndex = 0;
        int bufferIndex = offset;
        for (int s = 0; s < samplesToDecode; s++) {
            buffer[bufferIndex++] = decoder.decode(scratch, byteIndex);
            byteIndex += bytesPerSample;
        }

        frameCursor += cappedFramesToRead;
        return cappedFramesToRead;
    }

    /**
     * Reset the reader's internal frame cursor to {@code frameIndex}.
     *
     * <p>This method is a <em>bookkeeping</em> operation only: it does
     * <strong>not</strong> move the byte source. The enclosing
     * {@code WavAudioSource} is responsible for repositioning its
     * {@link FileChannel} (the only byte-source mode that supports
     * seeking) to {@code dataStartByteOffset + frameIndex * bytesPerFrame}
     * <em>before</em> calling this method. Splitting the two operations
     * keeps this reader free of knowledge about the absolute byte
     * offsets of the enclosing RIFF container.
     *
     * @param frameIndex new frame cursor value; must be in
     *                   {@code [0, totalFrames()]}
     * @throws IllegalArgumentException if {@code frameIndex} is out of
     *                                  range
     */
    public void seekToFrame(long frameIndex) {
        if (frameIndex < 0L || frameIndex > totalFrames) {
            throw new IllegalArgumentException(
                    "frameIndex must be in [0, " + totalFrames + "], got " + frameIndex);
        }
        this.frameCursor = frameIndex;
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    /**
     * @return the parsed WAV metadata this reader was built with
     */
    public WaveFormat format() {
        return format;
    }

    /**
     * @return the zero-based index of the next frame this reader will
     *         decode. Starts at zero, increases strictly monotonically
     *         through {@link #readFrames(double[], int, int)} calls, and
     *         is reset only via {@link #seekToFrame(long)}.
     */
    public long frameCursor() {
        return frameCursor;
    }

    /**
     * @return the total number of frames declared by the WAV header
     *         (cached from {@code WaveFormat.totalFrames()}). Reaching
     *         this value makes subsequent
     *         {@link #readFrames(double[], int, int)} calls return
     *         {@code -1}.
     */
    public long totalFrames() {
        return totalFrames;
    }

    // ------------------------------------------------------------------
    // Internal scratch-buffer management
    // ------------------------------------------------------------------

    /**
     * Ensure the scratch buffer holds at least {@code required} bytes,
     * capped at {@link #MAX_SCRATCH_BYTES}. The buffer is grown (never
     * shrunk) because the reader is called repeatedly with the same
     * frame-count request from {@code WavAudioSource}'s read loop, so
     * any initial growth is amortised over the whole decode.
     */
    private void ensureScratch(int required) {
        int target = Math.min(required, MAX_SCRATCH_BYTES);
        // Round up to a multiple of bytesPerFrame so the scratch always
        // holds a whole number of frames; otherwise the truncation in
        // readFrames would waste bytes at the tail.
        target = (target / bytesPerFrame) * bytesPerFrame;
        if (target <= 0) {
            target = bytesPerFrame;
        }
        if (scratch == null || scratch.length < target) {
            scratch = new byte[target];
        }
    }

    // ------------------------------------------------------------------
    // Internal byte-source strategy
    // ------------------------------------------------------------------

    /**
     * Minimal abstraction over the two supported byte sources
     * ({@link FileChannel} and {@link InputStream}). Exists purely so
     * the {@link #readFrames(double[], int, int)} path is source-mode
     * agnostic; the decode arithmetic never cares which kind of source
     * is underneath.
     *
     * <p>The interface is deliberately narrower than
     * {@link RiffReader.ByteSource} &mdash; the reader only needs
     * {@code readFully}; position tracking and {@code skip} belong to
     * the header parser, not the frame decoder.
     */
    private interface ByteSource {
        /**
         * Read exactly {@code len} bytes into
         * {@code buf[off .. off + len)}.
         *
         * @throws EOFException if fewer than {@code len} bytes remain
         * @throws IOException  on underlying-source failure
         */
        void readFully(byte[] buf, int off, int len) throws IOException;
    }

    /**
     * {@link FileChannel}-backed byte source. Reads are driven through
     * {@link FileChannel#read(ByteBuffer)}; short reads are retried in a
     * loop because {@code read(ByteBuffer)} is allowed to return fewer
     * bytes than the buffer can hold even when more remain in the file
     * (the {@link java.nio.channels.Channel} contract permits that).
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
    }

    /**
     * {@link InputStream}-backed byte source. {@code InputStream.read}
     * documents that short reads are legal even when more bytes remain,
     * so the implementation loops until {@code len} bytes have been
     * consumed or EOF is observed.
     */
    private static final class StreamSource implements ByteSource {

        private final InputStream stream;

        StreamSource(InputStream stream) {
            this.stream = stream;
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
        }
    }
}
