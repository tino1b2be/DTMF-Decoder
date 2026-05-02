package com.tino1b2be.dtmf.io;

import com.tino1b2be.dtmf.io.internal.SampleConversion;
import com.tino1b2be.dtmf.io.internal.SampleConversion.SampleDecoder;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * {@link AudioSource} wrapping an in-memory {@code byte[]} of raw linear PCM
 * bytes with caller-supplied format metadata.
 *
 * <p>This is the escape hatch for callers who already hold PCM bytes in
 * memory (from an in-process codec, a network stream, a unit-test fixture,
 * or a custom recorder) and want to feed them through the same
 * {@code AudioSource} pipeline as file-based callers. It bypasses the
 * {@link AudioSourceProvider} SPI; just construct it directly. For the
 * overwhelmingly common PCM16 little-endian signed case, the
 * {@link #fromPcm16LittleEndian(byte[], int, int)} factory removes the
 * endianness and encoding arguments (Requirement 7.11).
 *
 * <h2>Supported PCM tuples</h2>
 *
 * The constructor accepts any {@code (bitDepth, byteOrder, encoding)}
 * triple supported by the shared {@code SampleConversion} helper
 * (Requirements 7.7, 7.8):
 *
 * <ul>
 *   <li>{@code bitDepth ∈ {16, 24, 32, 64}}</li>
 *   <li>{@code byteOrder ∈ {LITTLE_ENDIAN, BIG_ENDIAN}}</li>
 *   <li>{@code encoding ∈ {SIGNED_INT, UNSIGNED_INT, IEEE_FLOAT}}, with the
 *       additional constraint that {@code IEEE_FLOAT} requires
 *       {@code bitDepth ∈ {32, 64}}.</li>
 * </ul>
 *
 * <h2>Normalisation</h2>
 *
 * Integer samples are divided by {@code 2^(bitDepth - 1)} so that full-scale
 * positive samples map just below {@code +1.0} and full-scale negative
 * samples map exactly to {@code -1.0}. IEEE float samples are widened to
 * {@code double} without scaling (Requirements 3.6, 7.12). The conversion
 * formulas live in
 * {@link com.tino1b2be.dtmf.io.internal.SampleConversion} and are shared
 * verbatim with the WAV and MP3 providers.
 *
 * <h2>Buffer ownership &mdash; the backing array is NOT copied</h2>
 *
 * <strong>The caller-supplied {@code data} array is referenced, not copied
 * (Requirement 7.13).</strong> The source reads from the array on every
 * {@link #read(double[], int, int)} call for the lifetime of this object.
 * Mutating {@code data} after construction will change the output of
 * subsequent reads and yields undefined behaviour; callers who need to
 * reuse or recycle their byte buffer must pass a defensive copy
 * (typically {@code data.clone()}) into the constructor. The cost of
 * zero-copy is entirely on the caller's side; the source pays nothing.
 *
 * <h2>Seekability</h2>
 *
 * {@link #canSeek()} always returns {@code true} (Requirement 7.10);
 * seeking is an O(1) update of the internal frame cursor since the whole
 * buffer is already resident.
 *
 * <h2>Thread safety</h2>
 *
 * Instances are <em>not</em> thread-safe (shared with the general
 * {@link AudioSource} contract). The mutable state is the frame cursor and
 * the closed flag; callers that need concurrent access must serialise
 * externally or construct one source per thread sharing the same
 * underlying {@code byte[]} (which is safe provided no one mutates the
 * bytes).
 *
 * @since 2.1.0
 * @see AudioSource
 * @see PcmEncoding
 * @see com.tino1b2be.dtmf.io.internal.SampleConversion
 */
public final class RawPcmAudioSource implements AudioSource {

    /** Lower bound (inclusive) for accepted sample rates, in Hz. */
    private static final int MIN_SAMPLE_RATE = 1;
    /** Upper bound (inclusive) for accepted sample rates, in Hz. */
    private static final int MAX_SAMPLE_RATE = 384_000;
    /** Lower bound (inclusive) for accepted channel counts. */
    private static final int MIN_CHANNEL_COUNT = 1;
    /** Upper bound (inclusive) for accepted channel counts. */
    private static final int MAX_CHANNEL_COUNT = 8;

    /**
     * Backing byte buffer. Held by reference, never copied
     * (Requirement 7.13). See the class Javadoc for the implications.
     */
    private final byte[] data;
    private final int sampleRate;
    private final int bitDepth;
    private final ByteOrder byteOrder;
    private final int channelCount;
    private final PcmEncoding encoding;
    /** {@code bitDepth / 8}; cached so read loops avoid the division. */
    private final int bytesPerSample;
    /** {@code bytesPerSample * channelCount}; cached for the same reason. */
    private final int bytesPerFrame;
    /** Total frame count, computed as {@code data.length / bytesPerFrame}. */
    private final long totalFrames;
    /**
     * Cached single-sample decoder for {@code (bitDepth, byteOrder,
     * encoding)}; resolved once in the constructor so the per-sample hot
     * loop in {@link #read(double[], int, int)} never re-dispatches.
     */
    private final SampleDecoder decoder;

    /** Zero-based index of the next frame to read. Updated by
     *  {@link #read(double[], int, int)} and {@link #seek(long)}. */
    private long frameCursor = 0L;

    /** Once {@link #close()} flips this to {@code true}, {@code read(...)}
     *  and {@code seek(...)} throw {@link IOException} (Req 3.14). */
    private boolean closed = false;

    /**
     * Wrap a caller-supplied byte buffer of raw linear PCM bytes.
     *
     * <p><strong>The {@code data} array is referenced, not copied
     * (Requirement 7.13).</strong> See the class Javadoc for the full
     * buffer-ownership contract; mutating {@code data} after construction
     * yields undefined {@link #read(double[], int, int)} output.
     *
     * @param data         raw PCM byte buffer; length must be an exact
     *                     multiple of {@code (bitDepth / 8) * channelCount}
     * @param sampleRate   source sample rate in Hz;
     *                     {@code 1 <= sampleRate <= 384000} (Req 7.5)
     * @param bitDepth     native sample bit depth;
     *                     {@code bitDepth ∈ {16, 24, 32, 64}} (Req 7.7)
     * @param byteOrder    byte order of multi-byte samples;
     *                     {@link ByteOrder#LITTLE_ENDIAN} or
     *                     {@link ByteOrder#BIG_ENDIAN}
     * @param channelCount number of interleaved channels;
     *                     {@code 1 <= channelCount <= 8} (Req 7.6)
     * @param encoding     PCM numeric encoding (Req 7.3); with the
     *                     additional constraint that {@link PcmEncoding#IEEE_FLOAT}
     *                     requires {@code bitDepth ∈ {32, 64}} (Req 7.8)
     * @throws NullPointerException     if {@code data}, {@code byteOrder},
     *                                  or {@code encoding} is {@code null};
     *                                  the message identifies the
     *                                  offending parameter (Req 7.4)
     * @throws IllegalArgumentException if any numeric parameter is outside
     *                                  its accepted range, if the
     *                                  {@code (encoding, bitDepth)} pair
     *                                  is invalid, or if {@code data.length}
     *                                  is not an exact multiple of the
     *                                  frame size (Req 7.5, 7.6, 7.7, 7.8,
     *                                  7.9); the message identifies the
     *                                  offending value and the valid
     *                                  range/set
     */
    public RawPcmAudioSource(
            byte[] data,
            int sampleRate,
            int bitDepth,
            ByteOrder byteOrder,
            int channelCount,
            PcmEncoding encoding) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(byteOrder, "byteOrder");
        Objects.requireNonNull(encoding, "encoding");

        if (sampleRate < MIN_SAMPLE_RATE || sampleRate > MAX_SAMPLE_RATE) {
            throw new IllegalArgumentException(
                    "sampleRate must be in [" + MIN_SAMPLE_RATE + ", "
                            + MAX_SAMPLE_RATE + "], was " + sampleRate);
        }
        if (channelCount < MIN_CHANNEL_COUNT || channelCount > MAX_CHANNEL_COUNT) {
            throw new IllegalArgumentException(
                    "channelCount must be in [" + MIN_CHANNEL_COUNT + ", "
                            + MAX_CHANNEL_COUNT + "], was " + channelCount);
        }
        if (bitDepth != 16 && bitDepth != 24 && bitDepth != 32 && bitDepth != 64) {
            throw new IllegalArgumentException(
                    "bitDepth must be in {16, 24, 32, 64}, was " + bitDepth);
        }
        if (encoding == PcmEncoding.IEEE_FLOAT && bitDepth != 32 && bitDepth != 64) {
            throw new IllegalArgumentException(
                    "IEEE_FLOAT requires bitDepth in {32, 64}, was " + bitDepth);
        }

        int perSample = bitDepth / 8;
        int perFrame = perSample * channelCount;
        if (data.length % perFrame != 0) {
            throw new IllegalArgumentException(
                    "data.length=" + data.length
                            + " is not a multiple of bytesPerFrame=" + perFrame
                            + " (bitDepth=" + bitDepth
                            + ", channelCount=" + channelCount + ")");
        }

        this.data = data;
        this.sampleRate = sampleRate;
        this.bitDepth = bitDepth;
        this.byteOrder = byteOrder;
        this.channelCount = channelCount;
        this.encoding = encoding;
        this.bytesPerSample = perSample;
        this.bytesPerFrame = perFrame;
        this.totalFrames = (long) data.length / perFrame;
        // Resolve once so the per-sample loop in read() stays tight.
        this.decoder = SampleConversion.decoderFor(bitDepth, byteOrder, encoding);
    }

    /**
     * Convenience factory for the overwhelmingly common case of 16-bit
     * little-endian signed PCM (Requirement 7.11). Equivalent to
     * <pre>{@code
     *   new RawPcmAudioSource(data, sampleRate, 16,
     *           ByteOrder.LITTLE_ENDIAN, channelCount, PcmEncoding.SIGNED_INT)
     * }</pre>
     *
     * <p><strong>The {@code data} array is referenced, not copied</strong>;
     * the same buffer-ownership contract as the primary constructor
     * applies (Requirement 7.13).
     *
     * @param data         raw PCM16 byte buffer; length must be an exact
     *                     multiple of {@code 2 * channelCount}
     * @param sampleRate   source sample rate in Hz
     * @param channelCount number of interleaved channels
     * @return a new {@code RawPcmAudioSource} configured for PCM16 LE
     *         signed int
     * @throws NullPointerException     if {@code data} is {@code null}
     * @throws IllegalArgumentException if any constructor validation rule
     *                                  is violated
     */
    public static RawPcmAudioSource fromPcm16LittleEndian(
            byte[] data, int sampleRate, int channelCount) {
        return new RawPcmAudioSource(
                data, sampleRate, 16,
                ByteOrder.LITTLE_ENDIAN, channelCount, PcmEncoding.SIGNED_INT);
    }

    @Override
    public int sampleRate() {
        return sampleRate;
    }

    @Override
    public int channelCount() {
        return channelCount;
    }

    @Override
    public int bitDepth() {
        return bitDepth;
    }

    @Override
    public long totalFrames() {
        return totalFrames;
    }

    @Override
    public boolean canSeek() {
        return true;
    }

    @Override
    public long currentFrame() {
        return frameCursor;
    }

    /**
     * Return the PCM numeric encoding this source was constructed with.
     * Informational; the decoded samples handed back from
     * {@link #read(double[], int, int)} are always normalised {@code
     * double} regardless.
     *
     * @return the {@link PcmEncoding} the source decodes from
     */
    public PcmEncoding encoding() {
        return encoding;
    }

    /**
     * Return the byte order this source was constructed with. Informational.
     *
     * @return the {@link ByteOrder} the source decodes from
     */
    public ByteOrder byteOrder() {
        return byteOrder;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Implementation notes:
     * <ul>
     *   <li>Reads up to {@code length} frames (not samples) into
     *       {@code buffer} starting at {@code offset}. Exactly
     *       {@code n * channelCount} samples are written when this call
     *       returns {@code n >= 0}, with channels interleaved per the
     *       {@link AudioSource} contract (Requirement 3.7).</li>
     *   <li>Returns {@code -1} when the source is already exhausted at
     *       entry (Requirement 3.6).</li>
     *   <li>Reads are served directly out of the backing byte array; no
     *       intermediate buffer allocations occur.</li>
     * </ul>
     *
     * @throws IOException               if the source has been
     *                                   {@linkplain #close() closed}
     *                                   (Requirement 3.14)
     * @throws NullPointerException      if {@code buffer} is {@code null}
     * @throws IndexOutOfBoundsException if {@code offset < 0},
     *                                   {@code length < 0}, or
     *                                   {@code offset + length * channelCount}
     *                                   exceeds {@code buffer.length}
     */
    @Override
    public int read(double[] buffer, int offset, int length) throws IOException {
        if (closed) {
            throw new IOException("RawPcmAudioSource is closed");
        }
        Objects.requireNonNull(buffer, "buffer");
        if (offset < 0 || length < 0) {
            throw new IndexOutOfBoundsException(
                    "offset and length must be non-negative, were offset="
                            + offset + ", length=" + length);
        }
        // Guard against overflow in `length * channelCount` before using it
        // as a bounds check: a malicious `length = Integer.MAX_VALUE` with
        // `channelCount = 2` would wrap otherwise.
        long requiredSamples = (long) length * (long) channelCount;
        if ((long) offset + requiredSamples > (long) buffer.length) {
            throw new IndexOutOfBoundsException(
                    "offset(" + offset + ") + length(" + length
                            + ") * channelCount(" + channelCount
                            + ") = " + (offset + requiredSamples)
                            + " exceeds buffer.length=" + buffer.length);
        }

        long remaining = totalFrames - frameCursor;
        if (remaining <= 0L) {
            return -1;
        }
        int framesToRead = (int) Math.min((long) length, remaining);
        if (framesToRead == 0) {
            return 0;
        }

        // Per-frame, per-channel decode: one SampleDecoder invocation per
        // interleaved sample. The decoder is stateless, so the loop body
        // stays branch-free.
        final long frameBase = frameCursor;
        for (int i = 0; i < framesToRead; i++) {
            long sampleOffsetLong = (frameBase + i) * (long) bytesPerFrame;
            int writeBase = offset + i * channelCount;
            for (int c = 0; c < channelCount; c++) {
                int sampleOffset = (int) (sampleOffsetLong + (long) c * bytesPerSample);
                buffer[writeBase + c] = decoder.decode(data, sampleOffset);
            }
        }
        frameCursor = frameBase + framesToRead;
        return framesToRead;
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code RawPcmAudioSource} always supports seeking
     * (Requirement 7.10); {@code canSeek()} is {@code true}. The valid
     * range is {@code [0, totalFrames()]}; seeking to
     * {@code totalFrames()} positions the cursor at end-of-stream so the
     * next {@code read(...)} returns {@code -1}.
     *
     * @throws IOException              if the source has been closed
     *                                  (Requirement 3.14)
     * @throws IllegalArgumentException if {@code frameIndex} is outside
     *                                  {@code [0, totalFrames()]}; the
     *                                  message identifies the offending
     *                                  value and the valid range
     *                                  (Requirement 3.12)
     */
    @Override
    public void seek(long frameIndex) throws IOException {
        if (closed) {
            throw new IOException("RawPcmAudioSource is closed");
        }
        if (frameIndex < 0L || frameIndex > totalFrames) {
            throw new IllegalArgumentException(
                    "frameIndex must be in [0, " + totalFrames
                            + "], was " + frameIndex);
        }
        frameCursor = frameIndex;
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code RawPcmAudioSource} owns no native resources; {@code close()}
     * simply flips an internal flag that causes subsequent
     * {@link #read(double[], int, int)} or {@link #seek(long)} calls to
     * throw {@link IOException} (Requirement 3.14). It does not touch the
     * caller-supplied backing byte array; callers remain free to reuse or
     * discard it afterwards.
     *
     * <p>This method is idempotent: a second and subsequent invocation is
     * a no-op.
     */
    @Override
    public void close() {
        closed = true;
    }
}
