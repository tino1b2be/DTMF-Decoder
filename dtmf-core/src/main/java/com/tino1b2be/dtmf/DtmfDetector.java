package com.tino1b2be.dtmf;

import java.util.Objects;
import java.util.function.Consumer;

import com.tino1b2be.dtmf.internal.AnalysisPipeline;
import com.tino1b2be.dtmf.internal.SampleConverter;

/**
 * Push-based DTMF detector: the caller feeds chunks of audio samples and
 * receives {@link DtmfTone} emissions via a registered {@link Consumer}
 * callback.
 *
 * <p>{@code DtmfDetector} is the streaming half of the public API (pair with
 * the batch {@link DtmfDecoder} and the pull-style {@code DtmfStream}). Per
 * Requirements&nbsp;6.1&ndash;6.8 and 4.9, it exposes:
 *
 * <ul>
 *   <li>{@link #onTone(Consumer)} &mdash; register a callback; a second call
 *       replaces the first.</li>
 *   <li>{@link #process(double[])}, {@link #process(double[], int, int)},
 *       {@link #process(short[])}, {@link #process(float[])},
 *       {@link #process(int[])} &mdash; feed a chunk. The callback is invoked
 *       synchronously during the same call when the chunk contains the first
 *       non-confirming analysis block after a confirmed tone (the
 *       Tone_End_Event).</li>
 *   <li>{@link #flush()} &mdash; finalise any tone still in flight.</li>
 *   <li>{@link #samplesProcessed()} &mdash; cumulative input sample count.</li>
 * </ul>
 *
 * <p><strong>Channel handling.</strong> The detector honours
 * {@link DtmfConfig#channelMode()}:
 *
 * <ul>
 *   <li>{@link ChannelMode#MONO MONO} &mdash; one internal
 *       {@link AnalysisPipeline}, every emission tagged {@code channel = 0}.</li>
 *   <li>{@link ChannelMode#STEREO_INDEPENDENT STEREO_INDEPENDENT} &mdash; two
 *       pipelines; even-index samples feed the left channel ({@code 0}),
 *       odd-index samples feed the right ({@code 1}). Odd-length chunks are
 *       rejected with {@link IllegalArgumentException} (Requirement 13.5).</li>
 *   <li>{@link ChannelMode#STEREO_DOWNMIX STEREO_DOWNMIX} &mdash; adjacent
 *       pairs averaged into a single mono stream; emissions tagged
 *       {@code channel = 0}. Odd-length chunks are rejected
 *       (Requirement 13.5).</li>
 * </ul>
 *
 * <p><strong>Chunk invariance</strong> (Requirement 6.7). Calling
 * {@code process} repeatedly with chunks whose concatenation equals a single
 * buffer {@code B} emits the same tones, in the same order, with the same
 * cumulative sample indices, as a single {@code process(B)} call on a fresh
 * detector. This holds by construction because the pipeline does not buffer
 * chunks &mdash; it streams samples one at a time through a
 * block-synchronous state machine whose only non-trivial state is
 * analysis-block-local.
 *
 * <p><strong>Cumulative sample indices</strong> (Requirement 6.8).
 * {@link #samplesProcessed()} counts every sample ever passed to any
 * {@code process} overload, including both channels of a stereo input. For
 * stereo modes it is the number of interleaved samples consumed, not the
 * per-channel count. Emitted tones' {@code startSample}/{@code endSample}
 * come from the per-channel analysis pipeline, so for
 * {@code STEREO_INDEPENDENT} those indices advance half as fast as
 * {@code samplesProcessed()}.
 *
 * <p><strong>Format overloads</strong> (Requirement 4.9). The {@code short[]},
 * {@code float[]}, and {@code int[]} overloads convert into a reusable
 * {@code double[] scratch} buffer owned by this detector and then feed the
 * normalised samples through the same path as {@code process(double[])}.
 * No per-chunk allocation occurs on the hot path &mdash; the scratch buffer
 * is resized only when a chunk exceeds its current capacity.
 *
 * <p><strong>Thread safety</strong> (Requirement 6.6). Instances are not
 * thread-safe. Concurrent {@code process} calls on the same detector have
 * undefined behaviour.
 *
 * @since 2.0.0
 */
