package com.tino1b2be.dtmf.io.wav;

import com.tino1b2be.dtmf.io.AudioSource;
import com.tino1b2be.dtmf.io.wav.internal.WaveFormat;
import com.tino1b2be.dtmf.io.wav.internal.WavSampleReader;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.Objects;

/**
 * {@link AudioSource} implementation returned by
 * {@link WavAudioSourceProvider}. Clean-room, streaming RIFF decoder
 * sitting directly on top of a {@link FileChannel} or an
 * {@link InputStream}: the {@code data} payload is never read into memory
 * in one go (Req 9.12's "no {@code javax.sound.sampled}" constraint is
 * honoured by the parser upstream; this class only sees the already-parsed
 * {@link WaveFormat} and a byte source positioned at the start of the
 * payload).
 *
 * <p><strong>This class is package-private on purpose.</strong> External
 * callers observe a WAV source through the {@link AudioSource} contract
 * returned from {@link WavAudioSourceProvider#open(java.nio.file.Path)} or
 * from {@code AudioSources.open(...)}; no consumer needs to name this
 * type. Making the type itself non-public avoids committing to a
 * stability contract for its constructors or static factories &mdash; the
 * only supported construction paths are through the two package-private
 * factories below, both exclusively invoked by {@link WavAudioSourceProvider}
 * in the same package.
 *
 * <h2>Construction modes</h2>
 *
 * There are exactly two flavours of {@code WavAudioSource}, produced by
 * the two package-private factories on this class:
 *
 * <ul>
 *   <li>{@link #fromChannel(WaveFormat, FileChannel)} &mdash; built by
 *       {@code WavAudioSourceProvider.open(Path)} from a
 *       {@code FileChannel} that the provider opened itself. This source
 *       <em>owns</em> the channel: {@link #canSeek()} returns
 *       {@code true}, {@link #seek(long)} repositions the channel to
 *       {@code dataStartByteOffset + frameIndex * bytesPerFrame}
 *       (Requirements 9.13, 3.10), and {@link #close()} closes the
 *       channel.</li>
 *   <li>{@link #fromCallerStream(WaveFormat, InputStream)} &mdash; built by
 *       {@code WavAudioSourceProvider.open(InputStream, String)} from the
 *       caller's {@link InputStream}. This source <em>does not own</em>
 *       the stream (Requirement 4.10): {@link #canSeek()} returns
 *       {@code false} because {@link InputStream} has no seek operation
 *       even when the underlying resource would support one
 *       (Requirement 3.9); {@link #seek(long)} throws
 *       {@link UnsupportedOperationException} identifying this class
 *       (Requirement 3.11); {@link #close()} transitions the source to
 *       the closed state but leaves the caller's stream open so the
 *       caller can continue using it or close it on its own schedule.</li>
 * </ul>
 *
 * <p>The stream factory wraps non-markable inputs in a
 * {@link BufferedInputStream} so the {@link WavSampleReader} underneath
 * can pull bytes with the short-read tolerance the {@link InputStream}
 * contract requires. The wrapper is deliberately <em>not</em> closed on
 * {@link #close()}; closing a {@link BufferedInputStream} closes its
 * underlying stream, which would violate Requirement 4.10.
 *
 * <h2>Sample decode and normalisation</h2>
 *
 * Every {@link #read(double[], int, int)} call delegates to
 * {@link WavSampleReader#readFrames(double[], int, int)}. The reader
 * dispatches through the shared {@code SampleConversion} helper
 * ({@code com.tino1b2be.dtmf.io.internal.SampleConversion}), so
 * PCM integer samples are divided by {@code 2^(bitDepth - 1)} and IEEE
 * float samples are widened to {@code double} without scaling
 * (Requirements 3.6, 9.14). This is the same normalisation
 * {@code RawPcmAudioSource} uses; the two paths share a single decode
 * table by design.
 *
 * <h2>Lifecycle and thread safety</h2>
 *
 * Instances are <em>not</em> thread-safe &mdash; the frame cursor inside
 * the underlying {@link WavSampleReader}, the closed flag on this
 * object, and (for channel-backed sources) the underlying
 * {@link FileChannel}'s position all hold mutable state. Post-close
 * behaviour follows the {@link AudioSource} contract: any call to
 * {@link #read(double[], int, int)}, {@link #read(double[])}, or
 * {@link #seek(long)} after {@link #close()} throws {@link IOException}
 * identifying the source as closed (Requirement 3.14).
 * {@link #close()} itself is idempotent.
 *
 * @since 2.0.0
 * @see AudioSource
 * @see WavAudioSourceProvider
 * @see WavSampleReader
 */
final class WavAudioSource implements AudioSource {

    /**
     * Error message prefix used by {@link #seek(long)} when the source is
     * stream-backed. Matches the wording Requirement 3.11 prescribes
     * ("identify the implementing class").
     */
    private static final String SEEK_UNSUPPORTED_MESSAGE =
            "WavAudioSource backed by an InputStream does not support seek";

