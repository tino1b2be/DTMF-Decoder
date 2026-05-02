package com.tino1b2be.dtmf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DtmfTone}.
 *
 * <p>Covers both the compact-constructor validation (Requirement 17.2) and
 * the three time-helper accessors defined by Requirement 14.
 */
class DtmfToneTest {

    @Test
    void rejectsNegativeStartSample() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new DtmfTone('5', -1L, 100L, 8000, 0.9, 0));
        assertMessageMentions(ex, "startSample");
    }

    @Test
    void rejectsEndSampleEqualToStart() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new DtmfTone('5', 100L, 100L, 8000, 0.9, 0));
        assertMessageMentions(ex, "endSample");
    }

    @Test
    void rejectsEndSampleLessThanStart() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new DtmfTone('5', 200L, 100L, 8000, 0.9, 0));
        assertMessageMentions(ex, "endSample");
    }

    @Test
    void rejectsZeroSampleRate() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new DtmfTone('5', 0L, 100L, 0, 0.9, 0));
        assertMessageMentions(ex, "sampleRate");
    }

    @Test
    void rejectsNegativeSampleRate() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new DtmfTone('5', 0L, 100L, -1, 0.9, 0));
        assertMessageMentions(ex, "sampleRate");
    }

    @Test
    void rejectsConfidenceBelowZero() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new DtmfTone('5', 0L, 100L, 8000, -0.0001, 0));
        assertMessageMentions(ex, "confidence");
    }

    @Test
    void rejectsConfidenceAboveOne() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new DtmfTone('5', 0L, 100L, 8000, 1.0001, 0));
        assertMessageMentions(ex, "confidence");
    }

    @Test
    void rejectsConfidenceNaN() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new DtmfTone('5', 0L, 100L, 8000, Double.NaN, 0));
        assertMessageMentions(ex, "confidence");
    }

    @Test
    void rejectsNegativeChannel() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new DtmfTone('5', 0L, 100L, 8000, 0.9, -1));
        assertMessageMentions(ex, "channel");
    }

    @Test
    void acceptsConfidenceAtBoundaries() {
        // Must not throw.
        DtmfTone lo = new DtmfTone('5', 0L, 100L, 8000, 0.0, 0);
        DtmfTone hi = new DtmfTone('5', 0L, 100L, 8000, 1.0, 0);
        assertEquals(0.0, lo.confidence());
        assertEquals(1.0, hi.confidence());
    }

    @Test
    void startTimeEndTimeAndDurationForOneSecondAt8kHz() {
        // The canonical example from Task 3.2: (0, 8000, 8000) → (PT0S, PT1S, PT1S).
        DtmfTone t = new DtmfTone('5', 0L, 8000L, 8000, 0.9, 0);
        assertEquals(Duration.ZERO, t.startTime());
        assertEquals(Duration.ofSeconds(1), t.endTime());
        assertEquals(Duration.ofSeconds(1), t.duration());
    }

    @Test
    void durationMatchesEndMinusStart() {
        DtmfTone t = new DtmfTone('A', 16000L, 40000L, 16000, 0.75, 1);
        Duration expected = t.endTime().minus(t.startTime());
        assertEquals(expected, t.duration());
    }

    @Test
    void accessorsReturnConstructedValues() {
        DtmfTone t = new DtmfTone('#', 123L, 4567L, 44100, 0.42, 1);
        assertEquals('#', t.key());
        assertEquals(123L, t.startSample());
        assertEquals(4567L, t.endSample());
        assertEquals(44100, t.sampleRate());
        assertEquals(0.42, t.confidence());
        assertEquals(1, t.channel());
    }

    private static void assertMessageMentions(IllegalArgumentException ex, String needle) {
        String message = ex.getMessage();
        if (message == null || !message.contains(needle)) {
            throw new AssertionError(
                    "Expected exception message to mention '" + needle + "', was: " + message);
        }
    }
}
