package com.tino1b2be.dtmf;

/**
 * How an incoming sample array should be interpreted as one or more audio
 * channels for DTMF detection.
 *
 * <p>The three cases directly correspond to Requirement 13.1 and drive the
 * branch in {@code DtmfDetector} / {@code DtmfDecoder} that selects between a
 * single-pipeline and dual-pipeline analysis topology:
 *
 * <ul>
 *   <li>{@link #MONO} — treat the sample array as a single channel; emitted
 *       tones carry {@code channel = 0}.</li>
 *   <li>{@link #STEREO_INDEPENDENT} — treat the sample array as interleaved
 *       left/right PCM, decode each channel independently, and tag emissions
 *       with {@code channel = 0} (left, even indices) or {@code channel = 1}
 *       (right, odd indices).</li>
 *   <li>{@link #STEREO_DOWNMIX} — average adjacent left/right samples into a
 *       single mono stream before detection; emitted tones carry
 *       {@code channel = 0}.</li>
 * </ul>
 *
 * <p>Used as the {@code channelMode} value of {@code DtmfConfig}.
 *
 * @since 2.0.0
 */
public enum ChannelMode {

    /** Single-channel input; all emitted tones carry {@code channel = 0}. */
    MONO,

    /**
     * Interleaved stereo input decoded as two independent channels. Emitted
     * tones carry {@code channel = 0} for the left channel (even sample
     * indices) and {@code channel = 1} for the right channel (odd sample
     * indices).
     */
    STEREO_INDEPENDENT,

    /**
     * Interleaved stereo input averaged into a single mono stream before
     * detection. Emitted tones carry {@code channel = 0}.
     */
    STEREO_DOWNMIX
}
