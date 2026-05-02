package com.tino1b2be.dtmf.io;

// Feature: dtmf-io, Property 4: RawPcmAudioSource constructor input validation

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteOrder;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for {@link RawPcmAudioSource} constructor input
 * validation.
 *
 * <p><strong>Property 4: {@code RawPcmAudioSource} constructor input
 * validation.</strong> <strong>Validates: Requirements 7.4, 7.5, 7.6,
 * 7.7, 7.8, 7.9, 12.1, 12.2.</strong>
 *
 * <p>For any randomly drawn out-of-domain value on each validation axis,
 * the constructor must throw {@link IllegalArgumentException} (numeric
 * domains) or {@link NullPointerException} (null parameters) whose
 * message identifies the offending value and the valid range / set, per
 * Requirements 12.1 ({@code NullPointerException} names the parameter)
 * and 12.2 ({@code IllegalArgumentException} names the offending value
 * and the expected domain).
 *
 * <p>Each property restricts itself to one axis at a time, holding every
 * other parameter to a known-valid configuration, so a failure
 * unambiguously points at the guard under test. The reference-valid
 * configuration used across the file is a {@code 1}-channel, {@code
 * 16}-bit, {@code SIGNED_INT}, {@code LITTLE_ENDIAN} buffer at {@code
 * 8000} Hz, which passes every other guard trivially.
 *
 * <p>Rationale for the per-axis split (rather than one sprawling
 * property): the design explicitly calls for a separate {@code
 * @Property(tries = 100)} method per out-of-domain dimension so that
 * jqwik's shrinker finds minimal counterexamples local to each guard,
 * and so that a regression in one validation path does not leak into
 * the error message of another.
 */
class RawPcmAudioSourceConstructorValidationPropertyTest {

    // ------------------------------------------------------------------
    // Known-valid reference configuration
    // ------------------------------------------------------------------
    //
    // These constants hold the "everything else is fine" baseline so each
    // property can vary exactly one parameter without tripping another
    // guard accidentally. When a property generates an out-of-domain
    // value on its axis of interest, it pairs it with these known-good
    // values for every other axis.

    private static final int VALID_SAMPLE_RATE = 8_000;
    private static final int VALID_CHANNEL_COUNT = 1;
    private static final int VALID_BIT_DEPTH = 16;
    private static final ByteOrder VALID_BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;
    private static final PcmEncoding VALID_ENCODING = PcmEncoding.SIGNED_INT;

    /**
     * A byte buffer whose length is a valid multiple of
     * {@code (VALID_BIT_DEPTH / 8) * VALID_CHANNEL_COUNT = 2} so the
     * "data.length not a multiple of frame size" guard stays silent.
     */
    private static final byte[] VALID_DATA = new byte[16];

    // ------------------------------------------------------------------
    // Property 4a — sampleRate outside [1, 384000] → IllegalArgumentException
    // ------------------------------------------------------------------

    /**
     * Any {@code sampleRate} outside {@code [1, 384000]} must trip the
     * sample-rate guard (Req 7.5). Generator covers both extremes: very
     * negative values including {@link Integer#MIN_VALUE}, the explicit
     * zero boundary (which is also out-of-range since the lower bound is
     * {@code 1}), and positive values beyond the {@code 384000} ceiling
     * up to {@link Integer#MAX_VALUE}.
     */
    @Property(tries = 100)
    void sampleRateOutOfRangeThrowsIllegalArgument(
            @ForAll("outOfRangeSampleRates") int badSampleRate) {

        IllegalArgumentException iae = assertThrows(
                IllegalArgumentException.class,
                () -> new RawPcmAudioSource(
                        VALID_DATA,
                        badSampleRate,
                        VALID_BIT_DEPTH,
                        VALID_BYTE_ORDER,
                        VALID_CHANNEL_COUNT,
                        VALID_ENCODING));

        String message = iae.getMessage();
        assertNotNull(message, "IllegalArgumentException must carry a message");
        assertTrue(
                message.contains("sampleRate"),
                () -> "Message must identify the parameter name 'sampleRate'; was: " + message);
        assertTrue(
                message.contains(Integer.toString(badSampleRate)),
                () -> "Message must contain the offending value " + badSampleRate
                        + "; was: " + message);
        // Expected-domain text: the message must communicate the valid
        // range so callers can correct the input. The production code
        // uses the exact bounds `[1, 384000]`; assert both bounds show up
        // without coupling to prose order.
        assertTrue(
                message.contains("1") && message.contains("384000"),
                () -> "Message must identify the valid range bounds 1 and 384000; was: " + message);
    }

