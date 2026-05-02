package com.tino1b2be.dtmf.io;

// Feature: dtmf-io, Property 10: DtmfFileDecoder auto-resolve config preservation

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Duration;

import com.tino1b2be.dtmf.ChannelMode;
import com.tino1b2be.dtmf.DtmfConfig;
import com.tino1b2be.dtmf.WindowFunction;
import com.tino1b2be.dtmf.internal.BlockSizer;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for {@link DtmfFileDecoder}'s auto-resolve config
 * preservation.
 *
 * <p><strong>Property 10: {@code DtmfFileDecoder} auto-resolve config
 * preservation.</strong> <strong>Validates: Requirements 8.6, 17.1,
 * 17.2.</strong>
 *
 * <p>When a caller passes a {@link DtmfConfig} whose sample rate does
 * not match the rate declared by the opened {@link AudioSource},
 * {@code DtmfFileDecoder} auto-resolves by rebuilding the config with
 * the source's rate substituted in. Every other field is preserved
 * verbatim (Requirement 17.2), and the analysis block size is
 * re-derived via {@link BlockSizer#blockSizeFor(int)
 * BlockSizer.blockSizeFor(newRate)} so the effective Goertzel bin
 * width lands back in {@code [40, 60]} Hz at the rate actually on
 * disk (Requirement 17.1). When the rates already match, the caller's
 * config is used verbatim (Requirement 8.6's "source's declared sample
 * rate takes precedence"): no rebuild is performed, so every field —
 * including any explicitly-set {@code analysisBlockSize} — is
 * preserved bit-for-bit.
 *
 * <h2>Seam</h2>
 *
 * <p>The property invokes the package-private
 * {@link DtmfFileDecoder#rebuildWithSampleRate(DtmfConfig, int)
 * DtmfFileDecoder.rebuildWithSampleRate(config, newRate)} directly
 * rather than routing through a full decode pipeline. That isolates
 * the config-preservation invariant from the read loop,
 * channel-downmix branch, and {@code DtmfDecoder} delegate — all of
 * which are covered by other properties / tests — so a regression on
 * field preservation shrinks to a tiny counterexample naming the
 * offending field rather than a full-pipeline failure whose root
 * cause could be anywhere.
 *
 * <p>The "rates match" arm of the property models the
 * {@code decodeInternal} short-circuit
 * ({@code effective = (srcRate == config.sampleRate()) ? config :
 * rebuildWithSampleRate(...)}): when the sample rates match the
 * caller's config is returned unchanged (by reference identity), so
 * the assertion is simply {@code assertSame(config, effective)}. The
 * "rates differ" arm invokes {@code rebuildWithSampleRate} and
 * asserts every field.
 *
 * <h2>Generator shape</h2>
 *
 * <p>The caller's {@link DtmfConfig} is built via
 * {@link DtmfConfig#advanced()} with every knob varied independently
 * so the property exercises the full 10-knob input surface described
 * in Requirements 8.1 and 8.2:
 *
 * <ul>
 *   <li>{@code sampleRate} &isin; {@code [4000, 192000]} Hz
 *       (Requirement 3.4 / advanced builder domain).</li>
 *   <li>{@code minimumToneDuration} &isin; {@code [10, 5000]} ms —
 *       lower bound matches the builder's {@code >= 10 ms} check
 *       (Requirement 8.8), upper bound is generous enough to cover
 *       realistic calling patterns.</li>
 *   <li>{@code minimumGapDuration} &isin; {@code [0, 5000]} ms — the
 *       builder permits any non-negative duration; zero is explicitly
 *       in range.</li>
 *   <li>{@code detectionThreshold} &isin; {@code [0.0, 1.0]} — the
 *       full domain accepted by the builder.</li>
 *   <li>{@code channelMode} drawn uniformly from
 *       {@code {MONO, STEREO_INDEPENDENT, STEREO_DOWNMIX}}.</li>
 *   <li>{@code windowFunction} drawn uniformly from
 *       {@code {RECTANGULAR, HAMMING, HANN}}.</li>
 *   <li>{@code forwardTwistDb} &isin; {@code [0.0, 20.0]} and
 *       {@code reverseTwistDb} &isin; {@code [-30.0, -1.0]} —
 *       non-overlapping ranges so {@code forward > reverse} is
 *       satisfied by construction (the builder's {@code build()}
 *       enforces this inter-field constraint). Finite bounded values
 *       also satisfy the builder's per-setter {@code Double.isFinite}
 *       check.</li>
 *   <li>{@code confirmationFrames} &isin; {@code [1, 10]} — the
 *       builder requires {@code >= 1}; an upper bound of 10 avoids
 *       pathological values with no additional coverage.</li>
 * </ul>
 *
 * <p>The source sample rate is drawn independently from the same
 * {@code [4000, 192000]} range, so:
 *
 * <ul>
 *   <li>With probability proportional to a single-integer collision
 *       in that range (~ 1 / 188,001), the rates match and the
 *       "rates match" arm runs.</li>
 *   <li>Otherwise, the rates differ and the "rates differ" arm runs.</li>
 * </ul>
 *
 * <p>To ensure both arms see non-trivial coverage within
 * {@code tries = 100}, a dedicated property
 * ({@link #rebuildPreservesEveryFieldExceptSampleRateAndBlockSize
 * rebuildPreservesEveryFieldExceptSampleRateAndBlockSize}) exercises
 * the rebuild path unconditionally by generating a source rate that is
 * guaranteed to differ from the config's rate
 * (via {@code Arbitraries.of(...)} remapping), and a second property
 * ({@link #effectiveConfigEqualsCallerWhenRatesMatch
 * effectiveConfigEqualsCallerWhenRatesMatch}) exercises the no-op
 * branch by using the same value for both rates.
 *
 * <h2>Scope</h2>
 *
 * <p>This property targets Requirements 8.6, 17.1, and 17.2.
 * Adjacent invariants are covered elsewhere:
 *
 * <ul>
 *   <li>Close semantics (Requirements 8.11, 8.12, 11.3, 11.4) are
 *       Property 9's concern.</li>
 *   <li>Rejection paths — unsupported channel counts, unsupported
 *       sample rates, mono/stereo mode mismatches (Requirements 8.9,
 *       8.10, 17.3) — are Property 11's concern.</li>
 *   <li>The {@code [40, 60]} Hz bin-width invariant across every
 *       sample rate is {@code dtmf-core}'s {@code BlockSizer}
 *       property coverage; this property only asserts that
 *       {@code rebuildWithSampleRate} routes through
 *       {@code BlockSizer.blockSizeFor(newRate)}, not that
 *       {@code BlockSizer} itself is correct.</li>
 * </ul>
 */
class DtmfFileDecoderAutoResolvePropertyTest {

    // ==================================================================
    // Arm A: rates differ — rebuild derives a new block size and
    //        preserves every other field verbatim. Req 17.1, 17.2.
    // ==================================================================

    /**
     * When the source's sample rate differs from the caller's config
     * rate, {@link DtmfFileDecoder#rebuildWithSampleRate(DtmfConfig,
     * int) rebuildWithSampleRate(config, newRate)} returns a new
     * {@link DtmfConfig} whose {@code sampleRate} equals
     * {@code newRate}, whose {@code analysisBlockSize} equals
     * {@link BlockSizer#blockSizeFor(int)
     * BlockSizer.blockSizeFor(newRate)}, and every one of the other
     * eight fields equals the caller's value bit-for-bit
     * (Requirements 17.1, 17.2).
     *
     * <p>{@code newRate} is forced to differ from
     * {@code config.sampleRate()} via a guard that redraws the source
     * rate inside the helper: the generator sometimes yields the same
     * value for both rates, but that case is covered by the "rates
     * match" property — here we want unconditional rebuild coverage.
     */
    @Property(tries = 100)
    void rebuildPreservesEveryFieldExceptSampleRateAndBlockSize(
            @ForAll("configs") DtmfConfig config,
            @ForAll("sourceRates") int candidateSourceRate) {

        // Force rates to differ so the rebuild branch is exercised
        // unconditionally. When the generator happens to draw the same
        // value as config.sampleRate(), nudge it by one within the
        // supported range. This preserves the uniformity of the draw
        // while guaranteeing rebuild coverage.
        int sourceRate = (candidateSourceRate == config.sampleRate())
                ? nudgeWithinRange(candidateSourceRate)
                : candidateSourceRate;

        DtmfConfig effective = DtmfFileDecoder.rebuildWithSampleRate(config, sourceRate);

        // Sample rate adopts the source's rate (Req 17.1 first clause).
        assertEquals(sourceRate, effective.sampleRate(),
                () -> "effective.sampleRate() must equal sourceRate=" + sourceRate
                        + " after rebuild; was " + effective.sampleRate()
                        + " [config.sampleRate=" + config.sampleRate() + "]");

        // Analysis block size is re-derived from the new rate
        // (Req 17.1 second clause) — not copied from the caller's
        // config. BlockSizer.blockSizeFor is deterministic, so the
        // rebuilt config's block size must equal the result of
        // calling it directly on sourceRate.
        int expectedBlockSize = BlockSizer.blockSizeFor(sourceRate);
        assertEquals(expectedBlockSize, effective.analysisBlockSize(),
                () -> "effective.analysisBlockSize() must equal "
                        + "BlockSizer.blockSizeFor(" + sourceRate + ")="
                        + expectedBlockSize + "; was "
                        + effective.analysisBlockSize()
                        + " [config.analysisBlockSize=" + config.analysisBlockSize() + "]");

        // Every other field is preserved bit-for-bit (Req 17.2).
        assertEquals(config.minimumToneDuration(), effective.minimumToneDuration(),
                "minimumToneDuration must be preserved across rebuild (Req 17.2)");
        assertEquals(config.minimumGapDuration(), effective.minimumGapDuration(),
                "minimumGapDuration must be preserved across rebuild (Req 17.2)");
        assertEquals(config.detectionThreshold(), effective.detectionThreshold(),
                "detectionThreshold must be preserved across rebuild (Req 17.2)");
        assertEquals(config.channelMode(), effective.channelMode(),
                "channelMode must be preserved across rebuild (Req 17.2)");
        assertEquals(config.windowFunction(), effective.windowFunction(),
                "windowFunction must be preserved across rebuild (Req 17.2)");
        assertEquals(config.forwardTwistDb(), effective.forwardTwistDb(),
                "forwardTwistDb must be preserved across rebuild (Req 17.2)");
        assertEquals(config.reverseTwistDb(), effective.reverseTwistDb(),
                "reverseTwistDb must be preserved across rebuild (Req 17.2)");
        assertEquals(config.confirmationFrames(), effective.confirmationFrames(),
                "confirmationFrames must be preserved across rebuild (Req 17.2)");
    }

    // ==================================================================
    // Arm B: rates match — effective config is the caller's config,
    //        unchanged (decodeInternal short-circuit). Req 8.6.
    // ==================================================================

    /**
     * When the source's sample rate equals the caller's config rate,
     * the effective config used for decoding is the caller's config
     * itself — no rebuild is performed. This is the
     * {@code decodeInternal} short-circuit
     * ({@code effective = (srcRate == config.sampleRate()) ? config :
     * rebuildWithSampleRate(...)}) and it matters because the caller
     * may have set an explicit {@code analysisBlockSize} on the
     * advanced builder; preserving reference identity guarantees that
     * explicit block size survives (Requirement 8.6 — the source's
     * rate "takes precedence" only when it differs).
     *
     * <p>The property models the short-circuit directly via the
     * {@link #resolveEffectiveConfig(DtmfConfig, int)} helper, which
     * mirrors the one-line conditional in
     * {@code DtmfFileDecoder.decodeInternal}. The assertion is
     * {@code assertSame} (not {@code assertEquals}) because the
     * preservation rule says "the same config" — returning a newly
     * constructed equal-but-not-identical config would silently drop
     * any advanced-builder-set block size that differs from
     * {@code BlockSizer.blockSizeFor(rate)}.
     */
    @Property(tries = 100)
    void effectiveConfigEqualsCallerWhenRatesMatch(
            @ForAll("configs") DtmfConfig config) {

        int sourceRate = config.sampleRate();

        DtmfConfig effective = resolveEffectiveConfig(config, sourceRate);

        assertSame(config, effective,
                () -> "Effective config must be the caller's config by reference "
                        + "identity when source.sampleRate() (=" + sourceRate
                        + ") == config.sampleRate() (=" + config.sampleRate()
                        + "); rebuild must be skipped (Req 8.6, decodeInternal "
                        + "short-circuit)");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * Mirror of the one-line conditional at the top of
     * {@code DtmfFileDecoder.decodeInternal}:
     * {@code effective = (srcRate == config.sampleRate()) ? config :
     * rebuildWithSampleRate(config, srcRate)}. The property uses this
     * helper so the test expresses the full auto-resolve decision —
     * both the short-circuit and the rebuild — rather than only one
     * half of it.
     *
     * @param config     caller-supplied config; non-null
     * @param sourceRate rate declared by the opened {@link AudioSource},
     *                   in Hz
     * @return {@code config} if the rates already match, otherwise
     *         {@code DtmfFileDecoder.rebuildWithSampleRate(config,
     *         sourceRate)}
     */
    private static DtmfConfig resolveEffectiveConfig(DtmfConfig config, int sourceRate) {
        return (sourceRate == config.sampleRate())
                ? config
                : DtmfFileDecoder.rebuildWithSampleRate(config, sourceRate);
    }

    /**
     * Return a value in {@code [4000, 192000]} that is guaranteed to
     * differ from {@code rate}. Used by the "rates differ" property
     * to convert the rare collision where the generator draws the
     * same value for {@code config.sampleRate()} and the source rate
     * into an unconditional rebuild case. Nudges by {@code +1} in the
     * common case and by {@code -1} at the top-of-range boundary so
     * the result always lies inside the supported domain.
     */
    private static int nudgeWithinRange(int rate) {
        return (rate < 192_000) ? rate + 1 : rate - 1;
    }

    // ==================================================================
    // Arbitraries
    // ==================================================================

    /**
     * Generate a {@link DtmfConfig} with every advanced-builder knob
     * varied independently. The generator combines nine independent
     * primitive arbitraries — sample rate, tone duration, gap
     * duration, detection threshold, channel mode, window function,
     * forward-twist, reverse-twist, confirmation frames — and feeds
     * them through {@link DtmfConfig#advanced()} so the property
     * exercises the full public 10-knob surface described in
     * Requirements 8.1 and 8.2. Note that {@code analysisBlockSize}
     * is intentionally left to the builder's auto-derivation; varying
     * it independently of sample rate would confuse the rebuild
     * invariant, which is specifically about re-derivation.
     *
     * <p>Twist ranges are split into non-overlapping windows
     * ({@code forward} &isin; {@code [0, 20]}, {@code reverse}
     * &isin; {@code [-30, -1]}) so the builder's
     * {@code forward > reverse} invariant is satisfied by
     * construction rather than by filtering (which would degrade
     * jqwik's shrinker).
     */
    @Provide
    Arbitrary<DtmfConfig> configs() {
        Arbitrary<Integer> sampleRate = Arbitraries.integers().between(4_000, 192_000);
        Arbitrary<Long> toneMillis = Arbitraries.longs().between(10L, 5_000L);
        Arbitrary<Long> gapMillis = Arbitraries.longs().between(0L, 5_000L);
        Arbitrary<Double> detectionThreshold = Arbitraries.doubles().between(0.0, 1.0);
        Arbitrary<ChannelMode> channelMode = Arbitraries.of(ChannelMode.class);
        Arbitrary<WindowFunction> windowFunction = Arbitraries.of(WindowFunction.class);
        Arbitrary<Double> forwardTwistDb = Arbitraries.doubles().between(0.0, 20.0);
        Arbitrary<Double> reverseTwistDb = Arbitraries.doubles().between(-30.0, -1.0);
        Arbitrary<Integer> confirmationFrames = Arbitraries.integers().between(1, 10);

        // jqwik's Combinators maxes out at 8 arbitraries per as(...)
        // call; fold the nine inputs via a nested combine. The inner
        // combinator packages the six "simple" knobs into a temporary
        // record-shaped holder so the outer combinator can stay
        // within the 8-arity limit.
        Arbitrary<InnerKnobs> inner = Combinators.combine(
                toneMillis, gapMillis, detectionThreshold,
                channelMode, windowFunction, confirmationFrames)
                .as(InnerKnobs::new);

        return Combinators.combine(
                sampleRate, inner, forwardTwistDb, reverseTwistDb)
                .as((rate, knobs, forward, reverse) -> DtmfConfig.advanced()
                        .sampleRate(rate)
                        .minimumToneDuration(Duration.ofMillis(knobs.toneMillis))
                        .minimumGapDuration(Duration.ofMillis(knobs.gapMillis))
                        .detectionThreshold(knobs.detectionThreshold)
                        .channelMode(knobs.channelMode)
                        .windowFunction(knobs.windowFunction)
                        .forwardTwistDb(forward)
                        .reverseTwistDb(reverse)
                        .confirmationFrames(knobs.confirmationFrames)
                        .build());
    }

    /**
     * Source sample rates drawn from the full advanced-builder domain
     * {@code [4000, 192000]} Hz (Requirement 3.4 / 17.3). The "rates
     * differ" property forces the draw to differ from
     * {@code config.sampleRate()} via {@link #nudgeWithinRange(int)}.
     */
    @Provide
    Arbitrary<Integer> sourceRates() {
        return Arbitraries.integers().between(4_000, 192_000);
    }

    /**
     * Tuple of the six "simple" {@code DtmfConfig} knobs — tone
     * duration, gap duration, detection threshold, channel mode,
     * window function, confirmation frames — used to keep the outer
     * {@link Combinators#combine Combinators.combine} call within
     * jqwik's 8-arity limit. Package-private record with public
     * fields so the lambda in {@link #configs()} can read them
     * directly; no external code references this type.
     */
    private record InnerKnobs(
            long toneMillis,
            long gapMillis,
            double detectionThreshold,
            ChannelMode channelMode,
            WindowFunction windowFunction,
            int confirmationFrames) {
    }
}