public final class DtmfDetector {

    private final DtmfConfig config;

    /** Currently registered callback, or {@code null} if none. */
    private Consumer<DtmfTone> callback;

    /**
     * Forwarding consumer that routes a pipeline emission to whatever
     * {@link #onTone(Consumer)} callback is currently registered. Bound via a
     * method reference so it does not capture the {@link #callback} field at
     * construction time &mdash; each dispatch reads the current value, which
     * is how {@link #onTone(Consumer)} can replace the callback mid-stream.
     */
    private final Consumer<DtmfTone> forwarder = this::dispatch;

    /** Left / mono / downmix pipeline. Never {@code null}. */
    private final AnalysisPipeline leftOrMono;

    /** Right pipeline for {@link ChannelMode#STEREO_INDEPENDENT}; {@code null} otherwise. */
    private final AnalysisPipeline right;

    /**
     * Reusable scratch buffer for non-{@code double} format conversion. Grown
     * on demand; never shrunk. Sharing one buffer across all format overloads
     * is safe because {@link DtmfDetector} is not thread-safe.
     */
    private double[] scratch;

    /**
     * Cumulative count of samples passed to any {@code process} overload,
     * regardless of channel mode.
     */
    private long samplesProcessed;

    /**
     * Construct a new detector for the given configuration.
     *
     * @param config detection configuration; non-null
     * @throws NullPointerException if {@code config} is {@code null}
     */
    public DtmfDetector(DtmfConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.leftOrMono = new AnalysisPipeline(config, 0, forwarder);
        this.right = (config.channelMode() == ChannelMode.STEREO_INDEPENDENT)
                ? new AnalysisPipeline(config, 1, forwarder)
                : null;
    }

    /**
     * Register the callback that receives every emitted {@link DtmfTone}.
     * Calling {@code onTone} replaces any previously-registered callback
     * (Requirement 6.1). Passing {@code null} clears the callback.
     *
     * @param callback consumer to receive emissions, or {@code null} to clear
     */
    public void onTone(Consumer<DtmfTone> callback) {
        this.callback = callback;
    }

    /**
     * Feed a full chunk of {@code double[]} samples through the detector.
     *
     * <p>Equivalent to {@code process(chunk, 0, chunk.length)}.
     *
     * @param chunk sample chunk; non-null. Samples are expected in the
     *              range {@code [-1.0, 1.0]}
     * @throws NullPointerException     if {@code chunk} is {@code null}
     * @throws IllegalArgumentException if the channel mode is stereo and
     *                                  {@code chunk.length} is odd
     */
    public void process(double[] chunk) {
        Objects.requireNonNull(chunk, "chunk");
        process(chunk, 0, chunk.length);
    }

    /**
     * Feed a sub-range of a {@code double[]} through the detector. The
     * callback registered via {@link #onTone(Consumer)} is invoked for every
     * tone confirmed within this chunk, synchronously before this method
     * returns.
     *
     * @param chunk  sample buffer; non-null
     * @param offset starting index; must satisfy
     *               {@code 0 <= offset && offset + length <= chunk.length}
     * @param length number of samples to consume; must be {@code >= 0}
     * @throws NullPointerException      if {@code chunk} is {@code null}
     * @throws IndexOutOfBoundsException if {@code offset}/{@code length} are
     *                                   out of range
     * @throws IllegalArgumentException  if the channel mode is stereo and
     *                                   {@code length} is odd
     */
    public void process(double[] chunk, int offset, int length) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.checkFromIndexSize(offset, length, chunk.length);

