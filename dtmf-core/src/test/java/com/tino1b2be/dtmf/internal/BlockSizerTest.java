package com.tino1b2be.dtmf.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BlockSizer}.
 *
 * <p>Asserts the four Supported_Sample_Rate values land on the canonical
 * {@code N} values tabulated in {@code design.md} (Requirement 3.5) and that
 * each produces exactly 50&nbsp;Hz bin width. Also covers the argument
 * validation path for non-positive sample rates.
 */
class BlockSizerTest {

    private static final double EPSILON = 1e-12;

    @Test
    void blockSizeFor8000HzIs160With50HzBinWidth() {
        int n = BlockSizer.blockSizeFor(8000);
        assertEquals(160, n);
        assertEquals(50.0, 8000.0 / n, EPSILON);
    }

    @Test
    void blockSizeFor16000HzIs320With50HzBinWidth() {
        int n = BlockSizer.blockSizeFor(16000);
        assertEquals(320, n);
        assertEquals(50.0, 16000.0 / n, EPSILON);
    }

    @Test
    void blockSizeFor44100HzIs882With50HzBinWidth() {
        int n = BlockSizer.blockSizeFor(44100);
        assertEquals(882, n);
        assertEquals(50.0, 44100.0 / n, EPSILON);
    }

    @Test
    void blockSizeFor48000HzIs960With50HzBinWidth() {
        int n = BlockSizer.blockSizeFor(48000);
        assertEquals(960, n);
        assertEquals(50.0, 48000.0 / n, EPSILON);
    }

    @Test
    void blockSizeAlwaysProducesBinWidthInBand() {
        // Spot-check a handful of off-catalog rates that still fall inside
        // the advanced domain. Each must satisfy 40 <= Fs/N <= 60.
        int[] samples = {4000, 11025, 22050, 32000, 96000, 192000};
        for (int fs : samples) {
            int n = BlockSizer.blockSizeFor(fs);
            double bin = (double) fs / n;
            assertTrue(bin >= 40.0 && bin <= 60.0,
                    "Fs=" + fs + " N=" + n + " bin=" + bin);
        }
    }

    @Test
    void rejectsZeroSampleRate() {
        assertThrows(IllegalArgumentException.class, () -> BlockSizer.blockSizeFor(0));
    }

    @Test
    void rejectsNegativeSampleRate() {
        assertThrows(IllegalArgumentException.class, () -> BlockSizer.blockSizeFor(-1));
    }
}
