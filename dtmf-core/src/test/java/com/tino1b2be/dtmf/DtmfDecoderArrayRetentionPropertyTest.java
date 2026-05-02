package com.tino1b2be.dtmf;

// Feature: dtmf-v2-foundation, Property 18: No retention or mutation of caller-supplied arrays

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.Size;

/**
 * Property-based test for array-handling contract in {@link DtmfDecoder}.
 *
 * <p><strong>Property 18: No retention or mutation of caller-supplied
 * arrays.</strong> <strong>Validates: Requirement 17.4.</strong>
 *
 * <p>For any input {@code double[] a} (random finite values) and its clone
 * {@code b = a.clone()}:
 *
 * <ol>
 *   <li>{@code decode(a, cfg)} equals {@code decode(b, cfg)} &mdash; behaviour
 *       does not depend on the reference identity;</li>
 *   <li>{@code a} equals {@code b} pointwise after the call &mdash; the
 *       decoder does not mutate the caller's buffer;</li>
 *   <li>mutating {@code a} after the call does not change the returned list
 *       &mdash; the decoder does not retain a reference to the caller's
 *       buffer (Requirement 17.4).</li>
 * </ol>
 */
class DtmfDecoderArrayRetentionPropertyTest {

    @Property(tries = 100)
    void decoderDoesNotMutateOrRetainInput(
            @ForAll("pcmSamples") @Size(max = 16_000) double[] input) {

        DtmfConfig cfg = DtmfConfig.advanced()
                .sampleRate(8000)
                .minimumToneDuration(Duration.ofMillis(60))
                .minimumGapDuration(Duration.ofMillis(40))
                .build();

        double[] original = input.clone();

        List<DtmfTone> viaOriginal = DtmfDecoder.decode(input, cfg);
        List<DtmfTone> viaClone = DtmfDecoder.decode(original, cfg);

        // (1) Same input value -> same result.
        assertEquals(viaClone, viaOriginal,
                "decode on equal arrays must produce equal results");

        // (2) Input array was not mutated.
        assertArrayEquals(original, input, 0.0,
                "decoder must not mutate caller's array");

        // (3) After the call, mutating the input does not change the
        // previously returned list. We serialize by snapshotting the list;
        // records are immutable so reference equality is sufficient.
        List<DtmfTone> snapshot = List.copyOf(viaOriginal);
        for (int i = 0; i < input.length; i++) {
            input[i] = 0.0;
        }
        assertEquals(snapshot, viaOriginal,
                "post-call mutation of input must not affect the returned list");
    }

    @Provide
    Arbitrary<double[]> pcmSamples() {
        // Scale 9 so bounds like ±1.0 are representable; default scale 2
        // would collapse to ±1.00 which is fine here but we set it
        // explicitly so narrower ranges in later iterations also work.
        Arbitrary<Double> samples = Arbitraries.doubles()
                .between(-1.0, 1.0).ofScale(9);
        return samples.array(double[].class);
    }
}
