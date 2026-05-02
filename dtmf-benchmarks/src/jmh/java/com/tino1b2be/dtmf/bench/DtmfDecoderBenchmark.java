package com.tino1b2be.dtmf.bench;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
import com.tino1b2be.dtmf.DtmfDecoder;
import com.tino1b2be.dtmf.DtmfGenerator;
import com.tino1b2be.dtmf.DtmfTone;

/**
 * JMH benchmark exercising {@link DtmfDecoder#decode(double[], DtmfConfig)}
 * across every Supported_Sample_Rate (Requirements&nbsp;15.1, 15.2).
 *
 * <p>Each rate gets a roughly one-second pre-generated {@code double[]}
 * holding a short repeating DTMF sequence. One {@code @Benchmark} method per
 * rate decodes that buffer and sinks the result list into a
 * {@link Blackhole} so the JIT cannot elide the call.
 *
 * <p>Methodology:
 * <ul>
 *   <li>{@link Mode#AverageTime} with {@link TimeUnit#MICROSECONDS} output —
 *       decode latency on a fixed-size buffer is a scalar property, not a
 *       throughput one, so the "average microseconds per decode" framing is
 *       the one the reader wants.</li>
 *   <li>{@link State} at {@link Scope#BENCHMARK} scope — the pre-generated
 *       audio is read-only after {@link #setup()}, so sharing one copy
 *       across threads (and iterations) is both safe and cheap.</li>
 *   <li>{@link DtmfConfig#forTelephony()} for every rate, wrapped through
 *       {@link DtmfConfig#advanced()} so the sample rate can vary beyond
 *       the 8&nbsp;kHz the standard factory is hard-wired to.</li>
 * </ul>
 *
 * <p>No performance gate is enforced (Requirement 15.4); the numbers produced
 * here are a baseline for future regression tracking.
 *
 * @since 2.0.0
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class DtmfDecoderBenchmark {

    /** Short repeating DTMF sequence. Keeps per-rate buffer length close to 1 s. */
    private static final String SEQUENCE = "0123456789ABCD*#";

    /** Tone duration used for the pre-generated corpus. */
    private static final Duration TONE = Duration.ofMillis(40);

    /** Gap duration used for the pre-generated corpus. */
    private static final Duration GAP = Duration.ofMillis(40);

    private DtmfConfig cfg8k;
    private DtmfConfig cfg16k;
    private DtmfConfig cfg44k;
    private DtmfConfig cfg48k;

    private double[] audio8k;
    private double[] audio16k;
    private double[] audio44k;
    private double[] audio48k;

    /**
     * Build one config per Supported_Sample_Rate and pre-generate roughly one
     * second of DTMF audio for each. {@link DtmfGenerator#generate(String, DtmfConfig)}
     * is deterministic, so each {@code @Setup} produces the same buffer.
     */
    @Setup
    public void setup() {
        cfg8k = configFor(8000);
        cfg16k = configFor(16000);
        cfg44k = configFor(44100);
        cfg48k = configFor(48000);

        // Single pass of SEQUENCE with 40 ms tones + 40 ms gaps is
        // 16 * 40 + 15 * 40 = 1240 ms, already over a second; one pass
        // therefore suffices for every rate.
        audio8k = DtmfGenerator.generate(SEQUENCE, cfg8k);
        audio16k = DtmfGenerator.generate(SEQUENCE, cfg16k);
        audio44k = DtmfGenerator.generate(SEQUENCE, cfg44k);
        audio48k = DtmfGenerator.generate(SEQUENCE, cfg48k);
    }

    private static DtmfConfig configFor(int sampleRate) {
        return DtmfConfig.advanced()
                .sampleRate(sampleRate)
                .minimumToneDuration(TONE)
                .minimumGapDuration(GAP)
                .build();
    }

    /** Decode the 8&nbsp;kHz corpus. */
    @Benchmark
    public void decode8k(Blackhole bh) {
        List<DtmfTone> out = DtmfDecoder.decode(audio8k, cfg8k);
        bh.consume(out);
    }

    /** Decode the 16&nbsp;kHz corpus. */
    @Benchmark
    public void decode16k(Blackhole bh) {
        List<DtmfTone> out = DtmfDecoder.decode(audio16k, cfg16k);
        bh.consume(out);
    }

    /** Decode the 44.1&nbsp;kHz corpus. */
    @Benchmark
    public void decode44k(Blackhole bh) {
        List<DtmfTone> out = DtmfDecoder.decode(audio44k, cfg44k);
        bh.consume(out);
    }

    /** Decode the 48&nbsp;kHz corpus. */
    @Benchmark
    public void decode48k(Blackhole bh) {
        List<DtmfTone> out = DtmfDecoder.decode(audio48k, cfg48k);
        bh.consume(out);
    }
}
