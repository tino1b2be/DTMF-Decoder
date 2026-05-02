package com.tino1b2be.dtmf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DtmfStream}. Covers Task 9.2:
 *
 * <ul>
 *   <li>{@code fromSamples(new double[0], cfg)} reports {@code hasNext() == false};</li>
 *   <li>iterating a generated sequence produces the same tones as
 *       {@link DtmfDecoder#decode(double[], DtmfConfig)};</li>
 *   <li>{@link DtmfStream#next()} throws {@link NoSuchElementException} when
 *       the iterator is exhausted;</li>
 *   <li>{@link DtmfStream#close()} is idempotent.</li>
 * </ul>
 */
class DtmfStreamTest {

    private static final DtmfConfig CFG = DtmfConfig.advanced()
            .sampleRate(8000)
            .minimumToneDuration(Duration.ofMillis(60))
            .minimumGapDuration(Duration.ofMillis(40))
            .build();

    @Test
    void fromSamplesWithEmptyArrayHasNoTones() {
        try (DtmfStream stream = DtmfStream.fromSamples(new double[0], CFG)) {
            assertFalse(stream.hasNext());
        }
    }

    @Test
    void iteratingProducesSameTonesAsBatchDecode() {
        double[] audio = DtmfGenerator.generate("A23", CFG);
        List<DtmfTone> fromStream = new ArrayList<>();
        try (DtmfStream stream = DtmfStream.fromSamples(audio, CFG)) {
            while (stream.hasNext()) {
                fromStream.add(stream.next());
            }
        }
        List<DtmfTone> fromBatch = DtmfDecoder.decode(audio, CFG);
        assertEquals(fromBatch, fromStream,
                "pull API must produce the same emissions as batch decode");
    }

    @Test
    void nextThrowsNoSuchElementAfterExhaustion() {
        double[] audio = DtmfGenerator.generate("5", CFG);
        try (DtmfStream stream = DtmfStream.fromSamples(audio, CFG)) {
            assertTrue(stream.hasNext());
            stream.next();
            assertFalse(stream.hasNext());
            assertThrows(NoSuchElementException.class, stream::next);
        }
    }

    @Test
    void closeIsIdempotent() {
        double[] audio = DtmfGenerator.generate("5", CFG);
        DtmfStream stream = DtmfStream.fromSamples(audio, CFG);
        stream.close();
        stream.close(); // second close must not throw.
    }

    @Test
    void closeBeforeIterationDoesNotThrow() {
        double[] audio = DtmfGenerator.generate("12", CFG);
        DtmfStream stream = DtmfStream.fromSamples(audio, CFG);
        stream.close();
        // After close hasNext must not throw; pending queue is empty so
        // hasNext returns false (the source was never actually read).
        // The contract is "safe to call multiple times" — we assert it stays
        // safe after close.
        assertFalse(stream.hasNext());
    }

    @Test
    void fromSamplesRejectsNullSamples() {
        assertThrows(NullPointerException.class,
                () -> DtmfStream.fromSamples(null, CFG));
    }

    @Test
    void fromSamplesRejectsNullConfig() {
        assertThrows(NullPointerException.class,
                () -> DtmfStream.fromSamples(new double[0], null));
    }

    @Test
    void fromSourceRejectsNullSource() {
        assertThrows(NullPointerException.class,
                () -> DtmfStream.fromSource(null, CFG));
    }

    @Test
    void fromSourceRejectsNullConfig() {
        DtmfStream.SampleSource source = (b, o, l) -> -1;
        assertThrows(NullPointerException.class,
                () -> DtmfStream.fromSource(source, null));
    }

    @Test
    void customSourceProducesEmissions() {
        // A source that delivers the pre-generated buffer one chunk at a time
        // should produce the same tones as fromSamples.
        double[] audio = DtmfGenerator.generate("789", CFG);
        int[] position = {0};
        DtmfStream.SampleSource source = (buffer, offset, length) -> {
            if (position[0] >= audio.length) {
                return -1;
            }
            int remaining = audio.length - position[0];
            int n = Math.min(length, Math.min(remaining, 256));
            System.arraycopy(audio, position[0], buffer, offset, n);
            position[0] += n;
            return n;
        };

        List<DtmfTone> collected = new ArrayList<>();
        try (DtmfStream stream = DtmfStream.fromSource(source, CFG)) {
            while (stream.hasNext()) {
                collected.add(stream.next());
            }
        }
        assertEquals(3, collected.size());
        assertEquals('7', collected.get(0).key());
        assertEquals('8', collected.get(1).key());
        assertEquals('9', collected.get(2).key());
    }
}
