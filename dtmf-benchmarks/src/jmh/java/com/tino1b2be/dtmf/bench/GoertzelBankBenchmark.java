package com.tino1b2be.dtmf.bench;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import com.tino1b2be.goertzel.GoertzelBank;

/**
 * JMH benchmark measuring raw {@link GoertzelBank} throughput for an
 * 8-filter bank at the DTMF frequencies (Requirement&nbsp;15.3).
 *
 * <p>The bank size is fixed at 8 (the eight DTMF frequencies); the
 * analysis-block size is parameterized over {@code {160, 320, 882, 960}},
 * which are exactly the values {@code BlockSizer.blockSizeFor} returns for
 * the four Supported_Sample_Rates {@code (8000, 16000, 44100, 48000)} Hz.
 * Running the same filter shape across the realistic block lengths lets the
 * reader see how per-block cost scales with {@code N}.
 *
 * <p>Methodology:
 * <ul>
 *   <li>{@link Mode#Throughput} with {@link TimeUnit#SECONDS} output — we
 *       want "operations per second" semantics since the block is a
 *       constant-cost unit of work and what varies between rows is
 *       per-block cost.</li>
 *   <li>{@link State} at {@link Scope#BENCHMARK} scope — the signal buffer
 *       and output array are read-only / write-only per invocation so
 *       sharing one instance is fine.</li>
 *   <li>The signal is deterministic noise from
 *       {@code new Random(0xDEADBEEFL).nextDouble()} shifted into
 *       {@code [-1, 1]}. Any predictable wave form would let the JIT
 *       constant-fold Goertzel coefficients; deterministic noise avoids
 *       that without introducing per-iteration randomness.</li>
 * </ul>
 *
 * <p>The {@code sampleRate} passed to {@code GoertzelBank} is held constant
 * at 48&nbsp;kHz because the parameter that matters for Goertzel per-block
 * cost is {@code blockSize}, not the rate-frequency pairing. The DTMF
 * frequencies themselves are constant; using a single rate across all rows
 * keeps the comparison clean.
 *
 * @since 2.0.0
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class GoertzelBankBenchmark {

    /** Eight DTMF frequencies. */
    private static final double[] DTMF_FREQUENCIES = {
            697.0, 770.0, 852.0, 941.0,
            1209.0, 1336.0, 1477.0, 1633.0
    };

    /** Sample rate used to build the bank. Held constant so {@code blockSize} is the lone axis. */
    private static final int SAMPLE_RATE = 48_000;

    /**
     * Block size axis. 160/320/882/960 are the values
     * {@code BlockSizer.blockSizeFor} returns for 8/16/44.1/48&nbsp;kHz, i.e.
     * the production-realistic range.
     */
    @Param({"160", "320", "882", "960"})
    public int blockSize;

    private GoertzelBank bank;
    private double[] signal;
    private double[] magnitudes;

    /**
     * Allocate the 8-filter bank, fill the input buffer with deterministic
     * noise, and preallocate the output magnitudes array. The setup runs
     * once per trial (once per {@code @Param} combination), keeping the
     * inner loop focused on {@code computeMagnitudesSquaredInto}.
     */
    @Setup
    public void setup() {
        bank = new GoertzelBank(SAMPLE_RATE, DTMF_FREQUENCIES);
        signal = new double[blockSize];
        Random rng = new Random(0xDEADBEEFL);
        for (int i = 0; i < blockSize; i++) {
            // Uniform in [-1, 1]. Deterministic across runs.
            signal[i] = rng.nextDouble() * 2.0 - 1.0;
        }
        magnitudes = new double[DTMF_FREQUENCIES.length];
    }

    /**
     * Measure throughput of one full batch evaluation: reset the bank, feed
     * {@code blockSize} samples, read the eight magnitudes, and reset again.
     */
    @Benchmark
    public void computeMagnitudesSquared(Blackhole bh) {
        bank.computeMagnitudesSquaredInto(signal, magnitudes);
        bh.consume(magnitudes);
    }
}
