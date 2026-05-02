package com.tino1b2be.dtmf.io;

// Feature: dtmf-io, Property 11: DtmfFileDecoder channel-count and sample-rate rejection

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import com.tino1b2be.dtmf.ChannelMode;
import com.tino1b2be.dtmf.DtmfConfig;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for {@link DtmfFileDecoder}'s channel-count and
 * sample-rate rejection paths.
 *
 * <p><strong>Property 11: {@code DtmfFileDecoder} channel-count and
 * sample-rate rejection.</strong> <strong>Validates: Requirements 8.9,
 * 8.10, 17.3.</strong>
 *
 * <p>{@code DtmfFileDecoder} runs three guards before it decodes a
 * single frame:
 *
 * <ol>
 *   <li><strong>Channel count &gt; 2 (Req 8.10).</strong> The DTMF
 *       decoder only knows how to interpret mono and stereo streams.
 *       Anything beyond two channels (quadraphonic, 5.1, ambisonic)
 *       is rejected up front with a diagnostic message naming the
 *       offending channel count so the caller can tell "I loaded a
 *       5.1 surround track by mistake" apart from "this file is
 *       corrupt."</li>
 *   <li><strong>Mono source paired with a stereo
 *       {@link ChannelMode} (Req 8.9).</strong> Asking
 *       {@code DtmfDecoder} to decode a mono buffer as two
 *       independent channels (or as an interleaved downmix) would
 *       split adjacent samples across imaginary channels and produce
 *       nonsense. The guard catches this configuration mismatch and
 *       names both the source (mono / channelCount 1) and the
 *       offending channel mode so the caller knows which knob to
 *       flip.</li>
 *   <li><strong>Sample rate outside {@code [4000, 192000]}
 *       (Req 17.3).</strong> The advanced builder enforces this
 *       range at {@code DtmfConfig} construction time; the decoder
 *       mirrors it on the source side so a WAV at 2 kHz or 384 kHz
 *       is rejected before the auto-resolve rebuild would throw from
 *       the builder. The diagnostic names both the offending rate
 *       and the valid range.</li>
 * </ol>
 *
 * <p>Each guard is exercised via a lightweight {@link AudioSource}
 * stub ({@link StubAudioSource}) whose {@code sampleRate()} and
 * {@code channelCount()} are caller-configurable. The stub is never
 * read from — every guard fires inside {@code decodeInternal} before
 * the read loop begins — so the stub's {@code read(...)} path is a
 * deliberate trap that throws {@link AssertionError} if reached.
 * That turns "the guard silently accepted an invalid input" into a
 * loud test failure rather than a vacuous pass.
 *
 * <h2>Why a stub rather than {@link RawPcmAudioSource}</h2>
 *
 * <p>{@code RawPcmAudioSource}'s constructor enforces its own
 * {@code channelCount &in; [1, 8]} and
 * {@code sampleRate &in; [1, 384000]} guards (Req 7.5, 7.6). Those
 * guards overlap with — but are strictly tighter than — the decoder's
 * guards on some axes (e.g., {@code sampleRate = 300000} is valid for
 * {@code RawPcmAudioSource} but invalid for the decoder; conversely,
 * {@code channelCount = 9} is invalid for both). Using a stub
 * decouples the property from {@code RawPcmAudioSource}'s constructor
 * domain so every generator value lands at the decoder's guard
 * surface, where the assertions belong.
 *
 * <h2>Scope</h2>
 *
 * <p>This property targets Requirements 8.9, 8.10, and 17.3. Adjacent
 * invariants are covered elsewhere:
 *
 * <ul>
 *   <li>Close semantics (Req 8.11, 8.12, 11.3, 11.4) are Property 9's
 *       concern — they are exercised independently via
 *       {@code CloseTrackingAudioSource}.</li>
 *   <li>Auto-resolve field preservation (Req 8.6, 17.1, 17.2) is
 *       Property 10's concern. That property exercises the rebuild
 *       branch; this one exercises the guards that fire <em>before</em>
 *       the rebuild branch even runs.</li>
 *   <li>Happy-path decoding (Req 8.7, 8.8 — stereo downmix,
 *       interleaved forward) is covered by {@code DtmfFileDecoderTest}'s
 *       unit tests.</li>
 * </ul>
 */
