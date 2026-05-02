package com.tino1b2be.dtmf.io;

import java.io.Closeable;
import java.io.IOException;
import java.util.Objects;

/**
 * Pull-based read interface returned by every {@link AudioSourceProvider}
 * and by {@link RawPcmAudioSource}. Samples read through this interface are
 * always normalised to {@code [-1.0, 1.0]}, regardless of the underlying
 * format's native encoding (Requirement 3.6). The shape deliberately
 * mirrors {@link java.io.InputStream} and
 * {@code javax.sound.sampled.AudioInputStream}: callers invoke
 * {@link #read(double[], int, int)} and receive either a frame count or
 * {@code -1} at end of stream.
 *
 * <p>This is the one interface that unifies WAV, MP3, raw PCM, and any
 * future provider module. Format-specific decoding logic lives entirely
 * inside the implementation; callers see the same
 * {@code (sampleRate, channelCount, bitDepth, totalFrames)} metadata and
 * the same interleaved normalised {@code double[]} output regardless of
 * which provider served them.
 *
 * <h2>Metadata invariants</h2>
 * <ul>
 *   <li>{@link #sampleRate()} is strictly positive (Requirement 3.2).</li>
 *   <li>{@link #channelCount()} is strictly positive (Requirement 3.3).</li>
 *   <li>{@link #bitDepth()} is one of {@code {16, 24, 32, 64}} for PCM
 *       integer sources, {@code {32}} for 32-bit IEEE float,
 *       {@code {64}} for 64-bit IEEE double (Requirement 3.4). MP3
 *       sources report {@code 16} because the JLayer-based provider
 *       decodes to 16-bit PCM.</li>
 *   <li>{@link #totalFrames()} returns a non-negative frame count, or
 *       {@code -1L} when the total is not known up front (typical for
 *       forward-only streams such as MP3) (Requirement 3.5).</li>
 * </ul>
 *
 * <h2>Channel interleaving</h2>
 *
 * When {@link #channelCount()} is {@code 1}, the output buffer holds one
 * sample per frame. When it is {@code 2}, frame {@code k} lives at
 * {@code buffer[offset + 2k]} (left) and {@code buffer[offset + 2k + 1]}
 * (right). For more than two channels, the file's native channel order
 * follows right (Requirement 3.7). This matches the shape
 * {@code com.tino1b2be.dtmf.DtmfDecoder} already consumes for
 * {@code ChannelMode.STEREO_INDEPENDENT}.
 *
 * <h2>Return-code conventions</h2>
 *
 * {@link #read(double[], int, int)} returns a non-negative frame count on
 * success, or {@code -1} once the source is exhausted and no more samples
 * will ever be produced (Requirement 3.6). A return value of {@code 0} is
 * <em>not</em> end of stream: it means "no samples available right now,
 * try again." Callers should treat {@code 0} as a retry signal and only
 * treat {@code -1} as terminal.
 *
 * <h2>Buffer ownership</h2>
 *
 * {@link #read(double[], int, int)} writes into the caller-supplied
 * {@code double[]} and must not retain a reference to it after the call
 * returns (Requirement 3.15). Callers are free to reuse, reallocate, or
 * overwrite the buffer between reads; implementations that cache samples
 * internally must copy into their own storage.
 *
 * <h2>Stream ownership on close</h2>
 *
 * {@link #close()} releases any resources the source owns — file channels
 * it opened, decoder state, native handles — and is idempotent: a second
 * call is a no-op (Requirement 3.14 implies this; the stream contract on
 * {@link Closeable} codifies it). A source obtained via
 * {@link AudioSources#open(java.io.InputStream, String)} only closes
 * streams the provider wrapped or opened itself; it does <em>not</em>
 * close caller-supplied {@link java.io.InputStream}s (Requirement 4.10).
 * A source obtained via {@link AudioSources#open(java.nio.file.Path)} or
 * {@link AudioSources#open(java.net.URL)} owns the stream it opened and
 * will close it on {@code close()}.
 *
 * <h2>Thread safety</h2>
 *
 * Instances are <em>not</em> thread-safe. A single {@code AudioSource}
 * must be driven by at most one thread at a time; callers that need
 * concurrent access must either serialise externally or open one source
 * per thread. Provider implementations are free to assume single-threaded
 * access to their internal state.
 *
 * <h2>Lifecycle after close</h2>
 *
 * Once {@link #close()} has returned, any subsequent call to
 * {@link #read(double[], int, int)}, {@link #read(double[])}, or
 * {@link #seek(long)} throws {@link IOException} identifying the source
 * as closed (Requirement 3.14). {@link #close()} itself remains callable
 * and is a no-op on subsequent invocations.
 *
 * @since 2.1.0
 * @see AudioSourceProvider
 * @see AudioSources
 * @see RawPcmAudioSource
 */
