package com.tino1b2be.dtmf;

// Feature: dtmf-v2-foundation, Property 1: Chunk invariance

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based test for push-detector chunk invariance.
 *
 * <p><strong>Property 1: Chunk invariance.</strong>
 * <strong>Validates: Requirements 6.7, 6.8.</strong>
 *
 * <p>For any DTMF sequence and any chunking of the generated buffer, a fresh
 * {@link DtmfDetector} fed the chunks emits the same sequence of
 * {@link DtmfTone} as a fresh detector fed the buffer in one call. Equality
 * covers all six {@code DtmfTone} fields: {@code key},
 * {@code startSample}, {@code endSample}, {@code sampleRate},
 * {@code confidence}, {@code channel}.
 *
 * <p>Both paths end with {@link DtmfDetector#flush()} so any tone in flight
 * at the end of the buffer is force-emitted identically in both cases.
 */
class DtmfDetectorChunkInvariancePropertyTest {

    @Property(tries = 50)
    void chunkedProcessingMatchesSingleShot(
            @ForAll("dtmfSequences") String sequence,
            @ForAll @IntRange(min = 1, max = 4096) int maxChunkSize) {

        DtmfConfig cfg = DtmfConfig.advanced()
                .sampleRate(8000)
                .minimumToneDuration(Duration.ofMillis(60))
                .minimumGapDuration(Duration.ofMillis(40))
                .build();

        double[] audio = DtmfGenerator.generate(sequence, cfg);

        List<DtmfTone> singleShot = runOnce(audio, cfg, audio.length);
        List<DtmfTone> chunked = runOnce(audio, cfg, maxChunkSize);

        assertEquals(singleShot, chunked,
                "chunked and single-shot emissions must match for sequence \""
                        + sequence + "\" at chunk size " + maxChunkSize);
    }

    /**
     * Feed {@code audio} to a fresh detector in chunks no larger than
     * {@code chunkSize}, flush, and return the emitted tones in order.
     */
    private static List<DtmfTone> runOnce(double[] audio, DtmfConfig cfg, int chunkSize) {
        List<DtmfTone> received = new ArrayList<>();
        DtmfDetector detector = new DtmfDetector(cfg);
        detector.onTone(received::add);

        int pos = 0;
        while (pos < audio.length) {
            int len = Math.min(chunkSize, audio.length - pos);
            detector.process(audio, pos, len);
            pos += len;
        }
        detector.flush();
        return received;
    }

    @Provide
    Arbitrary<String> dtmfSequences() {
        return Arbitraries.of('0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                        'A', 'B', 'C', 'D', '*', '#')
                .list().ofMinSize(1).ofMaxSize(8)
                .map(chars -> {
                    StringBuilder sb = new StringBuilder(chars.size());
                    for (Character c : chars) {
                        sb.append(c);
                    }
                    return sb.toString();
                });
    }
}