    @Provide
    Arbitrary<Integer> outOfRangeSampleRates() {
        // Below the lower bound: any int in [Integer.MIN_VALUE, 0].
        Arbitrary<Integer> tooLow = Arbitraries.integers()
                .between(Integer.MIN_VALUE, 0);
        // Above the upper bound: any int in [384001, Integer.MAX_VALUE].
        Arbitrary<Integer> tooHigh = Arbitraries.integers()
                .between(384_001, Integer.MAX_VALUE);
        return Arbitraries.oneOf(tooLow, tooHigh);
    }

    // ------------------------------------------------------------------
    // Property 4b — channelCount outside [1, 8] → IllegalArgumentException
    // ------------------------------------------------------------------

    /**
     * Any {@code channelCount} outside {@code [1, 8]} must trip the
     * channel-count guard (Req 7.6). Generator covers negative values,
     * zero, and values above eight up to {@link Integer#MAX_VALUE}.
     */
    @Property(tries = 100)
    void channelCountOutOfRangeThrowsIllegalArgument(
            @ForAll("outOfRangeChannelCounts") int badChannelCount) {

        // The "data.length % bytesPerFrame == 0" guard fires later in the
        // constructor than the channel-count guard, so we can keep the
        // reference data buffer even when `channelCount` is invalid — the
        // channel-count guard throws first. Verify by assertion text.

        IllegalArgumentException iae = assertThrows(
                IllegalArgumentException.class,
                () -> new RawPcmAudioSource(
                        VALID_DATA,
                        VALID_SAMPLE_RATE,
                        VALID_BIT_DEPTH,
                        VALID_BYTE_ORDER,
                        badChannelCount,
                        VALID_ENCODING));

        String message = iae.getMessage();
        assertNotNull(message, "IllegalArgumentException must carry a message");
        assertTrue(
                message.contains("channelCount"),
                () -> "Message must identify the parameter name 'channelCount'; was: " + message);
        assertTrue(
                message.contains(Integer.toString(badChannelCount)),
                () -> "Message must contain the offending value " + badChannelCount
                        + "; was: " + message);
        assertTrue(
                message.contains("1") && message.contains("8"),
                () -> "Message must identify the valid range bounds 1 and 8; was: " + message);
    }

    @Provide
    Arbitrary<Integer> outOfRangeChannelCounts() {
        Arbitrary<Integer> tooLow = Arbitraries.integers()
                .between(Integer.MIN_VALUE, 0);
        Arbitrary<Integer> tooHigh = Arbitraries.integers()
                .between(9, Integer.MAX_VALUE);
        return Arbitraries.oneOf(tooLow, tooHigh);
    }

    // ------------------------------------------------------------------
    // Property 4c — bitDepth outside {16, 24, 32, 64} → IllegalArgumentException
    // ------------------------------------------------------------------

    /**
     * Any {@code bitDepth} not in the set {@code {16, 24, 32, 64}} must
     * trip the bit-depth guard (Req 7.7). The generator emits arbitrary
     * integers and filters out the four valid values so shrinking
     * converges on the smallest off-set value.
     */
    @Property(tries = 100)
    void bitDepthNotInSetThrowsIllegalArgument(
            @ForAll @IntRange(min = -1024, max = 1024) int badBitDepth) {
        Assume.that(badBitDepth != 16 && badBitDepth != 24
                && badBitDepth != 32 && badBitDepth != 64);

        // To reach the bit-depth guard we need the data-length guard and
        // the channel/sample-rate guards to stay silent. Use
        // `channelCount = 1` and a zero-length buffer: zero is a valid
        // multiple of every positive frame size regardless of bit depth,
        // so the data-length check passes unconditionally.
        //
        // If the random `bitDepth` happens to be <= 0, the production
        // code still hits the bit-depth guard first (since none of
        // `-k`, `0`, or other non-set values are in the set); that's the
        // contract we're testing.

        IllegalArgumentException iae = assertThrows(
                IllegalArgumentException.class,
                () -> new RawPcmAudioSource(
                        new byte[0],
                        VALID_SAMPLE_RATE,
                        badBitDepth,
                        VALID_BYTE_ORDER,
                        VALID_CHANNEL_COUNT,
                        VALID_ENCODING));

        String message = iae.getMessage();
        assertNotNull(message, "IllegalArgumentException must carry a message");
        assertTrue(
                message.contains("bitDepth"),
                () -> "Message must identify the parameter name 'bitDepth'; was: " + message);
        assertTrue(
                message.contains(Integer.toString(badBitDepth)),
                () -> "Message must contain the offending value " + badBitDepth
                        + "; was: " + message);
        // Expected-set text: all four accepted bit depths must appear so
        // the caller can correct the input.
        assertTrue(
                message.contains("16") && message.contains("24")
                        && message.contains("32") && message.contains("64"),
                () -> "Message must identify the valid set {16, 24, 32, 64}; was: " + message);
    }

