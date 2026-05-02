package com.tino1b2be.dtmf.internal;

import java.util.Objects;
import java.util.function.Consumer;

import com.tino1b2be.dtmf.DtmfConfig;
import com.tino1b2be.dtmf.DtmfTone;
import com.tino1b2be.dtmf.WindowFunction;
import com.tino1b2be.goertzel.GoertzelBank;

/**
 * Block-level DTMF detection engine shared by {@code DtmfDecoder} (batch)
 * and {@code DtmfDetector} (push).
 *
 * <p>One {@code AnalysisPipeline} owns:
 *
 * <ul>
 *   <li>a small {@code double[]} block buffer sized to
 *       {@link DtmfConfig#analysisBlockSize()},</li>
 *   <li>a {@link GoertzelBank} tuned to the eight DTMF frequencies
 *       ({@link FrequencyBins#ALL_EIGHT}), and</li>
 *   <li>the tone-confirmation state machine from {@code design.md}
 *       (Idle → Confirming → Active → Ending → Idle).</li>
 * </ul>
 *
 * <p>Samples enter via {@link #accept(double)} or {@link #acceptAll(double[], int, int)}
 * one at a time. Every {@code N = analysisBlockSize} samples, the block
 * buffer is windowed (unless the configured {@link WindowFunction} is
 * {@link WindowFunction#RECTANGULAR}) and fed through the Goertzel bank in
 * a single {@link GoertzelBank#computeMagnitudesSquaredInto(double[], double[])}
 * call. The peaks of the low (indices 0&ndash;3 in
 * {@link FrequencyBins#ALL_EIGHT}) and high (indices 4&ndash;7) groups are
 * picked by {@code argmax}; the candidate is validated by
 * {@link ConfidenceScorer#compute(double, double, double)} against
 * {@link DtmfConfig#detectionThreshold()} and by
 * {@link TwistEvaluator#withinTolerance(double, DtmfConfig)}. The valid
 * candidate (or "no candidate") drives the state machine.
 *
 * <p>Emissions are handed to a {@code Consumer<DtmfTone>} sink supplied at
 * construction time. A tone is emitted on the first non-confirming block
 * after the state machine has entered {@code Active}, provided its duration
 * (in samples) is at least
 * {@code round(config.minimumToneDuration() * config.sampleRate())}. Tones
 * shorter than the minimum are discarded.
 *
 * <p>Sample indices reported on each emitted {@link DtmfTone} are cumulative
 * from the first sample ever passed to this pipeline instance; this gives
 * the push detector chunk invariance (Requirement 6.7) by construction.
 *
 * <p>Instances are mutable and not thread-safe. Each pipeline is tagged with
 * a {@code channel} value at construction so the stereo-independent
 * detector can run two pipelines in parallel with the correct channel tag
 * on each emission.
 *
 * <p>Package-private by convention: {@code com.tino1b2be.dtmf.internal.*}
 * is not part of the published API. The type is {@code public} so tests in
 * the same package and the {@code DtmfDetector} in
 * {@code com.tino1b2be.dtmf} can reach it via the existing internal-friend
 * pattern other helpers follow.
 *
 * @since 2.0.0
 */
public final class AnalysisPipeline {

    /** States of the block-level confirmation machine (see {@code design.md}). */
    private enum State {
        /** No tone in flight; waiting for a valid candidate. */
        IDLE,
        /** A candidate key has been seen; awaiting more confirming blocks. */
        CONFIRMING,
        /** Tone is confirmed and still ongoing. */
        ACTIVE,
        /** Tone has seen one non-confirming block; one more break finalises it. */
        ENDING
    }

    // --- Immutable collaborators and config-derived constants ---

    private final DtmfConfig config;
    private final int channel;
    private final Consumer<DtmfTone> sink;

    private final int analysisBlockSize;
    private final int sampleRate;
    private final double detectionThreshold;
    private final int confirmationFrames;
    private final long minimumToneDurationSamples;
    private final WindowFunction windowFunction;

    private final GoertzelBank bank;
    private final double[] blockBuffer;
    private final double[] magnitudes;

    // --- Mutable state ---

    /** Number of samples written into {@link #blockBuffer} since the last block boundary. */
    private int blockPos;

    /**
     * Number of complete analysis blocks evaluated so far. Incremented
     * after each block is processed. The first sample of the block
     * currently being evaluated lives at index {@code blockIndex * N}.
     */
    private long blockIndex;

    /** Cumulative sample count fed through {@link #accept(double)}. */
    private long currentSample;

    private State state = State.IDLE;
    private char candidateKey = 0;
    private int confirmCount;
    private long toneStart;
    private long toneEnd;
    private double toneConfidence;

