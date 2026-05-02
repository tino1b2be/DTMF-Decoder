package com.tino1b2be.dtmf;

import java.util.Objects;

import com.tino1b2be.dtmf.internal.FrequencyBins;

/**
 * DTMF tone generation: turn a key sequence into normalised {@code double}
 * PCM samples suitable for feeding back through
 * {@link DtmfDecoder#decode(double[], DtmfConfig)} or playing through an
 * audio output.
 *
 * <p>Per Requirements&nbsp;11.1&ndash;11.5, the generator:
 *
 * <ul>
 *   <li>accepts the sixteen DTMF keys {@code 0-9}, {@code A-D}, {@code *},
 *       {@code #} plus lowercase {@code a-d} (normalised to uppercase);</li>
 *   <li>produces each tone as
 *       {@code 0.5 * (sin(2π·lowHz·n/Fs) + sin(2π·highHz·n/Fs))} of length
 *       {@code N = round(minimumToneDuration.toSeconds() * sampleRate)};</li>
 *   <li>inserts {@code M = round(minimumGapDuration.toSeconds() * sampleRate)}
 *       samples of silence between consecutive tones, none after the final
 *       tone;</li>
 *   <li>rejects any character outside the accepted set with
 *       {@link IllegalArgumentException} naming the offending character and
 *       its index.</li>
 * </ul>
 *
 * <p>The {@code 0.5} amplitude keeps the combined peak at {@code 0.5}, leaving
 * 6&nbsp;dB of headroom so callers can apply gain without clipping.
 *
 * <p>Total output length for a sequence of {@code |s|} characters is
 * {@code |s| * N + max(0, |s| - 1) * M} samples. An empty sequence produces
 * an empty {@code double[]}.
 *
 * <p>The class is final with a private constructor; all entry points are
 * static.
 *
 * @since 2.0.0
 */
public final class DtmfGenerator {

    /** Accepted key alphabet (uppercase). Lowercase {@code a-d} is normalised. */
    private static final String ACCEPTED = "0123456789ABCD*#";

    private DtmfGenerator() { }

    /**
     * Generate a fresh {@code double[]} holding the PCM samples for
     * {@code sequence}.
     *
     * @param sequence key sequence; non-null. May be empty
     * @param config   configuration supplying sample rate, minimum tone
     *                 duration, and minimum gap duration; non-null
     * @return a newly allocated {@code double[]} of length
     *         {@code |sequence| * N + max(0, |sequence| - 1) * M}
     * @throws NullPointerException     if either argument is {@code null}
     * @throws IllegalArgumentException if {@code sequence} contains any
     *                                  character outside the accepted set
     */
    public static double[] generate(String sequence, DtmfConfig config) {
        Objects.requireNonNull(sequence, "sequence");
        Objects.requireNonNull(config, "config");

        int n = toneSamples(config);
        int m = gapSamples(config);
        int len = sequence.length();
        int total = totalLength(len, n, m);
        double[] out = new double[total];
        writeInto(sequence, config, out, 0, n, m);
        return out;
    }

    /**
     * Generate PCM samples for {@code sequence} into a caller-supplied buffer
     * starting at {@code offset}. Returns the number of samples written.
     *
     * <p>The caller is responsible for sizing {@code out} to hold the full
     * output: {@code offset + |sequence| * N + max(0, |sequence| - 1) * M}.
     *
     * @param sequence key sequence; non-null. May be empty
     * @param config   configuration supplying sample rate, minimum tone
     *                 duration, and minimum gap duration; non-null
     * @param out      destination buffer; non-null
     * @param offset   starting index into {@code out}; must be non-negative
     *                 and leave enough room for the full output
     * @return the number of samples written (equal to
     *         {@code |sequence| * N + max(0, |sequence| - 1) * M})
     * @throws NullPointerException      if any argument is {@code null}
     * @throws IllegalArgumentException  if {@code sequence} contains any
     *                                   character outside the accepted set,
     *                                   or if {@code offset < 0}
     * @throws IndexOutOfBoundsException if {@code out} is too small for the
     *                                   generated samples at the given offset
     */
    public static int generateInto(
            String sequence, DtmfConfig config, double[] out, int offset) {
        Objects.requireNonNull(sequence, "sequence");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(out, "out");
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0, was " + offset);
        }

        int n = toneSamples(config);
        int m = gapSamples(config);
        int total = totalLength(sequence.length(), n, m);
        Objects.checkFromIndexSize(offset, total, out.length);

        writeInto(sequence, config, out, offset, n, m);
        return total;
    }

    // ----- helpers -----

    /**
     * Validate the sequence and write its samples into {@code out}, starting
     * at {@code offset}. Validation is performed before any sample is
     * written, so a malformed sequence does not leave partial data in
     * {@code out}.
     */
    private static void writeInto(
            String sequence, DtmfConfig config, double[] out, int offset, int n, int m) {
        int len = sequence.length();
        // Validate every character first so we don't leave a partial buffer
        // behind if a malformed character is deep in the sequence.
        char[] normalised = new char[len];
        for (int i = 0; i < len; i++) {
            normalised[i] = normalise(sequence.charAt(i), i);
        }

        double fs = config.sampleRate();
        int pos = offset;
        for (int i = 0; i < len; i++) {
            double[] pair = FrequencyBins.frequenciesFor(normalised[i]);
            double low = pair[0];
            double high = pair[1];
            double omegaLow = 2.0 * Math.PI * low / fs;
            double omegaHigh = 2.0 * Math.PI * high / fs;
            for (int k = 0; k < n; k++) {
                out[pos + k] = 0.5 * (Math.sin(omegaLow * k) + Math.sin(omegaHigh * k));
            }
            pos += n;
            if (i < len - 1) {
                // Silence gap. out is already zero-initialised by Java for
                // fresh arrays; when generateInto is used on an existing
                // buffer we explicitly zero the gap so any pre-existing
                // contents do not leak through.
                for (int k = 0; k < m; k++) {
                    out[pos + k] = 0.0;
                }
                pos += m;
            }
        }
    }

    /**
     * Normalise a character to its uppercase DTMF form and validate.
     *
     * @throws IllegalArgumentException if {@code c} is not in the accepted set
     */
    private static char normalise(char c, int index) {
        char upper = c;
        if (c >= 'a' && c <= 'd') {
            upper = (char) (c - ('a' - 'A'));
        }
        if (ACCEPTED.indexOf(upper) < 0) {
            throw new IllegalArgumentException(
                    "sequence contains invalid character '" + c
                            + "' at index " + index
                            + "; accepted characters are " + ACCEPTED
                            + " (lowercase a-d also accepted)");
        }
        return upper;
    }

    private static int toneSamples(DtmfConfig config) {
        return (int) Math.round(
                config.minimumToneDuration().toNanos() / 1_000_000_000.0
                        * config.sampleRate());
    }

    private static int gapSamples(DtmfConfig config) {
        return (int) Math.round(
                config.minimumGapDuration().toNanos() / 1_000_000_000.0
                        * config.sampleRate());
    }

    private static int totalLength(int sequenceLength, int n, int m) {
        if (sequenceLength == 0) {
            return 0;
        }
        return sequenceLength * n + (sequenceLength - 1) * m;
    }
}
