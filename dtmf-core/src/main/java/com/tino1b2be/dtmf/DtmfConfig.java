package com.tino1b2be.dtmf;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

import com.tino1b2be.dtmf.internal.BlockSizer;

/**
 * Immutable configuration for DTMF detection and generation.
 *
 * <p>{@code DtmfConfig} has two tiers. The <em>common</em> tier, expressed via
 * the four static factories ({@link #defaults()}, {@link #forTelephony()},
 * {@link #forVoip()}, {@link #forNoisyAudio()}), covers the scenarios that
 * most callers reach for: 8&nbsp;kHz mono telephony, Q.24 Standard_Twist
 * tolerances, conservative minimum-tone and minimum-gap durations, and a
 * pre-picked number of confirmation frames. The <em>advanced</em> tier, via
 * {@link #advanced()}, adds explicit control over the window function, twist
 * thresholds, confirmation-frame count, and the broader sample-rate domain
 * {@code [4000, 192000]} Hz (Requirement 3.4 vs 3.2 vs 3.3).
 *
 * <p>The ten knobs exactly mirror Requirements 8.1 and 8.2. Six common knobs
 * are: sample rate, analysis block size, minimum tone duration, minimum gap
 * duration, detection threshold, channel mode. Four advanced knobs are:
 * window function, forward twist in dB, reverse twist in dB, and confirmation
 * frames.
 *
 * <p>Validation happens at construction time (Requirement 17): every numeric
 * field is checked against its documented domain, every reference field is
 * null-checked with {@link Objects#requireNonNull(Object, String)}, and the
 * standard factories enforce the narrow sample-rate set
 * {@code {8000, 16000, 44100, 48000}} (Requirement 3.3). The
 * {@code Advanced.build()} path enforces the wider domain. The
 * {@code analysisBlockSize} is auto-derived via
 * {@code BlockSizer.blockSizeFor(sampleRate)} when the caller does not set
 * it explicitly.
 *
 * <p>Instances are immutable: every accessor returns the value that was
 * baked in at construction time, and there is no setter on {@code DtmfConfig}
 * itself. The nested {@code Advanced} builder is mutable, but exposes only
 * fluent setters that return {@code this} and a terminal {@code build()}
 * method.
 *
 * @since 2.0.0
 */
public final class DtmfConfig {

    /** Supported rates for the standard factory path (Requirement 3.2). */
    private static final Set<Integer> SUPPORTED_SAMPLE_RATES =
            Set.of(8000, 16000, 44100, 48000);

    /** Lower bound of the advanced sample-rate domain (Requirement 3.4). */
    private static final int ADVANCED_MIN_SAMPLE_RATE = 4000;

    /** Upper bound of the advanced sample-rate domain (Requirement 3.4). */
    private static final int ADVANCED_MAX_SAMPLE_RATE = 192_000;

    /** Minimum tone duration in milliseconds (Requirement 8.8). */
    private static final long MIN_TONE_DURATION_MS = 10L;

    // --- Six common knobs (Requirement 8.1) ---

    private final int sampleRate;
    private final int analysisBlockSize;
    private final Duration minimumToneDuration;
    private final Duration minimumGapDuration;
    private final double detectionThreshold;
    private final ChannelMode channelMode;

    // --- Four advanced knobs (Requirement 8.2) ---

    private final WindowFunction windowFunction;
    private final double forwardTwistDb;
    private final double reverseTwistDb;
    private final int confirmationFrames;