    /** Parsed WAV header metadata. Never null. */
    private final WaveFormat format;

    /**
     * Underlying sample reader that decodes {@code data}-chunk bytes on
     * demand. Always bound to the same byte source this
     * {@code WavAudioSource} was built with; never null.
     */
    private final WavSampleReader reader;

    /**
     * Backing {@link FileChannel} for the channel-backed variant; null
     * for the stream-backed variant. When non-null the source owns this
     * channel and closes it in {@link #close()}.
     */
    private final FileChannel channel;

    /**
     * Backing {@link InputStream} for the stream-backed variant; null for
     * the channel-backed variant. This source does <em>not</em> own the
     * stream per Requirement 4.10, so {@link #close()} never closes it.
     * Held as a field purely for diagnostics and so
     * {@link #isStreamBacked()} stays obvious to read.
     */
    @SuppressWarnings("unused")
    private final InputStream stream;

    /**
     * Once {@link #close()} flips this to {@code true}, subsequent
     * {@link #read(double[], int, int)} and {@link #seek(long)} calls
     * throw {@link IOException} per the {@link AudioSource} contract
     * (Requirement 3.14). Also used to make {@link #close()} idempotent.
     */
    private boolean closed;

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    /**
     * Shared private constructor. Exactly one of {@code channel} and
     * {@code stream} is non-null; the static factories below enforce
     * that invariant.
     */
    private WavAudioSource(
            WaveFormat format,
            WavSampleReader reader,
            FileChannel channel,
            InputStream stream) {
        this.format = format;
        this.reader = reader;
        this.channel = channel;
        this.stream = stream;
        this.closed = false;
    }

    /**
     * Build a channel-backed {@code WavAudioSource} that owns
     * {@code channel}. The channel MUST already be positioned at
     * {@code format.dataStartByteOffset()} (i.e. at the first byte of
     * the {@code data} chunk's payload); the WAV provider leaves it
     * there after the RIFF parse completes.
     *
     * <p>The returned source reports {@link #canSeek()} as {@code true}
     * (Requirement 9.13) and {@link #close()} closes the channel.
     *
     * @param format  validated WAV metadata; must be non-null
     * @param channel open, seekable file channel positioned at the start
     *                of the {@code data} payload; must be non-null. The
     *                returned source takes ownership.
     * @return a new channel-backed {@code WavAudioSource}
     * @throws NullPointerException if either argument is {@code null}
     */
    static WavAudioSource fromChannel(WaveFormat format, FileChannel channel) {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(channel, "channel");
        WavSampleReader reader = new WavSampleReader(format, channel);
        return new WavAudioSource(format, reader, channel, null);
    }

    /**
     * Build a stream-backed {@code WavAudioSource} wrapping the
     * caller's {@link InputStream}. The stream MUST already be positioned
     * at {@code format.dataStartByteOffset()} (i.e. at the first byte of
     * the {@code data} chunk's payload); the WAV provider leaves it
     * there after the RIFF parse completes.
     *
     * <p>Non-markable streams are wrapped in a {@link BufferedInputStream}
     * so the underlying {@link WavSampleReader} can rely on the
     * short-read semantics the {@link InputStream} contract guarantees
     * for buffered streams. The wrapper is retained internally but
     * <em>never</em> closed by {@link #close()}, because closing a
     * {@link BufferedInputStream} also closes its underlying source and
     * that would violate Requirement 4.10 for caller-supplied streams.
     *
     * <p>The returned source reports {@link #canSeek()} as {@code false}
     * (Requirement 3.9; {@link InputStream} has no seek operation even
     * for seekable underlying resources), and {@link #seek(long)} throws
     * {@link UnsupportedOperationException} identifying
     * {@code WavAudioSource} (Requirement 3.11). {@link #close()}
     * transitions this source to the closed state but does NOT close the
     * caller's stream.
     *
     * @param format validated WAV metadata; must be non-null
     * @param stream open input stream positioned at the start of the
     *               {@code data} payload; must be non-null. The caller
     *               retains ownership; this source does not close it.
     * @return a new stream-backed {@code WavAudioSource}
     * @throws NullPointerException if either argument is {@code null}
     */
    static WavAudioSource fromCallerStream(WaveFormat format, InputStream stream) {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(stream, "stream");
        // Wrap non-markable streams so the inner reader can pull short
        // reads confidently. The wrapper is NOT closed on close() -- see
        // the class Javadoc for the Requirement-4.10 rationale.
        InputStream effective = stream.markSupported()
                ? stream
                : new BufferedInputStream(stream);
        WavSampleReader reader = new WavSampleReader(format, effective);
        return new WavAudioSource(format, reader, null, effective);
    }