public interface AudioSource extends Closeable {

    /**
     * Sample rate of the source in Hertz.
     *
     * @return sample rate in Hz; always strictly positive
     *         (Requirement 3.2)
     */
    int sampleRate();

    /**
     * Channel count of the source.
     *
     * @return channel count; always strictly positive
     *         (Requirement 3.3). {@code 1} for mono, {@code 2} for
     *         stereo, and so on.
     */
    int channelCount();

    /**
     * Native sample bit depth of the source.
     *
     * <p>The value is one of {@code {16, 24, 32, 64}} for PCM integer
     * sources, {@code {32}} for 32-bit IEEE float sources, and
     * {@code {64}} for 64-bit IEEE double sources (Requirement 3.4).
     * MP3 sources report {@code 16} because the JLayer-based decoder
     * emits 16-bit signed PCM.
     *
     * <p>Normalisation to the {@code [-1.0, 1.0]} {@code double} samples
     * returned from {@link #read(double[], int, int)} is performed by
     * the source: integer samples are divided by
     * {@code 2^(bitDepth - 1)}; IEEE float samples are widened without
     * scaling.
     *
     * @return native sample bit depth; one of the values listed above
     */
    int bitDepth();

    /**
     * Total number of sample frames available from the source, or
     * {@code -1L} if the total is unknown.
     *
     * <p>A seekable source (see {@link #canSeek()}) that knows its
     * length up front returns a non-negative count here; a forward-only
     * stream whose length is not recoverable without scanning the whole
     * input (a VBR MP3 without a Xing header, for example) returns
     * {@code -1L} (Requirement 3.5).
     *
     * @return total frame count, or {@code -1L} if unknown
     */
    long totalFrames();

    /**
     * Read up to {@code length} sample frames into {@code buffer}
     * starting at {@code offset}.
     *
     * <p>When {@link #channelCount()} is greater than {@code 1}, channels
     * are interleaved in the output buffer in the order left, right,
     * then any additional channels in the source's native channel order
     * (Requirement 3.7). Exactly {@code n * channelCount()} samples are
     * written when this call returns {@code n >= 0}.
     *
     * <p>Samples are normalised to {@code [-1.0, 1.0]}: integer samples
     * are divided by {@code 2^(bitDepth - 1)}; IEEE float samples are
     * widened to {@code double} without scaling (Requirement 3.6).
     *
     * <p>Return conventions:
     * <ul>
     *   <li>A non-negative return value is the number of frames
     *       actually written; it is always in {@code [0, length]}.</li>
     *   <li>{@code -1} means the source is exhausted and no further
     *       frames will ever be produced (Requirement 3.6).</li>
     *   <li>{@code 0} is <em>not</em> end of stream. It means "no
     *       frames available right now" and is a valid, retry-able
     *       return value; callers should re-invoke rather than treating
     *       it as terminal.</li>
     * </ul>
     *
     * <p>The caller owns {@code buffer}. Implementations do not retain
     * a reference to it after the call returns (Requirement 3.15);
     * callers are free to reuse, reallocate, or overwrite it between
     * reads.
     *
     * @param buffer destination buffer; non-null
     * @param offset starting index into {@code buffer};
     *               {@code 0 <= offset <= buffer.length}
     * @param length maximum number of frames to write; the buffer must
     *               hold at least {@code length * channelCount()}
     *               samples starting at {@code offset}
     * @return number of frames actually read ({@code 0 <= n <= length}),
     *         or {@code -1} at end of stream
     * @throws IOException if the source has been {@linkplain #close()
     *                     closed} (Requirement 3.14) or an underlying
     *                     I/O error occurs
     */
    int read(double[] buffer, int offset, int length) throws IOException;