    /**
     * Package-private canonical constructor. Validates every field and
     * assigns.
     *
     * <p>Public callers reach this constructor exclusively through the four
     * standard factories or {@code Advanced.build()} — both of which layer
     * their own additional constraints (sample-rate domain) on top.
     *
     * @throws NullPointerException     if any {@code Duration}, enum, or other
     *                                  reference field is {@code null}
     * @throws IllegalArgumentException if any numeric field is outside its
     *                                  documented domain
     */
    DtmfConfig(
            int sampleRate,
            int analysisBlockSize,
            Duration minimumToneDuration,
            Duration minimumGapDuration,
            double detectionThreshold,
            ChannelMode channelMode,
            WindowFunction windowFunction,
            double forwardTwistDb,
            double reverseTwistDb,
            int confirmationFrames) {

        Objects.requireNonNull(minimumToneDuration, "minimumToneDuration");
        Objects.requireNonNull(minimumGapDuration, "minimumGapDuration");
        Objects.requireNonNull(channelMode, "channelMode");
        Objects.requireNonNull(windowFunction, "windowFunction");

        if (sampleRate <= 0) {
            throw new IllegalArgumentException(
                    "sampleRate must be > 0, was " + sampleRate);
        }
        if (analysisBlockSize <= 0) {
            throw new IllegalArgumentException(
                    "analysisBlockSize must be > 0, was " + analysisBlockSize);
        }
        if (minimumToneDuration.toMillis() < MIN_TONE_DURATION_MS) {
            throw new IllegalArgumentException(
                    "minimumToneDuration must be >= " + MIN_TONE_DURATION_MS
                            + " ms, was " + minimumToneDuration);
        }
        if (minimumGapDuration.isNegative()) {
            throw new IllegalArgumentException(
                    "minimumGapDuration must be >= 0, was " + minimumGapDuration);
        }
        if (Double.isNaN(detectionThreshold)
                || detectionThreshold < 0.0 || detectionThreshold > 1.0) {
            throw new IllegalArgumentException(
                    "detectionThreshold must be in [0, 1], was " + detectionThreshold);
        }
        if (!Double.isFinite(forwardTwistDb)) {
            throw new IllegalArgumentException(
                    "forwardTwistDb must be finite, was " + forwardTwistDb);
        }
        if (!Double.isFinite(reverseTwistDb)) {
            throw new IllegalArgumentException(
                    "reverseTwistDb must be finite, was " + reverseTwistDb);
        }
        if (!(forwardTwistDb > reverseTwistDb)) {
            throw new IllegalArgumentException(
                    "forwardTwistDb must be > reverseTwistDb, was forwardTwistDb="
                            + forwardTwistDb + ", reverseTwistDb=" + reverseTwistDb);
        }
        if (confirmationFrames < 1) {
            throw new IllegalArgumentException(
                    "confirmationFrames must be >= 1, was " + confirmationFrames);
        }

        this.sampleRate = sampleRate;
        this.analysisBlockSize = analysisBlockSize;
        this.minimumToneDuration = minimumToneDuration;
        this.minimumGapDuration = minimumGapDuration;
        this.detectionThreshold = detectionThreshold;
        this.channelMode = channelMode;
        this.windowFunction = windowFunction;
        this.forwardTwistDb = forwardTwistDb;
        this.reverseTwistDb = reverseTwistDb;
        this.confirmationFrames = confirmationFrames;
    }

    /**
     * Validate that {@code sampleRate} is one of the Supported_Sample_Rate
     * values required by the standard factory path (Requirement 3.3).
     *
     * <p>Exposed as a package-private helper so property tests in the same
     * package ({@code com.tino1b2be.dtmf}) can exercise the rejection logic
     * directly — Property 20 in {@code design.md}. The advanced builder
     * does <strong>not</strong> call this method; it uses its own, looser
     * {@code [4000, 192000]} range check.
     *
     * @param sampleRate candidate sample rate in Hz
     * @throws IllegalArgumentException if {@code sampleRate} is not in
     *                                  {@code {8000, 16000, 44100, 48000}}
     */
    static void validateStandardFactorySampleRate(int sampleRate) {
        if (!SUPPORTED_SAMPLE_RATES.contains(sampleRate)) {
            throw new IllegalArgumentException(
                    "sampleRate must be one of " + SUPPORTED_SAMPLE_RATES
                            + " for standard factories; got " + sampleRate
                            + ". Use DtmfConfig.advanced() for sample rates in "
                            + "[" + ADVANCED_MIN_SAMPLE_RATE + ", "
                            + ADVANCED_MAX_SAMPLE_RATE + "].");
        }
    }

    // --- Accessors ---

    /** {@return the sample rate in Hz}. */
    public int sampleRate() { return sampleRate; }

