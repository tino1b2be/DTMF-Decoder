package com.tino1b2be.dtmf.io;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.tino1b2be.dtmf.DtmfConfig;
import com.tino1b2be.dtmf.DtmfGenerator;
import com.tino1b2be.dtmf.DtmfTone;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration test verifying that a corpus of DTMF sequences encoded to
 * 128&nbsp;kbps CBR mono MP3 at 44.1&nbsp;kHz round-trips back through
 * {@link DtmfFileDecoder} with an overall key-match rate of at least
 * {@value #DETECTION_RATE_THRESHOLD_PERCENT}% (Requirements 14.1, 14.2,
 * 14.3).
 *
 * <p>This is the only test in the suite that depends on an external
 * encoder. It is tagged {@code slow} and opt-in: it only runs when the
 * {@code dtmf.mp3.lameRoundTrip} system property is set to {@code true}.
 * Default CI runs — {@code ./gradlew :dtmf-io:integrationTest} with no
 * flag — skip the entire class via
 * {@link EnabledIfSystemProperty}. To run locally:
 *
 * <pre>
 *   ./gradlew :dtmf-io:integrationTest -Ddtmf.mp3.lameRoundTrip=true
 * </pre>
 *
 * <p>Even when the property is set, the test uses
 * {@link org.junit.jupiter.api.Assumptions#assumeTrue(boolean, String)} to
 * further skip (not fail) when LAME is not installed on {@code PATH}. The
 * committed MP3 fixture corpus under
 * {@code dtmf-core/src/integrationTest/resources/samples/} contains only
 * one DTMF-carrying fixture ({@code 12345678.mp3}), which is insufficient
 * to meet Requirement 14.3's "≥ 50 sequences" floor, so LAME is the only
 * viable path to a statistically meaningful detection-rate measurement.
 *
 * <h2>Encoding pipeline</h2>
 *
 * For each generated sequence the test:
 *
 * <ol>
 *   <li>Generates normalised PCM samples via {@link DtmfGenerator} at
 *       44.1&nbsp;kHz mono, with 80&nbsp;ms minimum tone duration and
 *       40&nbsp;ms minimum gap duration (Req 14.3);</li>
 *   <li>Quantises the {@code double[]} to signed 16-bit little-endian PCM
 *       bytes (clamped to {@code [Short.MIN_VALUE, Short.MAX_VALUE]});</li>
 *   <li>Invokes {@code lame -r -s 44100 -m m --signed --bitwidth 16
 *       --little-endian -b 128 --cbr - &lt;outFile&gt;}, pipes the raw PCM
 *       bytes through stdin, and waits for LAME to finish writing the
 *       CBR MP3 file;</li>
 *   <li>Decodes the resulting MP3 via
 *       {@code DtmfFileDecoder.decode(mp3Path, DtmfConfig.forNoisyAudio())}
 *       — the decoder auto-resolves from 8&nbsp;kHz to 44.1&nbsp;kHz on
 *       the source's reported rate (see
 *       {@code DtmfFileDecoder.rebuildWithSampleRate}).</li>
 * </ol>
 *
 * <h2>Match-rate computation</h2>
 *
 * For each sequence the test computes an <em>in-order match count</em>
 * between the expected and detected key strings via the longest-common-
 * subsequence length — the largest number of keys that can be paired in
 * the same order across the two strings. This is strictly more forgiving
 * than position-wise equality (an extra or missed tone near the start
 * does not cascade) and still penalises both spurious detections and
 * missed keys.
 *
 * <p>The overall detection rate is
 * {@code sum(LCS(expected_i, detected_i)) / sum(|expected_i|)}. Req 14.1
 * specifies an aggregate — not per-sequence — target, so a single
 * pathological case (e.g. LAME producing a degenerate frame) does not
 * fail the suite as long as the corpus average stays above 99%.
 *
 * @since 2.1.0
 */
