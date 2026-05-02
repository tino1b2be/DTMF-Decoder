package com.tino1b2be.dtmf.io.internal;

import com.tino1b2be.dtmf.io.AudioSourceProvider;

import java.util.Comparator;
import java.util.Objects;

/**
 * Scoring bookkeeping for a single {@link AudioSourceProvider} during the
 * content-based dispatch performed by {@code AudioSources.open(...)}. Pairs
 * a provider with the SPI Priority Score it returned from
 * {@link AudioSourceProvider#canOpen} and the {@link
 * AudioSourceProvider#priority() priority()} value captured at scoring time
 * so the tie-break (Requirement 5.6) is decided from a single, consistent
 * snapshot.
 *
 * <p>Extracting this tuple keeps {@code AudioSources}'s scoring loop
 * readable: the facade collects one {@code ProviderScore} per provider it
 * consults, filters out the non-applicable ones (score {@code < 0}), and
 * picks the winner with
 * {@code java.util.Collections.max(eligible, BY_SCORE_THEN_PRIORITY)}. The
 * full list of {@code ProviderScore}s also feeds the diagnostic population
 * on {@code UnsupportedAudioFormatException} (Requirements 6.4, 6.5, 6.6)
 * when every provider returned {@code -1}.
 *
 * <p><strong>This class is not part of the published API.</strong> It
 * lives in {@code com.tino1b2be.dtmf.io.internal}, whose stability
 * contract (see the package Javadoc) explicitly allows breakage between
 * any two releases. It is {@code public} at the type level purely so
 * {@code AudioSources} &mdash; which lives in the parent package and
 * cannot otherwise see a package-private type here &mdash; can reach it;
 * external callers MUST NOT depend on it.
 *
 * @param provider the provider this score belongs to; never {@code null}
 * @param score    the SPI Priority Score returned by
 *                 {@link AudioSourceProvider#canOpen}: an integer in
 *                 {@code [0, 100]} when the provider is applicable, or
 *                 {@code -1} when it declined (including the case where
 *                 {@code canOpen} threw an {@link java.io.IOException}
 *                 that {@code AudioSources} caught and logged per
 *                 Requirement 5.9)
 * @param priority the value of {@link AudioSourceProvider#priority()} at
 *                 the time of scoring, used as the tie-breaker when two
 *                 providers return the same {@code score}
 * @since 2.0.0
 */
public record ProviderScore(AudioSourceProvider provider, int score, int priority) {

    /**
     * Compact constructor enforcing that {@code provider} is non-null.
     *
     * @throws NullPointerException if {@code provider} is {@code null}
     */
    public ProviderScore {
        Objects.requireNonNull(provider, "provider");
    }

    /**
     * Ordering used to pick the winning provider during dispatch
     * (Requirement 5.6). Orders ascending first by {@link #score()} and
     * then, on ties, ascending by {@link #priority()} &mdash; so the
     * greatest pair under this ordering is the provider that returned
     * the strictly greatest SPI Priority Score, tie-broken by the
     * greatest {@code priority()} value.
     *
     * <p>Typical use in {@code AudioSources}:
     * <pre>{@code
     * ProviderScore winner = Collections.max(eligible, ProviderScore.BY_SCORE_THEN_PRIORITY);
     * }</pre>
     *
     * <p>When two {@code ProviderScore}s are equal under both
     * {@code score} and {@code priority}, the comparator returns zero;
     * in that case {@code AudioSources} picks the earlier entry in
     * discovery order simply because {@code Collections.max} returns the
     * last maximal element while the scoring loop encounters the
     * providers in discovery order &mdash; but the whole system does
     * not depend on which way that coin lands, since two providers
     * genuinely indistinguishable by this ordering are, by construction,
     * equally valid choices.
     */
    public static final Comparator<ProviderScore> BY_SCORE_THEN_PRIORITY =
            Comparator.comparingInt(ProviderScore::score)
                    .thenComparingInt(ProviderScore::priority);
}