    /** {@return the analysis block size in samples}. */
    public int analysisBlockSize() { return analysisBlockSize; }

    /** {@return the minimum tone duration}. */
    public Duration minimumToneDuration() { return minimumToneDuration; }

    /** {@return the minimum inter-tone gap duration}. */
    public Duration minimumGapDuration() { return minimumGapDuration; }

    /** {@return the detection-confidence threshold in [0.0, 1.0]}. */
    public double detectionThreshold() { return detectionThreshold; }

    /** {@return the channel mode}. */
    public ChannelMode channelMode() { return channelMode; }

    /** {@return the window function applied to each analysis block}. */
    public WindowFunction windowFunction() { return windowFunction; }

    /** {@return the forward-twist tolerance in dB}. */
    public double forwardTwistDb() { return forwardTwistDb; }

    /** {@return the reverse-twist tolerance in dB}. */
    public double reverseTwistDb() { return reverseTwistDb; }

    /** {@return the number of consecutive analysis blocks required to confirm a tone}. */
    public int confirmationFrames() { return confirmationFrames; }

    // --- Static factories (Requirement 8.3–8.6) ---

    /**
     * {@return a configuration suitable for 8&nbsp;kHz mono telephony audio
     *         with Standard_Twist tolerances}.
     *
     * <p>Delegates to {@link #forTelephony()}.
     */
    public static DtmfConfig defaults() {
        return forTelephony();
    }

    /**
     * {@return a configuration tuned for ITU-T Q.24 telephony audio}.
     *
     * <p>Values:
     * <ul>
     *   <li>sample rate: 8000&nbsp;Hz</li>
     *   <li>analysis block size: auto (160 samples at 8&nbsp;kHz, 50&nbsp;Hz bin)</li>
     *   <li>minimum tone duration: 40&nbsp;ms</li>
     *   <li>minimum gap duration: 40&nbsp;ms</li>
     *   <li>detection threshold: 0.25</li>
     *   <li>channel mode: {@link ChannelMode#MONO MONO}</li>
     *   <li>window: {@link WindowFunction#RECTANGULAR RECTANGULAR}</li>
     *   <li>forward twist: +4&nbsp;dB (Standard_Twist)</li>
     *   <li>reverse twist: &minus;8&nbsp;dB (Standard_Twist)</li>
     *   <li>confirmation frames: 2</li>
     * </ul>
     */
    public static DtmfConfig forTelephony() {
        int sampleRate = 8000;
        validateStandardFactorySampleRate(sampleRate);
        return new DtmfConfig(
                sampleRate,
                BlockSizer.blockSizeFor(sampleRate),
                Duration.ofMillis(40),
                Duration.ofMillis(40),
                0.25,
                ChannelMode.MONO,
                WindowFunction.RECTANGULAR,
                4.0,
                -8.0,
                2);
    }

    /**
     * {@return a configuration tuned for VoIP audio}.
     *
     * <p>Identical to {@link #forTelephony()} except for
     * {@code confirmationFrames = 3}, chosen to absorb packet-loss
     * concealment artifacts that briefly disrupt the active tone.
     */
    public static DtmfConfig forVoip() {
        int sampleRate = 8000;
        validateStandardFactorySampleRate(sampleRate);
        return new DtmfConfig(
                sampleRate,
                BlockSizer.blockSizeFor(sampleRate),
                Duration.ofMillis(40),
                Duration.ofMillis(40),
                0.25,
                ChannelMode.MONO,
                WindowFunction.RECTANGULAR,
                4.0,
                -8.0,
                3);
    }

    /**
     * {@return a configuration tuned for noisy audio}.
     *
     * <p>Differs from {@link #forTelephony()} as follows: minimum tone
     * duration is 50&nbsp;ms, detection threshold is 0.35, and
     * {@code confirmationFrames = 4}. Other knobs match {@code forTelephony}.
     */
    public static DtmfConfig forNoisyAudio() {
        int sampleRate = 8000;
        validateStandardFactorySampleRate(sampleRate);
        return new DtmfConfig(
                sampleRate,
                BlockSizer.blockSizeFor(sampleRate),
                Duration.ofMillis(50),
                Duration.ofMillis(40),
                0.35,
                ChannelMode.MONO,
                WindowFunction.RECTANGULAR,
                4.0,
                -8.0,
                4);
    }