class DtmfFileDecoderRejectionPropertyTest {

    /**
     * Standard telephony config. Channel mode is {@link ChannelMode#MONO},
     * which makes this config compatible with mono sources; combining it
     * with a {@code channelCount > 2} source exercises the channel-count
     * guard without tripping the mono/stereo-mode mismatch guard first.
     */
    private static final DtmfConfig TELEPHONY = DtmfConfig.forTelephony();

    /**
     * A valid sample rate inside {@code [4000, 192000]} used as the
     * source rate in arms A and B so the sample-rate guard does not
     * fire before the guard actually under test.
     */
    private static final int VALID_SAMPLE_RATE = 8_000;

    // ==================================================================
    // Arm A: channelCount > 2 — Req 8.10
    // ==================================================================

    /**
     * For any {@code channelCount &gt; 2} paired with a valid sample
     * rate and a {@link ChannelMode#MONO} config,
     * {@link DtmfFileDecoder#decode(AudioSource, DtmfConfig)} throws
     * {@link UnsupportedAudioFormatException} whose message names the
     * offending channel count and states that only 1 and 2 channels
     * are supported (Req 8.10).
     *
     * <p>The generator draws {@code channelCount} from {@code [3, 8]}.
     * The upper bound matches {@code RawPcmAudioSource}'s
     * {@code MAX_CHANNEL_COUNT} (Req 7.6) so the chosen set is a
     * realistic file-format surface — 3 through 8 covers
     * quadraphonic, 5.1 with LFE, 7.1, and Atmos-style beds — while
     * still being a closed interval that jqwik can shrink cleanly.
     * Any {@code channelCount > 2} trips the guard; the specific
     * upper bound is tested purely for coverage breadth.
     */
    @Property(tries = 100)
    void rejectsSourceWithChannelCountGreaterThanTwo(
            @ForAll @IntRange(min = 3, max = 8) int channelCount) {

        StubAudioSource source = new StubAudioSource(VALID_SAMPLE_RATE, channelCount);

        UnsupportedAudioFormatException ex = assertThrows(
                UnsupportedAudioFormatException.class,
                () -> DtmfFileDecoder.decode(source, TELEPHONY),
                () -> "decode(AudioSource, cfg) must throw UnsupportedAudioFormatException "
                        + "for channelCount=" + channelCount
                        + " (only 1 and 2 are supported; Req 8.10)");

        String msg = ex.getMessage();
        assertNotNull(msg, "UAFE message must not be null");
        assertAll(
                () -> assertTrue(msg.contains(Integer.toString(channelCount)),
                        () -> "Message must name the offending channel count '"
                                + channelCount + "'; was: " + msg),
                () -> assertTrue(msg.contains("1") && msg.contains("2"),
                        () -> "Message must state that only 1 and 2 channels "
                                + "are supported (Req 8.10); was: " + msg));
    }

    // ==================================================================
    // Arm B: mono source + STEREO_* channel mode — Req 8.9
    // ==================================================================

