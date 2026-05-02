package com.tino1b2be.dtmf.io.wav;

// Feature: dtmf-io, Property 12: WAV provider header detection

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for
 * {@link WavAudioSourceProvider#canOpen(Path) WavAudioSourceProvider.canOpen(Path)}
 * and
 * {@link WavAudioSourceProvider#canOpen(java.io.InputStream, String) WavAudioSourceProvider.canOpen(InputStream, String)}
 * header detection (Task 6.9).
 *
 * <p><strong>Property 12: WAV provider header
 * detection.</strong> <strong>Validates: Requirements 9.5, 9.6,
 * 4.6.</strong>
 *
 * <p>For any twelve-byte header, the provider's {@code canOpen} must
 * return the single score {@code 100} when, and only when, both of the
 * following are true:
 *
 * <ol>
 *   <li>Bytes {@code 0..3} are the ASCII sequence {@code "RIFF"} or
 *       {@code "RF64"} (Requirements 9.5, 9.6).</li>
 *   <li>Bytes {@code 8..11} are the ASCII sequence {@code "WAVE"}
 *       (Requirement 9.5).</li>
 * </ol>
 *
 * Otherwise the provider must return {@code -1}. Bytes {@code 4..7}
 * carry the outer RIFF size field and are <em>not</em> inspected by
 * {@code canOpen}; the property confirms this by varying them freely on
 * both the positive and negative paths.
 *
 * <p>The property exercises both {@code canOpen} overloads for every
 * generated header:
 *
 * <ul>
 *   <li>The {@link Path} overload writes the twelve bytes to a fresh
 *       temp file and delegates to
 *       {@link WavAudioSourceProvider#canOpen(Path)}. Extra bytes
 *       beyond the twelve-byte prefix are not needed because detection
 *       only inspects the magic prefix (Requirement 9.5).</li>
 *   <li>The {@link java.io.InputStream} overload wraps the twelve
 *       bytes in a {@link ByteArrayInputStream} &mdash; which supports
 *       {@code mark}/{@code reset}, so the provider's markable-stream
 *       branch is exercised (Requirement 4.6) &mdash; and additionally
 *       asserts the stream position is unchanged after the call: both
 *       {@link ByteArrayInputStream#available()} is identical before
 *       and after, and reading the first twelve bytes afterwards
 *       returns the original header byte-for-byte.</li>
 * </ul>
 *
 * <h2>Header generator design</h2>
 *
 * Truly uniform random twelve-byte headers hit the "match" case with
 * probability {@code (2 / 256^4) * (1 / 256^4) ≈ 10^{-19}}, which would
 * leave the positive branch effectively untested. The generator
 * therefore mixes four shapes so both branches receive weighted
 * coverage within the {@code tries = 100} budget:
 *
 * <ol>
 *   <li><strong>Fully random</strong> twelve bytes &mdash; covers the
 *       overwhelmingly common negative case, including short-prefix
 *       collisions like {@code "RIFZ"} and random byte patterns in
 *       byte positions {@code 0..3} and {@code 8..11}.</li>
 *   <li><strong>Valid RIFF/WAVE</strong> &mdash; bytes {@code 0..3} =
 *       {@code "RIFF"}, bytes {@code 8..11} = {@code "WAVE"},
 *       middle four bytes random.</li>
 *   <li><strong>Valid RF64/WAVE</strong> &mdash; bytes {@code 0..3} =
 *       {@code "RF64"}, bytes {@code 8..11} = {@code "WAVE"},
 *       middle four bytes random. Pins Requirement 9.6 (RF64 is
 *       treated identically to RIFF on the detection path).</li>
 *   <li><strong>Near-miss patterns</strong> &mdash; one of the four
 *       magic ASCII sequences is altered by exactly one byte, chosen
 *       uniformly at random. This exercises the boundary cases: the
 *       outer magic is correct but the form type is wrong (e.g.
 *       {@code "RIFFxxxx" + "WAVX"}), the form type is {@code "WAVE"}
 *       but the outer magic is wrong, and so on. These are the most
 *       likely real-world false-positive shapes.</li>
 * </ol>
 *
 * Each shape is independently weighted so the "true" and "false"
 * branches of {@code expectedMatch} each receive at least
 * {@code ~ 25%} of the try budget, and each fork through the parser
 * logic sees a range of otherwise-random bytes.
 *
 * <h2>Reference implementation</h2>
 *
 * The oracle is a six-line hand-decode ({@link #expectedMatch}) that
 * inspects bytes {@code 0..3} and {@code 8..11} directly without
 * calling into the provider &mdash; the property is asserting the
 * provider agrees with the specification as written in
 * Requirements 9.5 and 9.6, so the reference deliberately does not
 * share code with {@link WavAudioSourceProvider}.
 */
class WavHeaderDetectionPropertyTest {

    /** The one and only score {@code canOpen} is allowed to return on a match. */
    private static final int SCORE_MATCH = 100;

    /** The one and only score {@code canOpen} is allowed to return on a miss. */
    private static final int SCORE_MISS = -1;

    /** Length of the header slice {@code canOpen} inspects. */
    private static final int HEADER_BYTES = 12;

    // ------------------------------------------------------------------
    // The property
    // ------------------------------------------------------------------

    /**
     * For any generated twelve-byte header, both {@code canOpen}
     * overloads return {@code 100} iff the magic-byte pattern matches,
     * and {@code -1} otherwise. The stream overload additionally
     * guarantees the caller's stream position is unchanged.
     */
    @Property(tries = 100)
    void canOpenReturns100WhenMagicMatchesMinusOneOtherwise(
            @ForAll("headers") byte[] header) throws IOException {

        // Generator invariant: always exactly 12 bytes.
        assertEquals(HEADER_BYTES, header.length,
                "Generator precondition: headers must be exactly "
                        + HEADER_BYTES + " bytes");

        int expected = expectedMatch(header) ? SCORE_MATCH : SCORE_MISS;

        // ------------------------------------------------------------------
        // Overload 1: canOpen(Path)
        // ------------------------------------------------------------------
        Path tmp = Files.createTempFile("wav-header-prop-", ".bin");
        try {
            Files.write(tmp, header);
            WavAudioSourceProvider provider = new WavAudioSourceProvider();
            int actual = provider.canOpen(tmp);
            assertEquals(expected, actual,
                    () -> "canOpen(Path) must return " + expected + " for header "
                            + hex(header) + "; expectedMatch="
                            + expectedMatch(header));
        } finally {
            Files.deleteIfExists(tmp);
        }

        // ------------------------------------------------------------------
        // Overload 2: canOpen(InputStream, String)
        // Also: the stream's position must be unchanged after the call
        // (Requirement 4.6).
        // ------------------------------------------------------------------
        // Note: wrap exactly the 12-byte header. Reading back the first
        // 12 bytes after canOpen should return the original bytes, which
        // proves reset() restored the position to 0.
        ByteArrayInputStream stream = new ByteArrayInputStream(header);
        int availableBefore = stream.available();
        assertEquals(HEADER_BYTES, availableBefore,
                "ByteArrayInputStream.available() on a 12-byte buffer must be 12");

        WavAudioSourceProvider provider = new WavAudioSourceProvider();
        int actualStream = provider.canOpen(stream, /* hint */ null);
        assertEquals(expected, actualStream,
                () -> "canOpen(InputStream, String) must return " + expected
                        + " for header " + hex(header) + "; expectedMatch="
                        + expectedMatch(header));

        int availableAfter = stream.available();
        assertEquals(availableBefore, availableAfter,
                () -> "Stream position must be unchanged after canOpen "
                        + "(Req 4.6); available before=" + availableBefore
                        + ", after=" + availableAfter + ", header="
                        + hex(header));

        // Strongest cross-check: reading the first 12 bytes afterwards
        // must return the original header byte-for-byte. This rules out
        // the pathological case where a buggy reset somehow left
        // available() equal but changed the underlying position.
        byte[] readBack = stream.readNBytes(HEADER_BYTES);
        assertArrayEquals(header, readBack,
                () -> "Reading 12 bytes after canOpen must return the original "
                        + "header byte-for-byte (Req 4.6); expected="
                        + hex(header) + ", got=" + hex(readBack));
    }

    // ------------------------------------------------------------------
    // Reference implementation
    // ------------------------------------------------------------------

    /**
     * Hand-computed reference predicate for Requirements 9.5 and 9.6.
     * Returns {@code true} iff bytes {@code 0..3} are the ASCII
     * sequence {@code "RIFF"} or {@code "RF64"} and bytes
     * {@code 8..11} are {@code "WAVE"}. Deliberately implemented
     * inline without calling into {@link WavAudioSourceProvider} so the
     * property pins the provider against the specification directly.
     */
    private static boolean expectedMatch(byte[] header) {
        boolean outerOk = matchesAscii(header, 0, "RIFF")
                || matchesAscii(header, 0, "RF64");
        boolean waveOk = matchesAscii(header, 8, "WAVE");
        return outerOk && waveOk;
    }

    /** Returns {@code true} iff {@code buf[offset..offset+s.length)} equals {@code s}. */
    private static boolean matchesAscii(byte[] buf, int offset, String s) {
        if (offset + s.length() > buf.length) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (buf[offset + i] != (byte) s.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /** Render a byte[] as a dashed hex string for assertion messages. */
    private static String hex(byte[] buf) {
        StringBuilder sb = new StringBuilder(buf.length * 3);
        for (int i = 0; i < buf.length; i++) {
            if (i > 0) {
                sb.append('-');
            }
            sb.append(String.format("%02X", buf[i] & 0xFF));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Generators
    // ------------------------------------------------------------------

    /**
     * Twelve-byte header generator. Mixes four shapes so both positive
     * and negative branches of {@code expectedMatch} receive meaningful
     * coverage within {@code tries = 100}. See class Javadoc for the
     * rationale.
     */
    @Provide
    Arbitrary<byte[]> headers() {
        return Arbitraries.frequencyOf(
                // 40%: fully random 12 bytes — overwhelmingly the
                // negative case.
                net.jqwik.api.Tuple.of(40, fullyRandom()),
                // 20%: valid RIFF/WAVE header — positive case for the
                // RIFF magic.
                net.jqwik.api.Tuple.of(20, validRiff()),
                // 20%: valid RF64/WAVE header — positive case for the
                // RF64 magic (Requirement 9.6).
                net.jqwik.api.Tuple.of(20, validRf64()),
                // 20%: one-byte-off near-miss — covers the boundary
                // cases like "RIFG" + "WAVE" and "RIFF" + "WAVX".
                net.jqwik.api.Tuple.of(20, nearMiss())
        );
    }

    /** Uniformly random 12 bytes. */
    private Arbitrary<byte[]> fullyRandom() {
        return Arbitraries.bytes().array(byte[].class).ofSize(HEADER_BYTES);
    }

    /** {@code "RIFF" + <random 4 bytes> + "WAVE"} — always a match. */
    private Arbitrary<byte[]> validRiff() {
        return middleFourBytes()
                .map(middle -> header("RIFF", middle, "WAVE"));
    }

    /** {@code "RF64" + <random 4 bytes> + "WAVE"} — always a match. */
    private Arbitrary<byte[]> validRf64() {
        return middleFourBytes()
                .map(middle -> header("RF64", middle, "WAVE"));
    }

    /**
     * Build a header that is one byte away from a valid RIFF/WAVE or
     * RF64/WAVE layout. Two arms: either the outer magic is correct
     * but the form-type has a single byte off (so bytes {@code 8..11}
     * are not {@code "WAVE"}), or the form type is {@code "WAVE"} but
     * the outer magic has a single byte off. Both arms produce
     * headers that look <em>almost</em> like WAVs and must still be
     * rejected.
     */
    private Arbitrary<byte[]> nearMiss() {
        Arbitrary<byte[]> outerCorrectFormWrong = Combinators.combine(
                Arbitraries.of("RIFF", "RF64"),
                middleFourBytes(),
                mutatedAscii("WAVE")
        ).as((outer, middle, form) -> header(outer, middle, form));

        Arbitrary<byte[]> outerWrongFormCorrect = Combinators.combine(
                mutatedAscii("RIFF"),    // deliberately mutate from RIFF;
                                         // RF64 near-misses are covered by
                                         // random bytes in the other arm.
                middleFourBytes(),
                Arbitraries.just("WAVE")
        ).as((outer, middle, form) -> header(outer, middle, form));

        return Arbitraries.oneOf(outerCorrectFormWrong, outerWrongFormCorrect);
    }

    /** Random four bytes for the outer-size field position (bytes 4..7). */
    private Arbitrary<byte[]> middleFourBytes() {
        return Arbitraries.bytes().array(byte[].class).ofSize(4);
    }

    /**
     * Return an {@link Arbitrary} that mutates the given 4-character
     * ASCII string by flipping exactly one byte to a value that
     * differs from the original at that position. The result is
     * guaranteed not to equal {@code original}.
     */
    private Arbitrary<String> mutatedAscii(String original) {
        // Index of the byte to mutate.
        Arbitrary<Integer> index = Arbitraries.integers().between(0, original.length() - 1);
        // Replacement byte — any byte value (the provider compares raw
        // bytes, not characters, so ASCII-printability is irrelevant).
        Arbitrary<Byte> replacement = Arbitraries.bytes();
        return Combinators.combine(index, replacement).as((idx, rep) -> {
            byte[] out = original.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            byte originalByte = out[idx];
            // Guarantee mutation: if the draw happens to match the
            // original byte at that position, XOR by 1 to force a
            // difference without introducing bias toward any particular
            // replacement value.
            if (rep == originalByte) {
                out[idx] = (byte) (originalByte ^ 0x01);
            } else {
                out[idx] = rep;
            }
            return new String(out, java.nio.charset.StandardCharsets.ISO_8859_1);
        });
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    /**
     * Assemble a twelve-byte header from a 4-byte outer magic string,
     * four middle bytes, and a 4-byte form-type string. The strings
     * are interpreted as ISO-8859-1 so every {@code char} maps to
     * exactly one byte.
     */
    private static byte[] header(String outer, byte[] middle, String form) {
        if (outer.length() != 4 || form.length() != 4 || middle.length != 4) {
            throw new AssertionError("Header components must be 4 bytes each; "
                    + "outer=" + outer.length() + ", middle=" + middle.length
                    + ", form=" + form.length());
        }
        byte[] out = new byte[HEADER_BYTES];
        for (int i = 0; i < 4; i++) {
            out[i] = (byte) outer.charAt(i);
        }
        System.arraycopy(middle, 0, out, 4, 4);
        for (int i = 0; i < 4; i++) {
            out[8 + i] = (byte) form.charAt(i);
        }
        return out;
    }
}
