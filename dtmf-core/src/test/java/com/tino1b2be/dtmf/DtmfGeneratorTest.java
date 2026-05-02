package com.tino1b2be.dtmf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DtmfGenerator}. Covers Task 7.2:
 *
 * <ul>
 *   <li>empty sequence produces an empty array;</li>
 *   <li>one-character sequence produces exactly {@code N} samples with no
 *       trailing gap;</li>
 *   <li>two-character sequence produces exactly {@code N + M + N} samples
 *       and the middle {@code M} samples are zero;</li>
 *   <li>invalid characters raise {@link IllegalArgumentException} naming
 *       the character and its index;</li>
 *   <li>lowercase {@code a-d} round-trips through the decoder as uppercase
 *       {@code A-D};</li>
 *   <li>null inputs raise {@link NullPointerException} naming the
 *       parameter.</li>
 * </ul>
 */
class DtmfGeneratorTest {

    private static final DtmfConfig CFG = DtmfConfig.advanced()
            .sampleRate(8000)
            .minimumToneDuration(Duration.ofMillis(50))
            .minimumGapDuration(Duration.ofMillis(20))
            .build();

    /** {@code N = 50 ms * 8 kHz = 400} samples. */
    private static final int N = 400;

    /** {@code M = 20 ms * 8 kHz = 160} samples. */
    private static final int M = 160;

    @Test
    void emptySequenceProducesEmptyArray() {
        double[] out = DtmfGenerator.generate("", CFG);
        assertEquals(0, out.length);
    }

    @Test
    void singleCharacterHasExactlyNSamplesAndNoTrailingGap() {
        double[] out = DtmfGenerator.generate("5", CFG);
        assertEquals(N, out.length,
                "single-character sequence must produce exactly N samples");
    }

    @Test
    void twoCharacterSequenceHasExpectedLengthAndSilentMiddle() {
        double[] out = DtmfGenerator.generate("12", CFG);
        assertEquals(N + M + N, out.length,
                "two-character sequence must produce N + M + N samples");

        // The middle M samples, positioned at [N, N + M), must be exactly 0.
        for (int i = N; i < N + M; i++) {
            assertEquals(0.0, out[i], 0.0,
                    "gap sample at index " + i + " must be zero");
        }

        // Flanking tone samples must not all be zero.
        assertTrue(hasNonZero(out, 0, N),
                "first tone segment must contain non-zero samples");
        assertTrue(hasNonZero(out, N + M, out.length),
                "second tone segment must contain non-zero samples");
    }

    @Test
    void invalidCharacterAtIndexZeroThrowsIaeNamingCharAndIndex() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DtmfGenerator.generate("Z", CFG));
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("Z"),
                "expected message to mention 'Z', was: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("0"),
                "expected message to mention index 0, was: " + ex.getMessage());
    }

    @Test
    void invalidCharacterDeepInSequenceThrowsIaeNamingCorrectIndex() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DtmfGenerator.generate("12X4", CFG));
        assertTrue(ex.getMessage().contains("X"));
        assertTrue(ex.getMessage().contains("2"),
                "expected message to mention index 2, was: " + ex.getMessage());
    }

    @Test
    void lowercaseAToDAreNormalizedToUppercase() {
        // Generate with lowercase, decode, and assert the decoded keys are
        // the uppercase counterparts (A-D in Q.23).
        DtmfConfig decodeCfg = DtmfConfig.advanced()
                .sampleRate(8000)
                .minimumToneDuration(Duration.ofMillis(60))
                .minimumGapDuration(Duration.ofMillis(40))
                .build();
        double[] lowerAudio = DtmfGenerator.generate("abcd", decodeCfg);
        double[] upperAudio = DtmfGenerator.generate("ABCD", decodeCfg);

        // The audio for "abcd" and "ABCD" must be identical.
        assertEquals(upperAudio.length, lowerAudio.length);
        for (int i = 0; i < upperAudio.length; i++) {
            assertEquals(upperAudio[i], lowerAudio[i], 0.0,
                    "sample at " + i + " should match uppercase output");
        }

        List<DtmfTone> decoded = DtmfDecoder.decode(lowerAudio, decodeCfg);
        StringBuilder actual = new StringBuilder();
        for (DtmfTone t : decoded) {
            actual.append(t.key());
        }
        assertEquals("ABCD", actual.toString(),
                "lowercase a-d must decode back as uppercase A-D");
    }

    @Test
    void nullSequenceThrowsNpeNamingSequence() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> DtmfGenerator.generate(null, CFG));
        assertTrue(ex.getMessage().contains("sequence"),
                "expected message to mention 'sequence', was: " + ex.getMessage());
    }

    @Test
    void nullConfigThrowsNpeNamingConfig() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> DtmfGenerator.generate("5", null));
        assertTrue(ex.getMessage().contains("config"),
                "expected message to mention 'config', was: " + ex.getMessage());
    }

    @Test
    void generateIntoWritesSamplesAtOffsetAndReturnsWrittenCount() {
        double[] out = new double[N + 100];
        int written = DtmfGenerator.generateInto("5", CFG, out, 100);
        assertEquals(N, written);

        // Samples before offset remain zero.
        for (int i = 0; i < 100; i++) {
            assertEquals(0.0, out[i], 0.0, "prefix sample " + i + " must be zero");
        }
        assertTrue(hasNonZero(out, 100, 100 + N));
    }

    @Test
    void generateIntoRejectsNegativeOffset() {
        assertThrows(IllegalArgumentException.class,
                () -> DtmfGenerator.generateInto("5", CFG, new double[N], -1));
    }

    @Test
    void generateIntoRejectsTooSmallBuffer() {
        assertThrows(IndexOutOfBoundsException.class,
                () -> DtmfGenerator.generateInto("55", CFG, new double[N], 0));
    }

    @Test
    void generateIntoRejectsNullInputs() {
        assertThrows(NullPointerException.class,
                () -> DtmfGenerator.generateInto(null, CFG, new double[N], 0));
        assertThrows(NullPointerException.class,
                () -> DtmfGenerator.generateInto("5", null, new double[N], 0));
        assertThrows(NullPointerException.class,
                () -> DtmfGenerator.generateInto("5", CFG, null, 0));
    }

    private static boolean hasNonZero(double[] arr, int from, int to) {
        for (int i = from; i < to; i++) {
            if (arr[i] != 0.0) {
                return true;
            }
        }
        return false;
    }
}