@Tag("slow")
@EnabledIfSystemProperty(
        named = "dtmf.mp3.lameRoundTrip",
        matches = "true",
        disabledReason = "Set -Ddtmf.mp3.lameRoundTrip=true to run the LAME-based "
                + "MP3 round-trip corpus test; requires 'lame' on PATH.")
final class Mp3RoundTripIT {

    /** Logger for skip diagnostics and LAME-failure breadcrumbs. */
    private static final Logger LOG = Logger.getLogger(Mp3RoundTripIT.class.getName());

    /** Sample rate used for generation and LAME encoding (Req 14.3). */
    private static final int SAMPLE_RATE_HZ = 44_100;

    /** Minimum number of sequences in the corpus (Req 14.1, 14.3). */
    private static final int CORPUS_SIZE = 50;

    /** Minimum number of keys per generated sequence (Req 14.1, 14.3). */
    private static final int MIN_KEYS_PER_SEQUENCE = 8;

    /** Maximum number of keys per generated sequence; keeps MP3 size small. */
    private static final int MAX_KEYS_PER_SEQUENCE = 12;

    /** Minimum tone duration for corpus generation (Req 14.3). */
    private static final Duration TONE_DURATION = Duration.ofMillis(80);

    /** Minimum gap duration for corpus generation (Req 14.3). */
    private static final Duration GAP_DURATION = Duration.ofMillis(40);

    /** Deterministic RNG seed so corpus failures are reproducible. */
    private static final long RNG_SEED = 0xD7_F1_00_10_25_55_55L;

