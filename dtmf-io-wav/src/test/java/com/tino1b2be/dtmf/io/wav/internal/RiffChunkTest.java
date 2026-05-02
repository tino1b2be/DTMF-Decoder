package com.tino1b2be.dtmf.io.wav.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link RiffChunk} record (Task 6.2).
 *
 * <p>Validates the contract documented on the record: non-null {@code id},
 * non-negative {@code size}, and non-negative {@code dataStartOffset}. The
 * record deliberately does not enforce a four-character {@code id} length,
 * so test fixtures and the higher-level parser are both free to pass IDs
 * of any ASCII length; that "no length check" is pinned too so a later
 * refactor cannot silently tighten the contract.
 */
class RiffChunkTest {

    @Test
    @DisplayName("Valid chunk header stores id, size, and dataStartOffset exactly")
    void validChunkHeaderRoundTripsFields() {
        RiffChunk chunk = new RiffChunk("fmt ", 16L, 20L);
        assertEquals("fmt ", chunk.id());
        assertEquals(16L, chunk.size());
        assertEquals(20L, chunk.dataStartOffset());
    }

    @Test
    @DisplayName("Four-character data chunk id is preserved including trailing characters")
    void dataChunkIdIsPreserved() {
        RiffChunk chunk = new RiffChunk("data", 8_000_000L, 44L);
        assertEquals("data", chunk.id());
        assertEquals(8_000_000L, chunk.size());
    }

    @Test
    @DisplayName("Null id is rejected with a parameter-named NullPointerException")
    void nullIdRejected() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new RiffChunk(null, 0L, 0L));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("id"),
                "Expected NPE message to name parameter 'id', got: " + ex.getMessage());
    }

    @Test
    @DisplayName("Negative size is rejected with a value-identifying IllegalArgumentException")
    void negativeSizeRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new RiffChunk("LIST", -1L, 12L));
        assertTrue(ex.getMessage().contains("size"),
                "Expected exception message to identify 'size', got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("-1"),
                "Expected exception message to include the offending value, got: "
                        + ex.getMessage());
    }

    @Test
    @DisplayName("Negative dataStartOffset is rejected with a value-identifying IllegalArgumentException")
    void negativeDataStartOffsetRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new RiffChunk("LIST", 0L, -5L));
        assertTrue(ex.getMessage().contains("dataStartOffset"),
                "Expected exception message to identify 'dataStartOffset', got: "
                        + ex.getMessage());
        assertTrue(ex.getMessage().contains("-5"),
                "Expected exception message to include the offending value, got: "
                        + ex.getMessage());
    }

    @Test
    @DisplayName("Zero size and zero offset are legal (empty chunks can exist)")
    void zeroSizeAndOffsetAccepted() {
        RiffChunk chunk = new RiffChunk("junk", 0L, 0L);
        assertEquals("junk", chunk.id());
        assertEquals(0L, chunk.size());
        assertEquals(0L, chunk.dataStartOffset());
    }

    @Test
    @DisplayName("Large size at 32-bit boundary is preserved as long (no sign flip)")
    void largeSizeAtInt32BoundaryPreserved() {
        long nearMaxU32 = 0xFFFF_FFFEL;
        RiffChunk chunk = new RiffChunk("data", nearMaxU32, 44L);
        assertEquals(nearMaxU32, chunk.size(),
                "size field must survive promotion to long without wrap-around");
    }

    @Test
    @DisplayName("Non-four-character ids are accepted (record imposes no length constraint)")
    void nonFourCharacterIdsAccepted() {
        // The parser always passes length-4 IDs via readAscii(4); the record
        // itself leaves the length unconstrained so tests can exercise the
        // surrounding logic without threading a RiffReader through. Pin that
        // permissiveness so a later tightening requires an explicit design
        // change.
        RiffChunk empty = new RiffChunk("", 0L, 0L);
        assertEquals(0, empty.id().length());

        RiffChunk longId = new RiffChunk("verbose_id", 0L, 0L);
        assertEquals("verbose_id", longId.id());
    }
}