    // ------------------------------------------------------------------
    // Property 4d — IEEE_FLOAT with bitDepth ∈ {16, 24} → IllegalArgumentException
    // ------------------------------------------------------------------

    /**
     * {@link PcmEncoding#IEEE_FLOAT} only supports 32- and 64-bit
     * samples. Pairing it with {@code bitDepth ∈ {16, 24}} must throw
     * {@link IllegalArgumentException} (Req 7.8).
     *
     * <p>The bit-depth guard runs before the float-specific guard in the
     * current production code, so to reach the float guard we restrict
     * the generator to {@code {16, 24}} — both of which pass the
     * per-value bit-depth check — and pair each with {@code IEEE_FLOAT}.
     * The point of the property is that the encoding/bit-depth pair is
     * the thing rejected, not the bit depth in isolation.
     */
    @Property(tries = 100)
    void ieeeFloatWithIntegerOnlyBitDepthThrowsIllegalArgument(
            @ForAll("integerOnlyBitDepths") int integerOnlyBitDepth) {

        IllegalArgumentException iae = assertThrows(
                IllegalArgumentException.class,
                () -> new RawPcmAudioSource(
                        new byte[0],
                        VALID_SAMPLE_RATE,
                        integerOnlyBitDepth,
                        VALID_BYTE_ORDER,
                        VALID_CHANNEL_COUNT,
                        PcmEncoding.IEEE_FLOAT));

        String message = iae.getMessage();
        assertNotNull(message, "IllegalArgumentException must carry a message");
        assertTrue(
                message.contains("IEEE_FLOAT"),
                () -> "Message must identify the invalid encoding 'IEEE_FLOAT'; was: " + message);
        assertTrue(
                message.contains(Integer.toString(integerOnlyBitDepth)),
                () -> "Message must contain the offending bit depth "
                        + integerOnlyBitDepth + "; was: " + message);
        // Expected-set text: the valid float bit depths 32 and 64 must
        // appear so the caller can correct the input.
        assertTrue(
                message.contains("32") && message.contains("64"),
                () -> "Message must identify the valid set {32, 64} for IEEE_FLOAT; was: "
                        + message);
    }

    @Provide
    Arbitrary<Integer> integerOnlyBitDepths() {
        // Integer PCM bit depths the constructor accepts that are NOT
        // valid for IEEE_FLOAT. Per Req 7.8 these are 16 and 24.
        return Arbitraries.of(16, 24);
    }

    // ------------------------------------------------------------------
    // Property 4e — data.length not a multiple of bytesPerFrame → IllegalArgumentException
    // ------------------------------------------------------------------