    /**
     * For {@code channelCount == 1} paired with any stereo
     * {@link ChannelMode} ({@code STEREO_INDEPENDENT} or
     * {@code STEREO_DOWNMIX}),
     * {@link DtmfFileDecoder#decode(AudioSource, DtmfConfig)} throws
     * {@link UnsupportedAudioFormatException} whose message
     * identifies the mono source, the requested channel mode, and
     * points the caller at {@code ChannelMode.MONO} as the fix
     * (Req 8.9).
     *
     * <p>The channel mode is drawn from the set
     * {@code {STEREO_INDEPENDENT, STEREO_DOWNMIX}} — the two modes
     * that assume interleaved stereo input. {@code MONO} is excluded
     * from this arm because pairing it with a mono source is the
     * well-formed case that the decoder accepts.
     */
    @Property(tries = 100)
    void rejectsMonoSourceWithStereoChannelMode(
            @ForAll("stereoChannelModes") ChannelMode stereoMode) {

        StubAudioSource source = new StubAudioSource(VALID_SAMPLE_RATE, 1);
        DtmfConfig stereoCfg = DtmfConfig.advanced()
                .channelMode(stereoMode)
                .build();

        UnsupportedAudioFormatException ex = assertThrows(
                UnsupportedAudioFormatException.class,
                () -> DtmfFileDecoder.decode(source, stereoCfg),
                () -> "decode(AudioSource, cfg) must throw UnsupportedAudioFormatException "
                        + "for mono source paired with channelMode=" + stereoMode
                        + " (Req 8.9)");

        String msg = ex.getMessage();
        assertNotNull(msg, "UAFE message must not be null");
        assertAll(
                () -> assertTrue(msg.contains("mono") || msg.contains("1"),
                        () -> "Message must identify the mono source "
                                + "('mono' or channelCount '1'); was: " + msg),
                () -> assertTrue(msg.contains(stereoMode.name()),
                        () -> "Message must identify the offending channel mode '"
                                + stereoMode.name() + "'; was: " + msg),
                () -> assertTrue(msg.contains("MONO"),
                        () -> "Message must point the caller at "
                                + "ChannelMode.MONO as the fix; was: " + msg));
    }

    // ==================================================================
    // Arm C: sample rate outside [4000, 192000] — Req 17.3
    // ==================================================================

    /**
     * For any source sample rate outside {@code [4000, 192000]},
     * {@link DtmfFileDecoder#decode(AudioSource, DtmfConfig)} throws
     * {@link UnsupportedAudioFormatException} whose message names the
     * offending rate and the valid range (Req 17.3).
     *
     * <p>The generator samples from two disjoint sub-ranges that
     * straddle the valid range on both sides:
     *
     * <ul>
     *   <li>{@code [1, 3999]} — below the lower bound.
     *       {@code 1} is the lower bound of {@code RawPcmAudioSource}'s
     *       domain (Req 7.5); our stub has no such lower bound but
     *       matches that choice for consistency.</li>
     *   <li>{@code [192001, 384000]} — above the upper bound.
     *       {@code 384000} is the upper bound of
     *       {@code RawPcmAudioSource}'s domain (Req 7.5), representing
     *       the realistic ceiling of consumer/prosumer audio hardware
     *       (e.g., 4x oversampled 96 kHz).</li>
     * </ul>
     *
     * <p>The channel count is fixed at 1 and the config is
     * {@link DtmfConfig#forTelephony()} ({@code ChannelMode.MONO}) so
     * neither the channel-count guard (arm A) nor the mono/stereo-mode
     * guard (arm B) fires first; the sample-rate guard is the one
     * exercised.
     */
    @Property(tries = 100)
    void rejectsSourceSampleRateOutsideSupportedRange(
            @ForAll("invalidSampleRates") int sampleRate) {

        StubAudioSource source = new StubAudioSource(sampleRate, 1);

        UnsupportedAudioFormatException ex = assertThrows(
                UnsupportedAudioFormatException.class,
                () -> DtmfFileDecoder.decode(source, TELEPHONY),
                () -> "decode(AudioSource, cfg) must throw UnsupportedAudioFormatException "
                        + "for sampleRate=" + sampleRate + " Hz outside [4000, 192000] "
                        + "(Req 17.3)");

        String msg = ex.getMessage();
        assertNotNull(msg, "UAFE message must not be null");
        assertAll(
                () -> assertTrue(msg.contains(Integer.toString(sampleRate)),
                        () -> "Message must name the offending rate '"
                                + sampleRate + "'; was: " + msg),
                () -> assertTrue(msg.contains("4000") && msg.contains("192000"),
                        () -> "Message must name the valid range [4000, 192000] "
                                + "(Req 17.3); was: " + msg));
    }

