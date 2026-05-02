package com.tino1b2be.dtmf;

// Feature: dtmf-v2-foundation, Property 7: Callback fires exactly once per tone, at tone-end

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
 * Property-based test for the push-detector callback contract.
 *
 * <p><strong>Property 7: Callback fires exactly once per tone, at
 * tone-end.</strong> <strong>Validates: Requirements 6.4, 6.5.</strong>
 *
 * <p>For a random DTMF sequence rendered via {@link DtmfGenerator} and
 * streamed through {@link DtmfDetector} in randomly-sized chunks, the
 * callback registered via
 * {@link DtmfDetector#onTone(java.util.function.Consumer)} is invoked
 * exactly {@code |s|} times, once per confirmed tone. The emitted key
 * sequence matches the input sequence in order.
 *
 * <p>Config uses a generous 60&nbsp;ms tone, 40&nbsp;ms gap at 8&nbsp;kHz so
 * that the detector reliably confirms every tone across the whole input
 * space; the narrow default {@code forTelephony()} can be flaky right at
 * the 40&nbsp;ms boundary.
 */
class DtmfDetectorCallbackPropertyTest {

    @Property(tries = 50)
    void callbackInvocationsMatchToneCount(
            @ForAll("dtmfSequences") String sequence,
            @ForAll @IntRange(min = 1, max = 2048) int chunkSize) {

        DtmfConfig cfg = DtmfConfig.advanced()
                .sampleRate(8000)
                .minimumToneDuration(Duration.ofMillis(60))
                .minimumGapDuration(Duration.ofMillis(40))
                .build();

        double[] audio = DtmfGenerator.generate(sequence, cfg);

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

        assertEquals(sequence.length(), received.size(),
                "expected one emission per tone in sequence \"" + sequence + "\"");

        StringBuilder actual = new StringBuilder(received.size());
        for (DtmfTone t : received) {
            actual.append(t.key());
        }
        assertEquals(sequence.toUpperCase(java.util.Locale.ROOT), actual.toString(),
                "emitted key order must match input sequence");
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