    /**
     * Construct a pipeline bound to the given config, channel tag, and tone
     * sink.
     *
     * @param config  configuration whose sample rate, analysis block size,
     *                detection threshold, confirmation frames, minimum tone
     *                duration, window function, and twist tolerances all
     *                feed the state machine; non-null
     * @param channel channel tag written onto every emitted {@link DtmfTone};
     *                must be {@code >= 0}. Detectors use {@code 0} for mono
     *                or left, {@code 1} for right
     * @param sink    consumer invoked with each confirmed tone, at the
     *                first non-confirming block (Tone_End_Event); non-null
     * @throws NullPointerException     if {@code config} or {@code sink} is null
     * @throws IllegalArgumentException if {@code channel < 0}
     */
    public AnalysisPipeline(DtmfConfig config, int channel, Consumer<DtmfTone> sink) {
        this.config = Objects.requireNonNull(config, "config");
        this.sink = Objects.requireNonNull(sink, "sink");
        if (channel < 0) {
            throw new IllegalArgumentException(
                    "channel must be >= 0, was " + channel);
        }
        this.channel = channel;

        this.analysisBlockSize = config.analysisBlockSize();
        this.sampleRate = config.sampleRate();
        this.detectionThreshold = config.detectionThreshold();
        this.confirmationFrames = config.confirmationFrames();
        this.windowFunction = config.windowFunction();
        this.minimumToneDurationSamples = Math.round(
                config.minimumToneDuration().toNanos() / 1_000_000_000.0
                        * sampleRate);

        this.bank = new GoertzelBank(sampleRate, FrequencyBins.ALL_EIGHT);
        this.blockBuffer = new double[analysisBlockSize];
        this.magnitudes = new double[FrequencyBins.ALL_EIGHT.length];
    }

    /**
     * Feed a single sample through the pipeline. Samples accumulate into an
     * internal block buffer; every {@code analysisBlockSize} samples a
     * block is evaluated and the state machine advanced, possibly emitting
     * a {@link DtmfTone} to the sink.
     *
     * @param sample next sample in the signal (any finite double)
     */
    public void accept(double sample) {
        blockBuffer[blockPos++] = sample;
        currentSample++;
        if (blockPos == analysisBlockSize) {
            processBlock();
            blockPos = 0;
        }
    }

    /**
     * Feed a range of samples through the pipeline. Equivalent to calling
     * {@link #accept(double)} in order for each element in
     * {@code samples[offset .. offset + length)}.
     *
     * @param samples source array; non-null
     * @param offset  starting index; must satisfy
     *                {@code 0 <= offset && offset + length <= samples.length}
     * @param length  number of samples to consume; must be {@code >= 0}
     * @throws NullPointerException      if {@code samples} is null
     * @throws IndexOutOfBoundsException if {@code offset}/{@code length}
     *                                   describe a range outside {@code samples}
     */
    public void acceptAll(double[] samples, int offset, int length) {
        Objects.requireNonNull(samples, "samples");
        Objects.checkFromIndexSize(offset, length, samples.length);
        int end = offset + length;
        for (int i = offset; i < end; i++) {
            accept(samples[i]);
        }
    }

    /**
     * Finalise any in-progress tone. If the state machine is in
     * {@link State#ACTIVE} or {@link State#ENDING} and the tone duration so
     * far meets the configured minimum, the tone is emitted with
     * {@code endSample = currentSample}. The internal block buffer contents
     * (a possibly-partial block) are <strong>not</strong> processed; flush
     * is a state-machine termination, not a block boundary.
     *
     * <p>After {@code flush()} the pipeline is returned to
     * {@link State#IDLE} and can be fed further samples.
     */
    public void flush() {
        switch (state) {
            case ACTIVE:
                // Tone was still ongoing. Use the cumulative sample count as
                // the tentative end (exclusive).
                toneEnd = currentSample;
                emitIfLongEnough();
                break;
            case ENDING:
                // toneEnd was already set when we transitioned Active -> Ending.
                emitIfLongEnough();
                break;
            case IDLE:
            case CONFIRMING:
            default:
                // Nothing to emit.
                break;
        }
        state = State.IDLE;
        confirmCount = 0;
        candidateKey = 0;
    }

    /**
     * {@return the cumulative number of samples that have been fed to
     *          {@link #accept(double)} since this pipeline was constructed}.
     */
    public long samplesProcessed() {
        return currentSample;
    }

    // --- Block-level evaluation and state machine ---

