package com.tino1b2be.dtmf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DtmfConfig} validation and factory behaviour.
 *
 * <p>Covers Requirements 3.2–3.4 (sample-rate domain), 8.7 (immutability),
 * 8.8 (minimum tone duration lower bound), and 17.1–17.2 (validation errors
 * and messages). Each test exercises one validation rule in isolation so a
 * failure pinpoints the offending rule.
 */
class DtmfConfigTest {

    // --- Null argument validation (Requirement 17.1) ---

    @Test
    void advancedRejectsNullMinimumToneDuration() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> DtmfConfig.advanced().minimumToneDuration(null));
        assertMessageMentions(ex, "minimumToneDuration");
    }

    @Test
    void advancedRejectsNullMinimumGapDuration() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> DtmfConfig.advanced().minimumGapDuration(null));
        assertMessageMentions(ex, "minimumGapDuration");
    }

    @Test
    void advancedRejectsNullChannelMode() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> DtmfConfig.advanced().channelMode(null));
        assertMessageMentions(ex, "channelMode");
    }

    @Test
    void advancedRejectsNullWindowFunction() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> DtmfConfig.advanced().windowFunction(null));
        assertMessageMentions(ex, "windowFunction");
    }

    // --- Numeric domain validation (Requirement 17.2) ---

    @Test
    void advancedRejectsSampleRateBelowLowerBound() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DtmfConfig.advanced().sampleRate(3999));
        assertMessageMentions(ex, "sampleRate");
    }

    @Test
    void advancedRejectsSampleRateAboveUpperBound() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DtmfConfig.advanced().sampleRate(192_001));
        assertMessageMentions(ex, "sampleRate");
    }

    @Test
    void advancedRejectsZeroSampleRate() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DtmfConfig.advanced().sampleRate(0));
        assertMessageMentions(ex, "sampleRate");
    }

    @Test
    void advancedRejectsNegativeSampleRate() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DtmfConfig.advanced().sampleRate(-1));
        assertMessageMentions(ex, "sampleRate");
    }

    @Test
    void advancedAcceptsSampleRate11025() {
        // 11025 is outside the standard-factory set but inside [4000, 192000].
        DtmfConfig cfg = DtmfConfig.advanced().sampleRate(11025).build();
        assertEquals(11025, cfg.sampleRate());
    }

    @Test
    void advancedAcceptsLowerBoundaryAndUpperBoundary() {
        DtmfConfig lo = DtmfConfig.advanced().sampleRate(4000).build();
        DtmfConfig hi = DtmfConfig.advanced().sampleRate(192_000).build();
        assertEquals(4000, lo.sampleRate());
        assertEquals(192_000, hi.sampleRate());
    }

    // --- Requirement 8.8: minimum tone duration lower bound ---

    @Test
    void advancedRejectsMinimumToneDurationBelow10ms() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DtmfConfig.advanced().minimumToneDuration(Duration.ofMillis(9)));
        assertMessageMentions(ex, "minimumToneDuration");
    }

    @Test
    void advancedAcceptsMinimumToneDurationAt10ms() {
        DtmfConfig cfg = DtmfConfig.advanced()
                .minimumToneDuration(Duration.ofMillis(10))
                .build();
        assertEquals(Duration.ofMillis(10), cfg.minimumToneDuration());
    }

    @Test
    void advancedRejectsNegativeMinimumGapDuration() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DtmfConfig.advanced().minimumGapDuration(Duration.ofMillis(-1)));
        assertMessageMentions(ex, "minimumGapDuration");
    }

    // --- Detection threshold ---

    @Test
    void advancedRejectsDetectionThresholdBelowZero() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DtmfConfig.advanced().detectionThreshold(-0.0001));
        assertMessageMentions(ex, "detectionThreshold");
    }

    @Test
    void advancedRejectsDetectionThresholdAboveOne() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DtmfConfig.advanced().detectionThreshold(1.0001));
        assertMessageMentions(ex, "detectionThreshold");
    }

    @Test
    void advancedRejectsDetectionThresholdNaN() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DtmfConfig.advanced().detectionThreshold(Double.NaN));
        assertMessageMentions(ex, "detectionThreshold");
    }

    // --- Twist tolerances ---

    @Test
    void buildRejectsForwardLessThanOrEqualReverse() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DtmfConfig.advanced()
                        .forwardTwistDb(-5.0)
                        .reverseTwistDb(-5.0)
                        .build());
        assertMessageMentions(ex, "forwardTwistDb");
    }

    @Test
    void buildRejectsForwardLessThanReverse() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DtmfConfig.advanced()
                        .forwardTwistDb(-10.0)
                        .reverseTwistDb(0.0)
                        .build());
        assertMessageMentions(ex, "forwardTwistDb");
    }

    @Test
    void advancedRejectsNonFiniteForwardTwist() {
        IllegalArgumentException exNaN = assertThrows(IllegalArgumentException.class,
                () -> DtmfConfig.advanced().forwardTwistDb(Double.NaN));
        assertMessageMentions(exNaN, "forwardTwistDb");

        IllegalArgumentException exInf = assertThrows(IllegalArgumentException.class,
                () -> DtmfConfig.advanced().forwardTwistDb(Double.POSITIVE_INFINITY));
        assertMessageMentions(exInf, "forwardTwistDb");
    }

    @Test
    void advancedRejectsNonFiniteReverseTwist() {
        IllegalArgumentException exNaN = assertThrows(IllegalArgumentException.class,
                () -> DtmfConfig.advanced().reverseTwistDb(Double.NaN));
        assertMessageMentions(exNaN, "reverseTwistDb");

        IllegalArgumentException exInf = assertThrows(IllegalArgumentException.class,
                () -> DtmfConfig.advanced().reverseTwistDb(Double.NEGATIVE_INFINITY));
        assertMessageMentions(exInf, "reverseTwistDb");
    }

    // --- Confirmation frames ---

    @Test
    void advancedRejectsConfirmationFramesZero() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DtmfConfig.advanced().confirmationFrames(0));
        assertMessageMentions(ex, "confirmationFrames");
    }

    @Test
    void advancedRejectsNegativeConfirmationFrames() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DtmfConfig.advanced().confirmationFrames(-1));
        assertMessageMentions(ex, "confirmationFrames");
    }

    // --- Analysis block size ---

    @Test
    void advancedRejectsZeroAnalysisBlockSize() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DtmfConfig.advanced().analysisBlockSize(0));
        assertMessageMentions(ex, "analysisBlockSize");
    }

    @Test
    void analysisBlockSizeAutoDerivedWhenUnset() {
        // Advanced without explicit analysisBlockSize(...) uses BlockSizer.
        DtmfConfig at8k = DtmfConfig.advanced().sampleRate(8000).build();
        DtmfConfig at48k = DtmfConfig.advanced().sampleRate(48000).build();
        assertEquals(160, at8k.analysisBlockSize());
        assertEquals(960, at48k.analysisBlockSize());
    }

    @Test
    void analysisBlockSizeRespectsExplicitOverride() {
        DtmfConfig cfg = DtmfConfig.advanced()
                .sampleRate(8000)
                .analysisBlockSize(256)
                .build();
        assertEquals(256, cfg.analysisBlockSize());
    }

    // --- Factory settings (design.md + task spec) ---

    @Test
    void defaultsDelegatesToForTelephony() {
        DtmfConfig def = DtmfConfig.defaults();
        DtmfConfig tel = DtmfConfig.forTelephony();
        // Value-by-value comparison (DtmfConfig isn't a record, so no equals).
        assertEquals(tel.sampleRate(), def.sampleRate());
        assertEquals(tel.analysisBlockSize(), def.analysisBlockSize());
        assertEquals(tel.minimumToneDuration(), def.minimumToneDuration());
        assertEquals(tel.minimumGapDuration(), def.minimumGapDuration());
        assertEquals(tel.detectionThreshold(), def.detectionThreshold());
        assertEquals(tel.channelMode(), def.channelMode());
        assertEquals(tel.windowFunction(), def.windowFunction());
        assertEquals(tel.forwardTwistDb(), def.forwardTwistDb());
        assertEquals(tel.reverseTwistDb(), def.reverseTwistDb());
        assertEquals(tel.confirmationFrames(), def.confirmationFrames());
    }

    @Test
    void forTelephonyHasSpecifiedKnobs() {
        DtmfConfig c = DtmfConfig.forTelephony();
        assertEquals(8000, c.sampleRate());
        assertEquals(Duration.ofMillis(40), c.minimumToneDuration());
        assertEquals(Duration.ofMillis(40), c.minimumGapDuration());
        assertEquals(0.25, c.detectionThreshold());
        assertEquals(ChannelMode.MONO, c.channelMode());
        assertEquals(WindowFunction.RECTANGULAR, c.windowFunction());
        assertEquals(4.0, c.forwardTwistDb());
        assertEquals(-8.0, c.reverseTwistDb());
        assertEquals(2, c.confirmationFrames());
    }

    @Test
    void forVoipHasThreeConfirmationFrames() {
        DtmfConfig c = DtmfConfig.forVoip();
        assertEquals(8000, c.sampleRate());
        assertEquals(Duration.ofMillis(40), c.minimumToneDuration());
        assertEquals(3, c.confirmationFrames());
        // Other knobs identical to forTelephony.
        assertEquals(0.25, c.detectionThreshold());
        assertEquals(4.0, c.forwardTwistDb());
        assertEquals(-8.0, c.reverseTwistDb());
    }

    @Test
    void forNoisyAudioHasRaisedThresholdAndFourFrames() {
        DtmfConfig c = DtmfConfig.forNoisyAudio();
        assertEquals(8000, c.sampleRate());
        assertEquals(Duration.ofMillis(50), c.minimumToneDuration());
        assertEquals(0.35, c.detectionThreshold());
        assertEquals(4, c.confirmationFrames());
        assertEquals(4.0, c.forwardTwistDb());
        assertEquals(-8.0, c.reverseTwistDb());
    }

    // --- Immutability (Requirement 8.7) ---

    @Test
    void accessorsReturnSameValueAcrossRepeatedCalls() {
        DtmfConfig cfg = DtmfConfig.advanced()
                .sampleRate(16000)
                .minimumToneDuration(Duration.ofMillis(50))
                .minimumGapDuration(Duration.ofMillis(25))
                .detectionThreshold(0.5)
                .channelMode(ChannelMode.STEREO_INDEPENDENT)
                .windowFunction(WindowFunction.HANN)
                .forwardTwistDb(2.0)
                .reverseTwistDb(-6.0)
                .confirmationFrames(3)
                .build();

        // Every accessor must return an equal value on repeated calls —
        // the configuration is immutable.
        for (int i = 0; i < 5; i++) {
            assertEquals(16000, cfg.sampleRate());
            assertEquals(320, cfg.analysisBlockSize());
            assertEquals(Duration.ofMillis(50), cfg.minimumToneDuration());
            assertEquals(Duration.ofMillis(25), cfg.minimumGapDuration());
            assertEquals(0.5, cfg.detectionThreshold());
            assertEquals(ChannelMode.STEREO_INDEPENDENT, cfg.channelMode());
            assertEquals(WindowFunction.HANN, cfg.windowFunction());
            assertEquals(2.0, cfg.forwardTwistDb());
            assertEquals(-6.0, cfg.reverseTwistDb());
            assertEquals(3, cfg.confirmationFrames());
        }
    }

    @Test
    void accessorsReturnSameReferenceAcrossRepeatedCallsForEnums() {
        DtmfConfig cfg = DtmfConfig.forTelephony();
        assertSame(cfg.channelMode(), cfg.channelMode());
        assertSame(cfg.windowFunction(), cfg.windowFunction());
    }

    @Test
    void advancedBuilderReturnsItselfForChaining() {
        DtmfConfig.Advanced builder = DtmfConfig.advanced();
        assertSame(builder, builder.sampleRate(16000));
        assertSame(builder, builder.analysisBlockSize(256));
        assertSame(builder, builder.minimumToneDuration(Duration.ofMillis(30)));
        assertSame(builder, builder.minimumGapDuration(Duration.ofMillis(10)));
        assertSame(builder, builder.detectionThreshold(0.3));
        assertSame(builder, builder.channelMode(ChannelMode.MONO));
        assertSame(builder, builder.windowFunction(WindowFunction.HAMMING));
        assertSame(builder, builder.forwardTwistDb(3.0));
        assertSame(builder, builder.reverseTwistDb(-7.0));
        assertSame(builder, builder.confirmationFrames(2));
        assertNotNull(builder.build());
    }

    // Regression guard for Property 20: standard-factory validator rejects
    // every unsupported rate with a message enumerating the supported set.
    @Test
    void validateStandardFactorySampleRateRejectsUnsupported() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DtmfConfig.validateStandardFactorySampleRate(11025));
        String message = ex.getMessage();
        assertTrue(message != null && message.contains("8000"), "must mention 8000");
        assertTrue(message.contains("16000"), "must mention 16000");
        assertTrue(message.contains("44100"), "must mention 44100");
        assertTrue(message.contains("48000"), "must mention 48000");
        assertTrue(message.contains("11025"), "must mention offending value");
    }

    @Test
    void validateStandardFactorySampleRateAcceptsEachSupportedRate() {
        // Must not throw.
        DtmfConfig.validateStandardFactorySampleRate(8000);
        DtmfConfig.validateStandardFactorySampleRate(16000);
        DtmfConfig.validateStandardFactorySampleRate(44100);
        DtmfConfig.validateStandardFactorySampleRate(48000);
    }

    private static void assertMessageMentions(RuntimeException ex, String needle) {
        String message = ex.getMessage();
        if (message == null || !message.contains(needle)) {
            throw new AssertionError(
                    "Expected exception message to mention '" + needle + "', was: " + message);
        }
    }
}
