package com.tino1b2be.dtmf;

// Feature: dtmf-v2-foundation, Property 17: Input validation

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

/**
 * Property-based tests for Requirement 17.1 (null-check) and 17.2
 * (out-of-domain numeric).
 *
 * <p><strong>Property 17: Input validation.</strong>
 * <strong>Validates: Requirements 17.1, 17.2.</strong>
 *
 * <p>The property is parameterised over the public entry points discovered
 * at Stage 3: every setter on {@link DtmfConfig.Advanced} that accepts a
 * reference parameter, plus the {@link DtmfTone} constructor. Later stages
 * (decoder, detector, stream, generator) will layer their own entry points
 * into this same property's test matrix.
 *
 * <p>Each named property method below targets a single parameter and a
 * single kind of failure, so a counter-example points at the precise
 * validation rule that regressed.
 */
class InputValidationPropertyTest {

    // --- Null-check properties (Requirement 17.1) ---

    @Property(tries = 100)
    void advancedMinimumToneDurationNullThrowsNpeWithParameterName(
            @ForAll @LongRange(min = 10L, max = 1000L) long sentinelMillis) {
        // The sentinel is there to make the test vary; we don't actually use it.
        // This just ensures the property runs its configured `tries` times
        // rather than collapsing to a single cached invocation.
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> DtmfConfig.advanced().minimumToneDuration(null));
        assertMessageMentions(ex, "minimumToneDuration");
        // Use the sentinel so jqwik considers the parameter meaningful.
        assertTrue(sentinelMillis >= 10L);
    }

    @Property(tries = 100)
    void advancedMinimumGapDurationNullThrowsNpeWithParameterName(
            @ForAll @LongRange(min = 0L, max = 1000L) long sentinelMillis) {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> DtmfConfig.advanced().minimumGapDuration(null));
        assertMessageMentions(ex, "minimumGapDuration");
        assertTrue(sentinelMillis >= 0L);
    }

    @Property(tries = 100)
    void advancedChannelModeNullThrowsNpeWithParameterName(
            @ForAll @IntRange(min = 8000, max = 48000) int sentinelRate) {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> DtmfConfig.advanced().channelMode(null));
        assertMessageMentions(ex, "channelMode");
        assertTrue(sentinelRate > 0);
    }

    @Property(tries = 100)
    void advancedWindowFunctionNullThrowsNpeWithParameterName(
            @ForAll @IntRange(min = 8000, max = 48000) int sentinelRate) {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> DtmfConfig.advanced().windowFunction(null));
        assertMessageMentions(ex, "windowFunction");
        assertTrue(sentinelRate > 0);
    }

    // --- Numeric-domain properties (Requirement 17.2) ---

    @Property(tries = 100)
    void advancedSampleRateOutOfDomainThrowsIaeWithParameterName(
            @ForAll @IntRange(min = -10_000, max = 250_000) int candidate) {
        // Advanced domain is [4000, 192000]. Discard anything inside.
        Assume.that(candidate < 4000 || candidate > 192_000);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DtmfConfig.advanced().sampleRate(candidate));
        assertMessageMentions(ex, "sampleRate");
    }

    @Property(tries = 100)
    void advancedDetectionThresholdOutOfDomainThrowsIaeWithParameterName(
            @ForAll @DoubleRange(min = -10.0, max = 10.0) double candidate) {
        // Domain is [0, 1]. Discard anything inside.
        Assume.that(candidate < 0.0 || candidate > 1.0);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DtmfConfig.advanced().detectionThreshold(candidate));
        assertMessageMentions(ex, "detectionThreshold");
    }

    @Property(tries = 100)
    void advancedConfirmationFramesOutOfDomainThrowsIaeWithParameterName(
            @ForAll @IntRange(min = -100, max = 0) int candidate) {
        // Domain is [1, +infty). Generator draws only values <= 0.
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DtmfConfig.advanced().confirmationFrames(candidate));
        assertMessageMentions(ex, "confirmationFrames");
    }

    @Property(tries = 100)
    void advancedAnalysisBlockSizeOutOfDomainThrowsIaeWithParameterName(
            @ForAll @IntRange(min = -1000, max = 0) int candidate) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DtmfConfig.advanced().analysisBlockSize(candidate));
        assertMessageMentions(ex, "analysisBlockSize");
    }

    @Property(tries = 100)
    void advancedMinimumToneDurationBelow10MsThrowsIaeWithParameterName(
            @ForAll @LongRange(min = 0L, max = 9L) long millis) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DtmfConfig.advanced().minimumToneDuration(Duration.ofMillis(millis)));
        assertMessageMentions(ex, "minimumToneDuration");
    }

    // --- DtmfTone constructor validation ---

    @Property(tries = 100)
    void dtmfToneNegativeStartSampleThrowsIaeWithParameterName(
            @ForAll @LongRange(min = Long.MIN_VALUE, max = -1L) long negativeStart) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new DtmfTone('5', negativeStart, negativeStart + 100L, 8000, 0.5, 0));
        assertMessageMentions(ex, "startSample");
    }

    @Property(tries = 100)
    void dtmfToneEndSampleNotGreaterThanStartThrowsIaeWithParameterName(
            @ForAll @LongRange(min = 0L, max = 100_000L) long startSample,
            @ForAll @LongRange(min = -100L, max = 0L) long delta) {
        // delta <= 0 means endSample <= startSample, which is out of domain.
        long endSample = startSample + delta;
        // Ensure no overflow and that endSample <= startSample (the failure
        // case Property 17 targets).
        Assume.that(endSample <= startSample);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new DtmfTone('5', startSample, endSample, 8000, 0.5, 0));
        assertMessageMentions(ex, "endSample");
    }

    @Property(tries = 100)
    void dtmfToneNonPositiveSampleRateThrowsIaeWithParameterName(
            @ForAll @IntRange(min = Integer.MIN_VALUE, max = 0) int candidate) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new DtmfTone('5', 0L, 100L, candidate, 0.5, 0));
        assertMessageMentions(ex, "sampleRate");
    }

    @Property(tries = 100)
    void dtmfToneConfidenceOutOfDomainThrowsIaeWithParameterName(
            @ForAll @DoubleRange(min = -10.0, max = 10.0) double candidate) {
        Assume.that(candidate < 0.0 || candidate > 1.0);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new DtmfTone('5', 0L, 100L, 8000, candidate, 0));
        assertMessageMentions(ex, "confidence");
    }

    @Property(tries = 100)
    void dtmfToneNegativeChannelThrowsIaeWithParameterName(
            @ForAll @IntRange(min = Integer.MIN_VALUE, max = -1) int negativeChannel) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new DtmfTone('5', 0L, 100L, 8000, 0.5, negativeChannel));
        assertMessageMentions(ex, "channel");
    }

    private static void assertMessageMentions(RuntimeException ex, String needle) {
        String message = ex.getMessage();
        if (message == null || !message.contains(needle)) {
            throw new AssertionError(
                    "Expected exception message to mention '" + needle + "', was: " + message);
        }
    }
}