    /**
     * Process the currently-filled block buffer, advance the state machine,
     * and emit if the block is the first non-confirming block after an
     * {@link State#ACTIVE} tone.
     */
    private void processBlock() {
        // Apply window (no-op for RECTANGULAR).
        if (windowFunction != WindowFunction.RECTANGULAR) {
            windowFunction.applyInPlace(blockBuffer, 0, analysisBlockSize);
        }

        // Run the 8 DTMF filters over this block. This resets the bank
        // before and after.
        bank.computeMagnitudesSquaredInto(blockBuffer, magnitudes);

        // Peak-pick low (indices 0-3) and high (indices 4-7) groups.
        int lowIndex = 0;
        double peakLow = magnitudes[0];
        for (int i = 1; i < 4; i++) {
            if (magnitudes[i] > peakLow) {
                peakLow = magnitudes[i];
                lowIndex = i;
            }
        }
        int highIndex = 0;
        double peakHigh = magnitudes[4];
        for (int i = 5; i < 8; i++) {
            if (magnitudes[i] > peakHigh) {
                peakHigh = magnitudes[i];
                highIndex = i - 4;
            }
        }

        double sumAll = 0.0;
        for (int i = 0; i < 8; i++) {
            sumAll += magnitudes[i];
        }

        double confidence = ConfidenceScorer.compute(peakLow, peakHigh, sumAll);
        double twistDb = TwistEvaluator.twistDb(peakLow, peakHigh);

        boolean valid = confidence >= detectionThreshold
                && TwistEvaluator.withinTolerance(twistDb, config);

        char blockKey = valid ? FrequencyBins.keyFor(lowIndex, highIndex) : 0;

        advanceState(valid, blockKey, confidence);

        // Increment block counter after evaluation so that within this
        // method `blockIndex` refers to the block we just evaluated.
        blockIndex++;
    }

    /**
     * Drive the state machine for one evaluated block.
     *
     * <p>Sample-index arithmetic uses the convention that the block just
     * evaluated is block number {@code blockIndex} (before the post-
     * increment in {@link #processBlock()}), so its first sample is at
     * {@code blockIndex * analysisBlockSize} and its first sample exclusive
     * is at {@code (blockIndex + 1) * analysisBlockSize}.
     */
    private void advanceState(boolean valid, char blockKey, double confidence) {
        long blockStartSample = blockIndex * (long) analysisBlockSize;

        switch (state) {
            case IDLE:
                if (valid) {
                    state = State.CONFIRMING;
                    candidateKey = blockKey;
                    confirmCount = 1;
                    toneStart = blockStartSample;
                    toneConfidence = confidence;
                    if (confirmationFrames == 1) {
                        // Single-block confirmation: promote immediately.
                        state = State.ACTIVE;
                    }
                }
                break;

            case CONFIRMING:
                if (valid && blockKey == candidateKey) {
                    confirmCount++;
                    // Track the best confidence seen during confirmation.
                    if (confidence > toneConfidence) {
                        toneConfidence = confidence;
                    }
                    if (confirmCount >= confirmationFrames) {
                        state = State.ACTIVE;
                    }
                } else {
                    // Different key or invalid: drop back to Idle.
                    state = State.IDLE;
                    confirmCount = 0;
                    candidateKey = 0;
                    // If the new block itself is a valid candidate, start
                    // Confirming on it immediately.
                    if (valid) {
                        state = State.CONFIRMING;
                        candidateKey = blockKey;
                        confirmCount = 1;
                        toneStart = blockStartSample;
                        toneConfidence = confidence;
                        if (confirmationFrames == 1) {
                            state = State.ACTIVE;
                        }
                    }
                }
                break;

            case ACTIVE:
                if (valid && blockKey == candidateKey) {
                    // Still going; track best confidence.
                    if (confidence > toneConfidence) {
                        toneConfidence = confidence;
                    }
                } else {
                    // First non-confirming block: enter Ending, mark
                    // tentative end at this block's first sample.
                    state = State.ENDING;
                    toneEnd = blockStartSample;
                }
                break;

            case ENDING:
                if (valid && blockKey == candidateKey) {
                    // Jitter recovery: resume the same tone.
                    state = State.ACTIVE;
                } else {
                    // Confirmed end of the previous tone. Emit (subject to
                    // the minimum duration check), then either start a new
                    // Confirming for a different valid key, or go Idle.
                    emitIfLongEnough();
                    state = State.IDLE;
                    confirmCount = 0;
                    char previousKey = candidateKey;
                    candidateKey = 0;

                    if (valid && blockKey != previousKey) {
                        state = State.CONFIRMING;
                        candidateKey = blockKey;
                        confirmCount = 1;
                        toneStart = blockStartSample;
                        toneConfidence = confidence;
                        if (confirmationFrames == 1) {
                            state = State.ACTIVE;
                        }
                    }
                }
                break;

            default:
                throw new AssertionError("Unreachable state: " + state);
        }
    }

    /**
     * Emit the currently-tracked tone to the sink if its duration meets the
     * configured minimum. Called from both the Ending&#8594;Idle transition
     * and from {@link #flush()}.
     */
    private void emitIfLongEnough() {
        long duration = toneEnd - toneStart;
        if (duration >= minimumToneDurationSamples && duration > 0) {
            sink.accept(new DtmfTone(
                    candidateKey,
                    toneStart,
                    toneEnd,
                    sampleRate,
                    toneConfidence,
                    channel));
        }
    }
}