    /**
     * {@return a new {@link Advanced} builder seeded with the values from
     *         {@link #forTelephony()}}.
     *
     * <p>The returned builder accepts any integer sample rate in
     * {@code [4000, 192000]} Hz (Requirement 3.4) and exposes every one of
     * the ten knobs for override.
     */
    public static Advanced advanced() {
        return new Advanced();
    }

    /**
     * Fluent builder for {@link DtmfConfig}, exposing every knob and the
     * wider {@code [4000, 192000]} sample-rate domain.
     *
     * <p>Every setter validates its argument in the same way the canonical
     * constructor does, so misuse surfaces at the setter call site rather
     * than at {@link #build()}. The sample-rate range check and the twist
     * relationship check are enforced in {@code build()}.
     *
     * <p>Setters return {@code this} for chaining. Instances are not
     * thread-safe; build one per caller.
     */
    public static final class Advanced {

        // Seed values mirror forTelephony().
        private int sampleRate = 8000;
        private Integer analysisBlockSize = null; // null => auto-derive at build()
        private Duration minimumToneDuration = Duration.ofMillis(40);
        private Duration minimumGapDuration = Duration.ofMillis(40);
        private double detectionThreshold = 0.25;
        private ChannelMode channelMode = ChannelMode.MONO;
        private WindowFunction windowFunction = WindowFunction.RECTANGULAR;
        private double forwardTwistDb = 4.0;
        private double reverseTwistDb = -8.0;
        private int confirmationFrames = 2;

        private Advanced() { }

        /**
         * Set the sample rate.
         *
         * @param hz integer sample rate in Hz; must be in {@code [4000, 192000]}
         * @return {@code this}
         * @throws IllegalArgumentException if {@code hz} is outside
         *                                  {@code [4000, 192000]}
         */
        public Advanced sampleRate(int hz) {
            if (hz < ADVANCED_MIN_SAMPLE_RATE || hz > ADVANCED_MAX_SAMPLE_RATE) {
                throw new IllegalArgumentException(
                        "sampleRate must be in [" + ADVANCED_MIN_SAMPLE_RATE
                                + ", " + ADVANCED_MAX_SAMPLE_RATE + "], was " + hz);
            }
            this.sampleRate = hz;
            return this;
        }

        /**
         * Set the analysis block size explicitly. If never called, the block
         * size is derived at {@link #build()} time from
         * {@code BlockSizer.blockSizeFor(sampleRate)}.
         *
         * @param samples block size in samples; must be {@code > 0}
         * @return {@code this}
         * @throws IllegalArgumentException if {@code samples <= 0}
         */
        public Advanced analysisBlockSize(int samples) {
            if (samples <= 0) {
                throw new IllegalArgumentException(
                        "analysisBlockSize must be > 0, was " + samples);
            }
            this.analysisBlockSize = samples;
            return this;
        }

        /**
         * Set the minimum tone duration. Must be &ge; 10&nbsp;ms
         * (Requirement 8.8).
         *
         * @param d minimum tone duration; non-null
         * @return {@code this}
         * @throws NullPointerException     if {@code d} is {@code null}
         * @throws IllegalArgumentException if {@code d < 10 ms}
         */
        public Advanced minimumToneDuration(Duration d) {
            Objects.requireNonNull(d, "minimumToneDuration");
            if (d.toMillis() < MIN_TONE_DURATION_MS) {
                throw new IllegalArgumentException(
                        "minimumToneDuration must be >= " + MIN_TONE_DURATION_MS
                                + " ms, was " + d);
            }
            this.minimumToneDuration = d;
            return this;
        }