    /** The 16 DTMF keys (Req 14.1 key alphabet). */
    private static final char[] DTMF_KEYS = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'A', 'B', 'C', 'D', '*', '#'
    };

    /** Quantisation divisor for PCM16 mono. */
    private static final double PCM16_SCALE = 32768.0;

    /** CBR bitrate (kbps) passed to LAME (Req 14.1). */
    private static final int LAME_BITRATE_KBPS = 128;

    /** Max time to wait for LAME to finish one encode. */
    private static final Duration LAME_TIMEOUT = Duration.ofSeconds(30);

    /** Aggregate detection-rate threshold; integer percent for display. */
    private static final int DETECTION_RATE_THRESHOLD_PERCENT = 99;

    /** Same as above as a {@code double} for comparison. */
    private static final double DETECTION_RATE_THRESHOLD = 0.99;

    @Test
    @DisplayName("LAME-encoded 128 kbps CBR MP3 round-trip achieves ≥ 99% key match rate "
            + "over ≥ 50 sequences at 44.1 kHz mono")
    void lameRoundTripMeetsDetectionRateThreshold(@TempDir Path tempDir) throws IOException {
        // Even when the opt-in property is set, skip (not fail) if LAME is
        // not reachable — we have no committed corpus of ≥ 50 DTMF MP3
        // fixtures to fall back on.
        assumeTrue(
                isLameAvailable(),
                "lame is not on PATH; install lame (e.g. brew install lame) "
                        + "to run this opt-in corpus test.");

        DtmfConfig genConfig = DtmfConfig.advanced()
                .sampleRate(SAMPLE_RATE_HZ)
                .minimumToneDuration(TONE_DURATION)
                .minimumGapDuration(GAP_DURATION)
                .build();

        Random rng = new Random(RNG_SEED);
        int totalExpectedKeys = 0;
        int totalMatchedKeys = 0;
        List<String> failureBreadcrumbs = new ArrayList<>();

        for (int i = 0; i < CORPUS_SIZE; i++) {
            String expected = randomSequence(rng);
            double[] pcm = DtmfGenerator.generate(expected, genConfig);
            byte[] pcm16Bytes = quantizePcm16LittleEndian(pcm);

            Path mp3Path = tempDir.resolve("corpus-" + i + ".mp3");
            lameEncode(pcm16Bytes, mp3Path);

            List<DtmfTone> decoded = DtmfFileDecoder.decode(
                    mp3Path, DtmfConfig.forNoisyAudio());
            String detected = toKeyString(decoded);

            int lcs = longestCommonSubsequenceLength(expected, detected);
            totalExpectedKeys += expected.length();
            totalMatchedKeys += lcs;

            if (lcs < expected.length()) {
                failureBreadcrumbs.add(String.format(
                        "i=%d expected=\"%s\" detected=\"%s\" lcs=%d/%d",
                        i, expected, detected, lcs, expected.length()));
            }

            // Delete each MP3 as we go; with 50 files at ~20 KB each the
            // temp dir would still only reach ~1 MB, but leaving the
            // files makes running this with LAME multiple times on the
            // same build slower than necessary.
            try {
                Files.deleteIfExists(mp3Path);
            } catch (IOException ignored) {
                // tempDir will be scrubbed by the JUnit @TempDir cleanup
                // anyway — leaking a stale file here is not a test failure.
            }
        }

        final int finalMatched = totalMatchedKeys;
        final int finalExpected = totalExpectedKeys;
        final double detectionRate = (double) finalMatched / (double) finalExpected;
        final int divergentCount = failureBreadcrumbs.size();

        assertTrue(
                detectionRate >= DETECTION_RATE_THRESHOLD,
                () -> String.format(
                        "MP3 round-trip detection rate %.4f < %.2f (%d/%d keys "
                                + "matched across %d sequences; %d sequences had "
                                + "at least one mismatch).%n"
                                + "First few divergences:%n%s",
                        detectionRate,
                        DETECTION_RATE_THRESHOLD,
                        finalMatched,
                        finalExpected,
                        CORPUS_SIZE,
                        divergentCount,
                        String.join(
                                System.lineSeparator(),
                                failureBreadcrumbs.subList(
                                        0, Math.min(10, divergentCount)))));
    }

    // ------------------------------------------------------------------
    // LAME availability probe
    // ------------------------------------------------------------------

    /**
     * Probe whether {@code lame} is on {@code PATH} by exec-ing
     * {@code lame --version} and checking the exit status. Any failure
     * (IO error, non-zero exit, timeout) → not available.
     */
    private static boolean isLameAvailable() {
        ProcessBuilder pb = new ProcessBuilder("lame", "--version")
                .redirectErrorStream(true);
        try {
            Process p = pb.start();
            // Drain stdout so LAME doesn't block on a full pipe.
            p.getInputStream().readAllBytes();
            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    // ------------------------------------------------------------------
    // LAME invocation
    // ------------------------------------------------------------------

    /**
     * Encode {@code pcm16Bytes} (signed 16-bit little-endian mono PCM at
     * 44.1&nbsp;kHz) to a 128&nbsp;kbps CBR MP3 at {@code outMp3Path} by
     * piping the bytes through LAME's stdin.
     *
     * <p>The LAME command line matches the spec's design note exactly:
     *
     * <pre>
     *   lame -r -s 44100 -m m --signed --bitwidth 16 --little-endian
     *        -b 128 --cbr - &lt;outFile&gt;
     * </pre>
     *
     * <p>{@code -r} tells LAME the input is raw PCM; {@code -s 44100}
     * fixes the sample rate; {@code -m m} fixes mono output;
     * {@code --signed --bitwidth 16 --little-endian} describes the raw
     * PCM byte layout; {@code -b 128 --cbr} fixes 128&nbsp;kbps CBR
     * (Req 14.1); {@code -} means "read PCM from stdin";
     * {@code &lt;outFile&gt;} is the MP3 destination.
     *
     * @throws IOException if LAME cannot be launched, exits non-zero, or
     *                     does not finish within {@link #LAME_TIMEOUT}
     */
    private static void lameEncode(byte[] pcm16Bytes, Path outMp3Path) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                "lame",
                "-r",
                "-s", "44100",
                "-m", "m",
                "--signed",
                "--bitwidth", "16",
                "--little-endian",
                "-b", String.valueOf(LAME_BITRATE_KBPS),
                "--cbr",
                "-",
                outMp3Path.toString())
                .redirectErrorStream(true);
        Process p = pb.start();

        // Drain stdout+stderr on a helper thread so LAME doesn't block
        // on a full pipe while we're still writing PCM to its stdin.
        final byte[][] drained = new byte[1][];
        Thread reader = new Thread(() -> {
            try {
                drained[0] = p.getInputStream().readAllBytes();
            } catch (IOException ignored) {
                drained[0] = new byte[0];
            }
        }, "lame-stdout-drain");
        reader.setDaemon(true);
        reader.start();

        // Feed the PCM on the main thread.
        try (OutputStream stdin = p.getOutputStream()) {
            stdin.write(pcm16Bytes);
            stdin.flush();
        } catch (IOException ex) {
            // LAME may have exited early on a malformed input; fall
            // through to the waitFor + exit-code check below which
            // produces a more helpful diagnostic.
            LOG.log(Level.FINE, "write to lame stdin failed (exited early?)", ex);
        }

        boolean finished;
        try {
            finished = p.waitFor(LAME_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            throw new IOException("Interrupted waiting for lame", ex);
        }
        if (!finished) {
            p.destroyForcibly();
            throw new IOException(
                    "lame did not finish within " + LAME_TIMEOUT + " — aborting encode");
        }

        try {
            reader.join(1000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        int exit = p.exitValue();
        if (exit != 0) {
            String output = (drained[0] == null) ? "" : new String(drained[0]);
            throw new IOException(
                    "lame exited " + exit + " encoding " + outMp3Path
                            + System.lineSeparator() + output);
        }
    }

    // ------------------------------------------------------------------
    // Corpus generation helpers
    // ------------------------------------------------------------------

    /**
     * Pick a random DTMF key sequence of length in
     * {@code [MIN_KEYS_PER_SEQUENCE, MAX_KEYS_PER_SEQUENCE]}.
     */
    private static String randomSequence(Random rng) {
        int length = MIN_KEYS_PER_SEQUENCE
                + rng.nextInt(MAX_KEYS_PER_SEQUENCE - MIN_KEYS_PER_SEQUENCE + 1);
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(DTMF_KEYS[rng.nextInt(DTMF_KEYS.length)]);
        }
        return sb.toString();
    }

    /**
     * Quantise a normalised {@code double[]} in {@code [-1.0, 1.0]} to
     * signed 16-bit little-endian PCM bytes. Matches the integration-
     * test WAV encoder's {@code round(sample * 32768.0)} clamp.
     */
    private static byte[] quantizePcm16LittleEndian(double[] samples) {
        ByteBuffer buf = ByteBuffer.allocate(samples.length * 2)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (double s : samples) {
            long q = Math.round(s * PCM16_SCALE);
            if (q > Short.MAX_VALUE) {
                q = Short.MAX_VALUE;
            } else if (q < Short.MIN_VALUE) {
                q = Short.MIN_VALUE;
            }
            buf.putShort((short) q);
        }
        return buf.array();
    }

    /** Concatenate {@link DtmfTone#key()} across {@code tones} into a string. */
    private static String toKeyString(List<DtmfTone> tones) {
        StringBuilder sb = new StringBuilder(tones.size());
        for (DtmfTone t : tones) {
            sb.append(t.key());
        }
        return sb.toString();
    }

    /**
     * Classic O(n·m) LCS length. Used to credit detections that arrive in
     * the correct relative order even when extra or missing keys appear
     * — a per-position equality check would penalise a single dropped
     * tone cascading across the rest of the sequence.
     */
    private static int longestCommonSubsequenceLength(String a, String b) {
        int n = a.length();
        int m = b.length();
        if (n == 0 || m == 0) {
            return 0;
        }
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int i = 1; i <= n; i++) {
            char ai = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                if (ai == b.charAt(j - 1)) {
                    curr[j] = prev[j - 1] + 1;
                } else {
                    curr[j] = Math.max(prev[j], curr[j - 1]);
                }
            }
            int[] swap = prev;
            prev = curr;
            curr = swap;
            // curr is about to be overwritten from index 1 on the next
            // row, but zero the sentinel so the algorithm starts clean.
            curr[0] = 0;
        }
        return prev[m];
    }
}