    // ==================================================================
    // Arbitraries
    // ==================================================================

    /**
     * The two channel modes that assume interleaved stereo input.
     * {@code MONO} is excluded because pairing it with a mono source
     * is the accepted case, not a rejection path.
     */
    @Provide
    Arbitrary<ChannelMode> stereoChannelModes() {
        return Arbitraries.of(ChannelMode.STEREO_INDEPENDENT, ChannelMode.STEREO_DOWNMIX);
    }

    /**
     * Sample rates outside {@code [4000, 192000]} drawn from two
     * disjoint sub-ranges: {@code [1, 3999]} below the lower bound
     * and {@code [192001, 384000]} above the upper bound. The union
     * covers the guard boundary symmetrically; jqwik's shrinker will
     * collapse a failing counterexample to the smallest offending
     * value in whichever sub-range the failure originated.
     */
    @Provide
    Arbitrary<Integer> invalidSampleRates() {
        Arbitrary<Integer> below = Arbitraries.integers().between(1, 3_999);
        Arbitrary<Integer> above = Arbitraries.integers().between(192_001, 384_000);
        return Arbitraries.oneOf(below, above);
    }

    // ==================================================================
    // Test doubles
    // ==================================================================

    /**
     * Lightweight {@link AudioSource} stub whose {@code sampleRate()}
     * and {@code channelCount()} return caller-configurable values.
     * Used as the property input for every arm — the decoder's
     * guards read only {@code sampleRate()} and {@code channelCount()}
     * before rejecting, so the remaining {@code AudioSource} surface
     * is either fixed (bitDepth = 16, totalFrames = 0, canSeek = false)
     * or a deliberate trap.
     *
     * <p>{@link #read(double[], int, int) read(...)} throws
     * {@link AssertionError} because every rejection path under test
     * must fire before the read loop is entered. If
     * {@code decodeInternal} ever gets past the guards on an input
     * the property considers invalid, the read call turns that
     * silent guard failure into a loud test failure pointing at the
     * exact instance of the stub used in the counterexample.
     */
    private static final class StubAudioSource implements AudioSource {
        private final int sampleRate;
        private final int channelCount;

        StubAudioSource(int sampleRate, int channelCount) {
            this.sampleRate = sampleRate;
            this.channelCount = channelCount;
        }

        @Override
        public int sampleRate() {
            return sampleRate;
        }

        @Override
        public int channelCount() {
            return channelCount;
        }

        @Override
        public int bitDepth() {
            return 16;
        }

        @Override
        public long totalFrames() {
            return 0L;
        }

        @Override
        public int read(double[] buffer, int offset, int length) throws IOException {
            throw new AssertionError(
                    "DtmfFileDecoder.decodeInternal must reject the stub via one of "
                            + "its guards (channelCount > 2, mono + STEREO mode, or "
                            + "sample rate outside [4000, 192000]) before reading a "
                            + "single frame. read(...) reached despite "
                            + "sampleRate=" + sampleRate
                            + ", channelCount=" + channelCount);
        }

        @Override
        public boolean canSeek() {
            return false;
        }

        @Override
        public void seek(long frameIndex) {
            throw new UnsupportedOperationException(
                    "StubAudioSource is not seekable");
        }

        @Override
        public long currentFrame() {
            return 0L;
        }

        @Override
        public void close() {
            // No-op: the property's assertions operate on the thrown
            // exception, not on close() side-effects. Close-semantics
            // coverage lives in Property 9 / DtmfFileDecoderCloseSemanticsPropertyTest.
        }
    }
}