        switch (config.channelMode()) {
            case MONO:
                leftOrMono.acceptAll(chunk, offset, length);
                break;

            case STEREO_INDEPENDENT:
                requireEvenLength(length);
                feedStereoIndependent(chunk, offset, length);
                break;

            case STEREO_DOWNMIX:
                requireEvenLength(length);
                feedStereoDownmix(chunk, offset, length);
                break;

            default:
                throw new AssertionError("Unreachable channel mode: " + config.channelMode());
        }
        samplesProcessed += length;
    }

    /**
     * Feed a chunk of signed PCM16 samples through the detector. Samples are
     * normalised to {@code double} via division by {@code 32768.0}
     * (Requirement 4.5).
     *
     * @param chunk PCM16 sample chunk; non-null
     * @throws NullPointerException     if {@code chunk} is {@code null}
     * @throws IllegalArgumentException if the channel mode is stereo and
     *                                  {@code chunk.length} is odd
     */
    public void process(short[] chunk) {
        Objects.requireNonNull(chunk, "chunk");
        ensureScratch(chunk.length);
        SampleConverter.fromShortInto(chunk, scratch);
        process(scratch, 0, chunk.length);
    }

    /**
     * Feed a chunk of normalised {@code float} samples through the detector.
     * Samples are widened to {@code double} (Requirement 4.6).
     *
     * @param chunk float sample chunk; non-null. Samples are expected in the
     *              range {@code [-1.0, 1.0]}
     * @throws NullPointerException     if {@code chunk} is {@code null}
     * @throws IllegalArgumentException if the channel mode is stereo and
     *                                  {@code chunk.length} is odd
     */
    public void process(float[] chunk) {
        Objects.requireNonNull(chunk, "chunk");
        ensureScratch(chunk.length);
        SampleConverter.fromFloatInto(chunk, scratch);
        process(scratch, 0, chunk.length);
    }

    /**
     * Feed a chunk of signed PCM32 samples through the detector. Samples are
     * normalised to {@code double} via division by {@code 2^31}
     * (Requirement 4.7).
     *
     * @param chunk PCM32 sample chunk; non-null
     * @throws NullPointerException     if {@code chunk} is {@code null}
     * @throws IllegalArgumentException if the channel mode is stereo and
     *                                  {@code chunk.length} is odd
     */
    public void process(int[] chunk) {
        Objects.requireNonNull(chunk, "chunk");
        ensureScratch(chunk.length);
        SampleConverter.fromIntInto(chunk, scratch);
        process(scratch, 0, chunk.length);
    }

    /**
     * Finalise any tone still in flight. After {@code flush()} returns, each
     * internal pipeline is back in its idle state and can be fed further
     * samples.
     *
     * <p>If a tone was still Active or had just entered Ending when
     * {@code flush} was called, it is emitted synchronously (via the
     * registered callback) provided its duration so far meets the configured
     * minimum (Requirement 6.3).
     */
    public void flush() {
        leftOrMono.flush();
        if (right != null) {
            right.flush();
        }
    }

    /**
     * {@return the cumulative number of samples passed to any
     *          {@code process} overload since construction}
     *
     * <p>For stereo modes this counts interleaved input samples
     * (i.e. {@code left + right}), not per-channel samples.
     */
    public long samplesProcessed() {
        return samplesProcessed;
    }

    // ----- helpers -----

    /**
     * Internal dispatch for the forwarding consumer. Reads
     * {@link #callback} at invocation time, so {@code onTone} replacements
     * take effect immediately.
     */
    private void dispatch(DtmfTone tone) {
        Consumer<DtmfTone> cb = callback;
        if (cb != null) {
            cb.accept(tone);
        }
    }

    private static void requireEvenLength(int length) {
        if ((length & 1) != 0) {
            throw new IllegalArgumentException(
                    "stereo input length must be even, was " + length);
        }
    }

    private void feedStereoIndependent(double[] chunk, int offset, int length) {
        // Even indices -> left (channel 0); odd -> right (channel 1).
        int end = offset + length;
        for (int i = offset; i < end; i += 2) {
            leftOrMono.accept(chunk[i]);
            right.accept(chunk[i + 1]);
        }
    }

    private void feedStereoDownmix(double[] chunk, int offset, int length) {
        int end = offset + length;
        for (int i = offset; i < end; i += 2) {
            leftOrMono.accept((chunk[i] + chunk[i + 1]) * 0.5);
        }
    }

    /** Grow the scratch buffer to at least {@code needed} slots. Never shrinks. */
    private void ensureScratch(int needed) {
        if (scratch == null || scratch.length < needed) {
            scratch = new double[needed];
        }
    }
}
