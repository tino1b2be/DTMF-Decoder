package com.tino1b2be.dtmf.bench;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import com.tino1b2be.dtmf.DtmfConfig;
import com.tino1b2be.dtmf.DtmfGenerator;

/**
 * Optional FFT-based comparison benchmark (Requirements&nbsp;10.3, 15.4).
 *
 * <p>The production detector uses Goertzel (Requirement&nbsp;10.1); this
 * benchmark exists solely so the reader can see how a naive FFT pipeline
 * compares when asked to extract the same eight DTMF bin magnitudes from
 * the same audio payloads as {@link DtmfDecoderBenchmark}.
 *
 * <p>Pipeline per iteration (for each sample rate):
 *
 * <ol>
 *   <li>Pad or truncate the 1-second DTMF audio to the next power-of-two
 *       length (8192 at 8&nbsp;kHz, 16384 at 16&nbsp;kHz, 32768 at 44.1&nbsp;kHz,
 *       65536 at 48&nbsp;kHz).</li>
 *   <li>Run {@link FastFourierTransformer#transform(double[], TransformType)}
 *       to get the complex spectrum.</li>
 *   <li>For each of the eight DTMF frequencies, compute the closest bin
 *       index and read its magnitude.</li>
 *   <li>Run an {@code argmax} over the four low-group bins and the four
 *       high-group bins to pick a DTMF key.</li>
 * </ol>
 *
 * <p>This is a deliberately simple FFT-detector stand-in — no windowing,
 * no block-level state machine, no twist check. Its only purpose is to
 * give a visible data point for "what would it cost to replace Goertzel
 * with an off-the-shelf FFT just for magnitude extraction?" Informational
 * only; there is no pass/fail gate.
 *
 * <p>Methodology: {@link Mode#AverageTime} with
 * {@link TimeUnit#MICROSECONDS} so the output units match
 * {@link DtmfDecoderBenchmark}.
 *
 * @since 2.0.0
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class FftComparisonBenchmark {

    /** Short repeating DTMF sequence matched to {@link DtmfDecoderBenchmark}. */
    private static final String SEQUENCE = "0123456789ABCD*#";

    /** Tone duration used for the pre-generated corpus. */
    private static final Duration TONE = Duration.ofMillis(40);

    /** Gap duration used for the pre-generated corpus. */
    private static final Duration GAP = Duration.ofMillis(40);

    /** Low-group DTMF frequencies (Hz). */
    private static final double[] LOW_GROUP = {697.0, 770.0, 852.0, 941.0};

    /** High-group DTMF frequencies (Hz). */
    private static final double[] HIGH_GROUP = {1209.0, 1336.0, 1477.0, 1633.0};

    /** 4&times;4 DTMF key matrix indexed by {@code (lowIndex, highIndex)}. */
    private static final char[][] KEY_MATRIX = {
            {'1', '2', '3', 'A'},
            {'4', '5', '6', 'B'},
            {'7', '8', '9', 'C'},
            {'*', '0', '#', 'D'}
    };

    private FastFourierTransformer fft;

    private int rate8k;
    private int rate16k;
    private int rate44k;
    private int rate48k;

    private double[] audio8k;
    private double[] audio16k;
    private double[] audio44k;
    private double[] audio48k;

    /**
     * Generate a ~1-second DTMF corpus at each Supported_Sample_Rate, then
     * pad each buffer up to the next power of two so the FFT library
     * accepts it.
     */
    @Setup
    public void setup() {
        fft = new FastFourierTransformer(DftNormalization.STANDARD);

        rate8k = 8000;
        rate16k = 16000;
        rate44k = 44100;
        rate48k = 48000;

        audio8k = padToPow2(DtmfGenerator.generate(SEQUENCE, configFor(rate8k)));
        audio16k = padToPow2(DtmfGenerator.generate(SEQUENCE, configFor(rate16k)));
        audio44k = padToPow2(DtmfGenerator.generate(SEQUENCE, configFor(rate44k)));
        audio48k = padToPow2(DtmfGenerator.generate(SEQUENCE, configFor(rate48k)));
    }

    private static DtmfConfig configFor(int sampleRate) {
        return DtmfConfig.advanced()
                .sampleRate(sampleRate)
                .minimumToneDuration(TONE)
                .minimumGapDuration(GAP)
                .build();
    }

    /**
     * Return {@code src} padded with trailing zeros to the next power of
     * two (or truncated to the previous power of two — neither case comes
     * up in practice here because the 1-second-ish buffer always lands
     * between consecutive powers of two). Exposed package-private for
     * future unit testing; never called outside {@link #setup()} in
     * benchmark runs.
     */
    static double[] padToPow2(double[] src) {
        int target = Integer.highestOneBit(src.length);
        if (target < src.length) {
            target <<= 1; // round up
        }
        if (target == src.length) {
            return src;
        }
        double[] padded = new double[target];
        System.arraycopy(src, 0, padded, 0, src.length);
        return padded;
    }

    /** 8&nbsp;kHz pipeline: FFT the padded buffer, pick a DTMF key. */
    @Benchmark
    public void fftDecode8k(Blackhole bh) {
        bh.consume(fftDecode(audio8k, rate8k));
    }

    /** 16&nbsp;kHz pipeline: FFT the padded buffer, pick a DTMF key. */
    @Benchmark
    public void fftDecode16k(Blackhole bh) {
        bh.consume(fftDecode(audio16k, rate16k));
    }

    /** 44.1&nbsp;kHz pipeline: FFT the padded buffer, pick a DTMF key. */
    @Benchmark
    public void fftDecode44k(Blackhole bh) {
        bh.consume(fftDecode(audio44k, rate44k));
    }

    /** 48&nbsp;kHz pipeline: FFT the padded buffer, pick a DTMF key. */
    @Benchmark
    public void fftDecode48k(Blackhole bh) {
        bh.consume(fftDecode(audio48k, rate48k));
    }

    /**
     * Run the shared FFT pipeline: forward transform, read magnitudes at the
     * eight DTMF bin indices, pick the DTMF key from the strongest low- and
     * high-group bins.
     */
    private char fftDecode(double[] audio, int sampleRate) {
        Complex[] spectrum = fft.transform(audio, TransformType.FORWARD);
        int n = spectrum.length;

        int lowIdx = argmaxBin(spectrum, n, sampleRate, LOW_GROUP);
        int highIdx = argmaxBin(spectrum, n, sampleRate, HIGH_GROUP);
        return KEY_MATRIX[lowIdx][highIdx];
    }

    /**
     * Scan the {@code frequencies} set against {@code spectrum} and return
     * the index (into {@code frequencies}) whose closest FFT bin has the
     * largest magnitude.
     */
    private static int argmaxBin(
            Complex[] spectrum, int n, int sampleRate, double[] frequencies) {
        double bestMag = Double.NEGATIVE_INFINITY;
        int bestIdx = 0;
        double binHz = (double) sampleRate / n;
        for (int i = 0; i < frequencies.length; i++) {
            int bin = (int) Math.round(frequencies[i] / binHz);
            if (bin < 0) {
                bin = 0;
            } else if (bin >= n) {
                bin = n - 1;
            }
            Complex c = spectrum[bin];
            // Skip sqrt: the argmax over magnitude equals the argmax over
            // magnitude-squared, and avoiding the sqrt shaves a few percent
            // off an already-cheap inner loop.
            double mag = c.getReal() * c.getReal() + c.getImaginary() * c.getImaginary();
            if (mag > bestMag) {
                bestMag = mag;
                bestIdx = i;
            }
        }
        return bestIdx;
    }
}
