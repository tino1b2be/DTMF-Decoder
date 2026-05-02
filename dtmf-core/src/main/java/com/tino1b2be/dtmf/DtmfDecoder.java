package com.tino1b2be.dtmf;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.tino1b2be.dtmf.internal.SampleConverter;

/**
 * Batch DTMF decoder: turn a buffer of PCM samples into a list of detected
 * {@link DtmfTone} instances.
 *
 * <p>{@code DtmfDecoder} is the batch half of the public API. Every overload
 * is a thin wrapper around {@link DtmfDetector}:
 *
 * <ol>
 *   <li>null-check the inputs (Requirement 4.8, 17.1);</li>
 *   <li>normalise the sample format to {@code double} via
 *       {@link SampleConverter} (Requirements 4.5, 4.6, 4.7);</li>
 *   <li>instantiate a fresh {@code DtmfDetector}, wire an
 *       {@link ArrayList#add} callback, feed the normalised samples, and
 *       {@link DtmfDetector#flush() flush};</li>
 *   <li>return the list in non-decreasing {@code startSample} order
 *       (Requirement 5.2).</li>
 * </ol>
 *
 * <p>Running batch decoding through the same push pipeline is how chunk
 * invariance (Requirement 6.7) becomes structural rather than tested-after-
 * the-fact: {@code decode(B, cfg)} and any chunking of {@code B} fed into a
 * fresh detector produce the same tones by construction.
 *
 * <p>The class is final with a private constructor; all entry points are
 * static.
 *
 * @since 2.0.0
 */
public final class DtmfDecoder {

    private DtmfDecoder() { }

    /**
     * Decode a {@code double[]} buffer of normalised PCM samples in
     * {@code [-1.0, 1.0]}.
     *
     * @param samples PCM samples; non-null
     * @param config  detection configuration; non-null
     * @return the list of detected tones, in non-decreasing
     *         {@code startSample} order
     * @throws NullPointerException if either argument is {@code null}
     */
    public static List<DtmfTone> decode(double[] samples, DtmfConfig config) {
        Objects.requireNonNull(samples, "samples");
        Objects.requireNonNull(config, "config");
        return detectOn(samples, config);
    }

    /**
     * Decode a {@code short[]} buffer of signed PCM16 samples. Samples are
     * normalised to {@code double} via division by {@code 32768.0}
     * (Requirement 4.5).
     *
     * @param samples PCM16 samples; non-null
     * @param config  detection configuration; non-null
     * @return the list of detected tones, in non-decreasing
     *         {@code startSample} order
     * @throws NullPointerException if either argument is {@code null}
     */
    public static List<DtmfTone> decode(short[] samples, DtmfConfig config) {
        Objects.requireNonNull(samples, "samples");
        Objects.requireNonNull(config, "config");
        return detectOn(SampleConverter.fromShort(samples), config);
    }

    /**
     * Decode a {@code float[]} buffer of normalised samples in
     * {@code [-1.0, 1.0]}. Samples are widened to {@code double} without
     * scaling (Requirement 4.6).
     *
     * @param samples float samples; non-null
     * @param config  detection configuration; non-null
     * @return the list of detected tones, in non-decreasing
     *         {@code startSample} order
     * @throws NullPointerException if either argument is {@code null}
     */
    public static List<DtmfTone> decode(float[] samples, DtmfConfig config) {
        Objects.requireNonNull(samples, "samples");
        Objects.requireNonNull(config, "config");
        return detectOn(SampleConverter.fromFloat(samples), config);
    }

    /**
     * Decode an {@code int[]} buffer of signed PCM32 samples. Samples are
     * normalised via division by {@code 2^31} (Requirement 4.7).
     *
     * @param samples PCM32 samples; non-null
     * @param config  detection configuration; non-null
     * @return the list of detected tones, in non-decreasing
     *         {@code startSample} order
     * @throws NullPointerException if either argument is {@code null}
     */
    public static List<DtmfTone> decode(int[] samples, DtmfConfig config) {
        Objects.requireNonNull(samples, "samples");
        Objects.requireNonNull(config, "config");
        return detectOn(SampleConverter.fromInt(samples), config);
    }

    /**
     * Decode an {@code int[]} buffer where each entry carries a signed PCM24
     * value in its low 24 bits. Helper for callers supplying packed PCM24
     * (Requirement 4.4).
     *
     * @param samples PCM24-in-int samples; non-null
     * @param config  detection configuration; non-null
     * @return the list of detected tones, in non-decreasing
     *         {@code startSample} order
     * @throws NullPointerException if either argument is {@code null}
     */
    public static List<DtmfTone> decodePcm24(int[] samples, DtmfConfig config) {
        Objects.requireNonNull(samples, "samples");
        Objects.requireNonNull(config, "config");
        return detectOn(SampleConverter.fromPcm24(samples), config);
    }

    /**
     * Core decode: create a fresh {@link DtmfDetector}, wire a callback that
     * appends to an {@link ArrayList}, feed the samples once, flush, and
     * return the list. The detector is not reused across calls so that
     * cumulative sample indices always reset to {@code 0} (Requirement 6.8).
     */
    private static List<DtmfTone> detectOn(double[] samples, DtmfConfig config) {
        List<DtmfTone> collected = new ArrayList<>();
        DtmfDetector detector = new DtmfDetector(config);
        detector.onTone(collected::add);
        detector.process(samples);
        detector.flush();
        return collected;
    }
}