    /**
     * Read up to {@code buffer.length / channelCount()} frames into
     * {@code buffer}, starting at index {@code 0}. Behaves identically
     * to {@code read(buffer, 0, buffer.length)} (Requirement 3.8);
     * kept on the interface as a default so implementations only have
     * to supply the three-argument form.
     *
     * @param buffer destination buffer; non-null
     * @return number of frames actually read, or {@code -1} at end of
     *         stream
     * @throws IOException if the source has been closed or an
     *                     underlying I/O error occurs
     * @throws NullPointerException if {@code buffer} is {@code null}
     */
    default int read(double[] buffer) throws IOException {
        Objects.requireNonNull(buffer, "buffer");
        return read(buffer, 0, buffer.length);
    }

    /**
     * Whether random-access seeking is supported on this source.
     *
     * <p>Seekable sources (file-backed WAV, {@link RawPcmAudioSource})
     * return {@code true}; forward-only sources (MP3, any
     * {@link java.io.InputStream}-backed source) return {@code false}
     * (Requirement 3.9).
     *
     * @return {@code true} if {@link #seek(long)} is supported
     */
    boolean canSeek();

    /**
     * Reposition the read cursor so the next {@link #read(double[], int, int)}
     * returns frames starting at {@code frameIndex} from the start of
     * the source (Requirement 3.10).
     *
     * @param frameIndex zero-based frame index to reposition to
     * @throws UnsupportedOperationException if {@link #canSeek()}
     *                                       returns {@code false}; the
     *                                       exception message
     *                                       identifies the implementing
     *                                       class (Requirement 3.11)
     * @throws IllegalArgumentException if {@code frameIndex < 0}, or
     *                                  if {@link #totalFrames()} is
     *                                  non-negative and
     *                                  {@code frameIndex > totalFrames()};
     *                                  the exception message
     *                                  identifies the offending value
     *                                  and the valid range
     *                                  (Requirement 3.12)
     * @throws IOException if the source has been {@linkplain #close()
     *                     closed} (Requirement 3.14) or an underlying
     *                     I/O error occurs
     */
    void seek(long frameIndex) throws IOException;

    /**
     * Zero-based index of the next frame that will be returned by
     * {@link #read(double[], int, int)} (Requirement 3.13).
     *
     * <p>On a freshly opened source the return value is {@code 0}.
     * After a successful {@code read} that returned {@code n} frames,
     * it increases by {@code n}. After a successful
     * {@link #seek(long)} to {@code f}, it equals {@code f}. At end of
     * stream it equals {@link #totalFrames()} when that value is
     * known.
     *
     * @return zero-based frame index of the next frame to be read
     */
    long currentFrame();

    /**
     * Release any resources the source owns.
     *
     * <p>Closing a source obtained via
     * {@link AudioSources#open(java.nio.file.Path)} or
     * {@link AudioSources#open(java.net.URL)} also closes the
     * underlying stream that {@code AudioSources} opened on the
     * caller's behalf. Closing a source obtained via
     * {@link AudioSources#open(java.io.InputStream, String)} does
     * <em>not</em> close the caller-supplied stream
     * (Requirement 4.10); the caller retains ownership of any
     * {@link java.io.InputStream} they handed in.
     *
     * <p>This method is idempotent: a second and subsequent
     * invocation is a no-op. After {@code close()} has returned, any
     * call to {@link #read(double[], int, int)}, {@link #read(double[])},
     * or {@link #seek(long)} throws {@link IOException} identifying
     * the source as closed (Requirement 3.14).
     *
     * @throws IOException if releasing the underlying resources fails
     */
    @Override
    void close() throws IOException;
}