    /**
     * Any {@code data.length} that is not a positive multiple of
     * {@code (bitDepth / 8) * channelCount} must trip the frame-alignment
     * guard (Req 7.9). The generator draws from the valid cross-product
     * of bit depths and channel counts, computes the resulting frame
     * size, then chooses a buffer length that is strictly not a multiple
     * of that frame size.
     */
    @Property(tries = 100)
    void misalignedDataLengthThrowsIllegalArgument(
            @ForAll("validBitDepths") int bitDepth,
            @ForAll @IntRange(min = 1, max = 8) int channelCount,
            @ForAll @IntRange(min = 1, max = 4096) int rawLength) {

        int bytesPerFrame = (bitDepth / 8) * channelCount;
        // Skew `rawLength` until it is NOT a multiple of bytesPerFrame.
        // Using a straight offset guarantees a misaligned length within
        // the [1, 4096 + bytesPerFrame) window.
        int misalignedLength = rawLength;
        if (misalignedLength % bytesPerFrame == 0) {
            misalignedLength = misalignedLength + 1;
        }
        // Final safety check: if bytesPerFrame == 1 (only possible when
        // bitDepth == 16 divided by 8 == 2, no — bitDepth / 8 is at
        // least 2 since min bitDepth is 16), every length is a multiple
        // of 1 and the misalignment guard cannot fire. bytesPerFrame is
        // therefore always >= 2 for any valid (bitDepth, channelCount)
        // pair generated here, and the `+ 1` skew above reliably lands
        // on a misaligned length.
        Assume.that(misalignedLength % bytesPerFrame != 0);

        byte[] misalignedData = new byte[misalignedLength];

        // For IEEE_FLOAT we need bitDepth ∈ {32, 64}; pin encoding to
        // SIGNED_INT so the alignment guard is the one that fires, not
        // the encoding-vs-bit-depth guard. SIGNED_INT accepts all four
        // valid bit depths.

        final int finalMisalignedLength = misalignedLength;
        IllegalArgumentException iae = assertThrows(
                IllegalArgumentException.class,
                () -> new RawPcmAudioSource(
                        misalignedData,
                        VALID_SAMPLE_RATE,
                        bitDepth,
                        VALID_BYTE_ORDER,
                        channelCount,
                        PcmEncoding.SIGNED_INT));

        String message = iae.getMessage();
        assertNotNull(message, "IllegalArgumentException must carry a message");
        assertTrue(
                message.contains("data.length"),
                () -> "Message must identify the parameter 'data.length'; was: " + message);
        assertTrue(
                message.contains(Integer.toString(finalMisalignedLength)),
                () -> "Message must contain the offending data.length="
                        + finalMisalignedLength + "; was: " + message);
        assertTrue(
                message.contains("bytesPerFrame"),
                () -> "Message must identify the expected multiple 'bytesPerFrame'; was: "
                        + message);
        assertTrue(
                message.contains(Integer.toString(bytesPerFrame)),
                () -> "Message must contain the computed bytesPerFrame="
                        + bytesPerFrame + "; was: " + message);
        // The message should also identify the inputs that drove the
        // frame size so the caller can cross-check their own arithmetic.
        assertTrue(
                message.contains("bitDepth=" + bitDepth)
                        && message.contains("channelCount=" + channelCount),
                () -> "Message must identify bitDepth and channelCount driving the frame size; was: "
                        + message);
    }

    @Provide
    Arbitrary<Integer> validBitDepths() {
        return Arbitraries.of(16, 24, 32, 64);
    }

    // ------------------------------------------------------------------
    // Property 4f — null parameters throw NullPointerException (Req 7.4, 12.1)
    // ------------------------------------------------------------------

    /**
     * {@code Objects.requireNonNull} guards {@code data},
     * {@code byteOrder}, and {@code encoding}. The message must identify
     * which parameter is null so the caller can fix the wrong argument
     * (Req 7.4, 12.1). The property iterates over a uniformly chosen
     * null position and asserts the corresponding parameter name surfaces.
     *
     * <p>This is a small, enumerable input space (three positions), so
     * property-based coverage is borderline; we still express it as a
     * {@code @Property} for stylistic uniformity with the other guards
     * in this file and because jqwik's shrinker surfaces the "which
     * parameter" dimension crisply on failure.
     */
    @Property(tries = 100)
    void nullParameterThrowsNullPointerExceptionNamingIt(
            @ForAll @IntRange(min = 0, max = 2) int nullPosition) {

        byte[] data = nullPosition == 0 ? null : VALID_DATA;
        ByteOrder byteOrder = nullPosition == 1 ? null : VALID_BYTE_ORDER;
        PcmEncoding encoding = nullPosition == 2 ? null : VALID_ENCODING;
        String expectedParamName = switch (nullPosition) {
            case 0 -> "data";
            case 1 -> "byteOrder";
            case 2 -> "encoding";
            default -> throw new AssertionError("Unreachable nullPosition=" + nullPosition);
        };

        NullPointerException npe = assertThrows(
                NullPointerException.class,
                () -> new RawPcmAudioSource(
                        data,
                        VALID_SAMPLE_RATE,
                        VALID_BIT_DEPTH,
                        byteOrder,
                        VALID_CHANNEL_COUNT,
                        encoding));

        String message = npe.getMessage();
        assertNotNull(message, "NullPointerException must carry a message");
        assertEquals(
                expectedParamName, message,
                "Objects.requireNonNull must propagate the parameter name verbatim as the message");
    }
}