        /**
         * Set the minimum inter-tone gap duration. Must be non-negative.
         *
         * @param d minimum gap duration; non-null
         * @return {@code this}
         * @throws NullPointerException     if {@code d} is {@code null}
         * @throws IllegalArgumentException if {@code d} is negative
         */
        public Advanced minimumGapDuration(Duration d) {
            Objects.requireNonNull(d, "minimumGapDuration");
            if (d.isNegative()) {
                throw new IllegalArgumentException(
                        "minimumGapDuration must be >= 0, was " + d);
            }
            this.minimumGapDuration = d;
            return this;
        }

        /**
         * Set the detection-confidence threshold. Must be in
         * {@code [0.0, 1.0]}.
         *
         * @param t detection threshold
         * @return {@code this}
         * @throws IllegalArgumentException if {@code t} is {@code NaN} or
         *                                  outside {@code [0, 1]}
         */
        public Advanced detectionThreshold(double t) {
            if (Double.isNaN(t) || t < 0.0 || t > 1.0) {
                throw new IllegalArgumentException(
                        "detectionThreshold must be in [0, 1], was " + t);
            }
            this.detectionThreshold = t;
            return this;
        }

        /**
         * Set the channel mode.
         *
         * @param m channel mode; non-null
         * @return {@code this}
         * @throws NullPointerException if {@code m} is {@code null}
         */
        public Advanced channelMode(ChannelMode m) {
            this.channelMode = Objects.requireNonNull(m, "channelMode");
            return this;
        }

        /**
         * Set the window function.
         *
         * @param w window function; non-null
         * @return {@code this}
         * @throws NullPointerException if {@code w} is {@code null}
         */
        public Advanced windowFunction(WindowFunction w) {
            this.windowFunction = Objects.requireNonNull(w, "windowFunction");
            return this;
        }

        /**
         * Set the forward-twist tolerance in dB. Must be finite and strictly
         * greater than the reverse-twist value at {@code build()} time.
         *
         * @param db forward-twist tolerance in dB
         * @return {@code this}
         * @throws IllegalArgumentException if {@code db} is not finite
         */
        public Advanced forwardTwistDb(double db) {
            if (!Double.isFinite(db)) {
                throw new IllegalArgumentException(
                        "forwardTwistDb must be finite, was " + db);
            }
            this.forwardTwistDb = db;
            return this;
        }

        /**
         * Set the reverse-twist tolerance in dB. Must be finite and strictly
         * less than the forward-twist value at {@code build()} time.
         *
         * @param db reverse-twist tolerance in dB
         * @return {@code this}
         * @throws IllegalArgumentException if {@code db} is not finite
         */
        public Advanced reverseTwistDb(double db) {
            if (!Double.isFinite(db)) {
                throw new IllegalArgumentException(
                        "reverseTwistDb must be finite, was " + db);
            }
            this.reverseTwistDb = db;
            return this;
        }

        /**
         * Set the number of consecutive analysis blocks required to confirm
         * a tone. Must be {@code >= 1}.
         *
         * @param frames confirmation-frame count
         * @return {@code this}
         * @throws IllegalArgumentException if {@code frames < 1}
         */
        public Advanced confirmationFrames(int frames) {
            if (frames < 1) {
                throw new IllegalArgumentException(
                        "confirmationFrames must be >= 1, was " + frames);
            }
            this.confirmationFrames = frames;
            return this;
        }

        /**
         * Materialize an immutable {@link DtmfConfig} from the values set on
         * this builder. If {@link #analysisBlockSize(int)} has not been
         * called, the block size is derived via
         * {@code BlockSizer.blockSizeFor(sampleRate)} so the effective bin
         * width lands in {@code [40, 60]} Hz.
         *
         * @return the new {@code DtmfConfig}
         * @throws IllegalArgumentException if any inter-field constraint is
         *                                  violated (e.g. forward &le; reverse)
         */
        public DtmfConfig build() {
            int blockSize = (analysisBlockSize != null)
                    ? analysisBlockSize
                    : BlockSizer.blockSizeFor(sampleRate);
            return new DtmfConfig(
                    sampleRate,
                    blockSize,
                    minimumToneDuration,
                    minimumGapDuration,
                    detectionThreshold,
                    channelMode,
                    windowFunction,
                    forwardTwistDb,
                    reverseTwistDb,
                    confirmationFrames);
        }
    }
}