    // ------------------------------------------------------------------
    // Metadata accessors
    // ------------------------------------------------------------------

    @Override
    public int sampleRate() {
        return format.sampleRate();
    }

    @Override
    public int channelCount() {
        return format.channelCount();
    }

    @Override
    public int bitDepth() {
        return format.bitDepth();
    }

    @Override
    public long totalFrames() {
        return format.totalFrames();
    }

    @Override
    public long currentFrame() {
        return reader.frameCursor();
    }

    @Override
    public boolean canSeek() {
        // Channel-backed sources are seekable (Req 9.13); stream-backed
        // sources are not (Req 3.9, 3.11) because InputStream lacks any
        // seek primitive even when the underlying resource would support
        // one. Callers who need random access must use the Path overload
        // of AudioSources.open(...).
        return channel != null;
    }

    // ------------------------------------------------------------------
    // Read path
    // ------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to
     * {@link WavSampleReader#readFrames(double[], int, int)} with the
     * closed-state guard from Requirement 3.14 applied up front. The
     * reader walks the {@code data} payload frame-by-frame, dispatching
     * each interleaved sample through the shared
     * {@code SampleConversion} decode table (Requirements 3.6, 9.14).
     *
     * @throws IOException               if the source has been
     *                                   {@linkplain #close() closed}
     *                                   (Requirement 3.14), or if the
     *                                   underlying byte source throws
     * @throws NullPointerException      if {@code buffer} is {@code null}
     * @throws IllegalArgumentException  if {@code offset < 0},
     *                                   {@code length < 0}, or the
     *                                   required buffer span exceeds
     *                                   {@code buffer.length}
     */
    @Override
    public int read(double[] buffer, int offset, int length) throws IOException {
        if (closed) {
            throw new IOException("WavAudioSource is closed");
        }
        return reader.readFrames(buffer, offset, length);
    }

    // ------------------------------------------------------------------
    // Seek path
    // ------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Channel-backed sources translate {@code frameIndex} into an
     * absolute byte offset of
     * {@code dataStartByteOffset + frameIndex * bytesPerFrame} and
     * reposition the underlying {@link FileChannel} before updating the
     * reader's bookkeeping cursor (Requirement 9.13 &rarr; 3.10).
     * Stream-backed sources throw {@link UnsupportedOperationException}
     * identifying this class (Requirement 3.11).
     *
     * @throws IOException                   if the source has been
     *                                       {@linkplain #close() closed}
     *                                       (Requirement 3.14), or if
     *                                       the channel reposition
     *                                       fails
     * @throws UnsupportedOperationException if this source is
     *                                       stream-backed
     *                                       (Requirement 3.11)
     * @throws IllegalArgumentException      if {@code frameIndex} is
     *                                       outside
     *                                       {@code [0, totalFrames()]}
     *                                       (Requirement 3.12)
     */
    @Override
    public void seek(long frameIndex) throws IOException {
        if (closed) {
            throw new IOException("WavAudioSource is closed");
        }
        if (channel == null) {
            // Req 3.11: identify the implementing class in the message.
            throw new UnsupportedOperationException(SEEK_UNSUPPORTED_MESSAGE);
        }
        long totalFrames = format.totalFrames();
        if (frameIndex < 0L || frameIndex > totalFrames) {
            throw new IllegalArgumentException(
                    "frameIndex must be in [0, " + totalFrames
                            + "], was " + frameIndex);
        }
        long byteOffset = format.dataStartByteOffset()
                + frameIndex * (long) format.bytesPerFrame();
        channel.position(byteOffset);
        reader.seekToFrame(frameIndex);
    }

    // ------------------------------------------------------------------
    // Close
    // ------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Channel-backed sources close the underlying {@link FileChannel}
     * the provider opened for them. Stream-backed sources leave the
     * caller's {@link InputStream} untouched (Requirement 4.10) and
     * simply transition this source into the closed state so subsequent
     * {@link #read(double[], int, int)} and {@link #seek(long)} calls
     * throw {@link IOException} (Requirement 3.14).
     *
     * <p>This method is idempotent: a second and subsequent invocation
     * is a no-op.
     *
     * @throws IOException if closing the owned channel fails
     */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        if (channel != null) {
            channel.close();
        }
        // Stream-backed variant: never close the caller-supplied stream
        // (Req 4.10). The BufferedInputStream wrapper, if we created one,
        // is also left open because closing it would cascade to the
        // caller's underlying stream.
    }

    // ------------------------------------------------------------------
    // Internals exposed for tests in the same package
    // ------------------------------------------------------------------

    /**
     * @return {@code true} if this source was built via
     *         {@link #fromCallerStream(WaveFormat, InputStream)}; used
     *         by tests to pin the construction-mode branching. Not part
     *         of any external API.
     */
    boolean isStreamBacked() {
        return channel == null;
    }
}
