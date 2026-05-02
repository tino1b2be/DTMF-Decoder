package com.tino1b2be.dtmf.internal;

/**
 * Analysis-block sizing logic for the DTMF detection pipeline.
 *
 * <p>The detector picks a block length {@code N} such that the effective
 * Goertzel bin width {@code sampleRate / N} lands in the closed range
 * {@code [40, 60]} Hz, with a target of 50&nbsp;Hz in the middle of the
 * band. That range is wide enough that every one of the Supported_Sample_Rate
 * values (8&nbsp;kHz, 16&nbsp;kHz, 44.1&nbsp;kHz, 48&nbsp;kHz) lands on an
 * integer {@code N} producing exactly 50&nbsp;Hz bin width (Requirement 3.5),
 * and comfortably satisfies Requirement 3.4 for any integer sample rate in
 * {@code [4000, 192000]} Hz.
 *
 * <p>The algorithm is a rounded divide plus a clamp: start from
 * {@code N = round(sampleRate / 50)}, then nudge {@code N} up or down until
 * {@code sampleRate / N} lands back inside {@code [40, 60]}. At any positive
 * integer sample rate in the supported range this terminates in at most two
 * steps because the {@code 1 / N} spacing between candidate bin widths is
 * strictly narrower than the 20&nbsp;Hz tolerance band.
 *
 * <p>Although the type is {@code public} so {@code DtmfConfig} in the sibling
 * package {@code com.tino1b2be.dtmf} can call it, the convention is that
 * {@code com.tino1b2be.dtmf.internal.*} is not part of the published API:
 * callers go through {@code DtmfConfig.Advanced} rather than constructing
 * this type directly. There is no constructor to call; every entry point is
 * a {@code static} method.
 */
public final class BlockSizer {

    /** Target bin width at the centre of the band. */
    private static final double TARGET_BIN_HZ = 50.0;

    /** Minimum acceptable bin width (Requirement 3.5 lower bound). */
    private static final double MIN_BIN_HZ = 40.0;

    /** Maximum acceptable bin width (Requirement 3.5 upper bound). */
    private static final double MAX_BIN_HZ = 60.0;

    private BlockSizer() { }

    /**
     * Compute the analysis block size for the given sample rate so that the
     * effective Goertzel bin width lies in {@code [40, 60]} Hz.
     *
     * @param sampleRate sample rate in Hz; must be {@code > 0}
     * @return a positive {@code int N} such that {@code sampleRate / N}
     *         is in {@code [40.0, 60.0]}
     * @throws IllegalArgumentException if {@code sampleRate <= 0}
     */
    public static int blockSizeFor(int sampleRate) {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException(
                    "sampleRate must be > 0, was " + sampleRate);
        }

        // Start at the rounded target. round(-0.5)==0 in Java, but sampleRate > 0
        // so this quotient is always positive; still, guard against N < 1 in
        // case of pathological inputs at the very low end of the sample-rate
        // domain (sampleRate < 25 would round to 0, but the public config layer
        // enforces sampleRate >= 4000 — we still defend here so the internal
        // helper is self-contained).
        int n = (int) Math.round(sampleRate / TARGET_BIN_HZ);
        if (n < 1) {
            n = 1;
        }

        // Nudge up while bin width exceeds the upper bound.
        while ((double) sampleRate / n > MAX_BIN_HZ) {
            n++;
        }
        // Nudge down while bin width is below the lower bound (but never
        // below 1).
        while (n > 1 && (double) sampleRate / n < MIN_BIN_HZ) {
            n--;
        }
        return n;
    }
}
