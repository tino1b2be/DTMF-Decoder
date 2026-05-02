package com.tino1b2be.dtmf;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Pull-based DTMF iteration: expose a {@link DtmfDetector} as an
 * {@link Iterator} over emitted {@link DtmfTone} values.
 *
 * <p>Callers wire a {@link SampleSource} to an in-memory buffer, file reader,
 * or any other source of normalised {@code double} PCM samples, and iterate.
 * {@link #hasNext()} pulls samples from the source until a tone becomes
 * available or the source signals end-of-stream; {@link #next()} dequeues
 * the head of the buffered emissions.
 *
 * <p>Relationship to {@link DtmfDetector} (Requirement 7.5):
 * {@code DtmfStream.fromSamples(samples, cfg)} iterated to exhaustion
 * produces the same tones as a fresh {@code DtmfDetector(cfg)} fed
 * {@code samples} followed by {@link DtmfDetector#flush()}. The stream is a
 * re-packaging of the push API, not a separate detection implementation.
 *
 * <p>{@link #close()} flushes the underlying detector and releases references
 * to the {@link SampleSource}; it is idempotent and safe to call multiple
 * times, including from a {@code try-with-resources} block.
 *
 * <p>Instances are not thread-safe. One stream per consumer.
 *
 * @since 2.0.0
 */
public final class DtmfStream implements Iterator<DtmfTone>, AutoCloseable {

    /**
     * Default read buffer size. Chosen so typical sample-rate / block-size
     * pairs comfortably fit many analysis blocks per read, which amortises
     * the overhead of the source callback.
     */
    private static final int READ_BUFFER_SIZE = 4096;

    /**
     * A source of {@code double} PCM samples for
     * {@link #fromSource(SampleSource, DtmfConfig)}. Callers implement this
     * as a functional interface, typically a lambda wrapping a file, socket,
     * or pre-buffered array.
     *
     * <p>The return value follows the {@link java.io.InputStream} convention:
     * a non-negative integer is the number of samples written into
     * {@code buffer} starting at {@code offset}, and {@code -1} signals
     * end-of-stream.
     */
    @FunctionalInterface
    public interface SampleSource {

        /**
         * Read up to {@code length} samples into
         * {@code buffer[offset .. offset + length)}.
         *
         * @param buffer destination buffer; non-null
         * @param offset starting index; {@code 0 <= offset <= buffer.length}
         * @param length maximum number of samples to write;
         *               {@code 0 <= length && offset + length <= buffer.length}
         * @return the number of samples written ({@code 0 <= n <= length}),
         *         or {@code -1} to signal end-of-stream
         */
        int readInto(double[] buffer, int offset, int length);
    }

    private final DtmfDetector detector;
    private final Deque<DtmfTone> pending = new ArrayDeque<>();
    private final double[] readBuffer = new double[READ_BUFFER_SIZE];

    private SampleSource source;
    private boolean sourceExhausted;
    private boolean flushed;
    private boolean closed;

    /**
     * Create a {@code DtmfStream} that pulls samples from {@code source}.
     *
     * @param source source of PCM samples; non-null
     * @param config detection configuration; non-null
     * @return a new stream
     * @throws NullPointerException if either argument is {@code null}
     */
    public static DtmfStream fromSource(SampleSource source, DtmfConfig config) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(config, "config");
        return new DtmfStream(source, config);
    }

    /**
     * Create a {@code DtmfStream} over a pre-existing {@code double[]}
     * sample buffer.
     *
     * <p>The buffer is read sequentially exactly once; subsequent iteration
     * past the end of the buffer triggers {@link SampleSource} EOS.
     *
     * @param samples sample buffer; non-null
     * @param config  detection configuration; non-null
     * @return a new stream
     * @throws NullPointerException if either argument is {@code null}
     */
    public static DtmfStream fromSamples(double[] samples, DtmfConfig config) {
        Objects.requireNonNull(samples, "samples");
        Objects.requireNonNull(config, "config");
        return new DtmfStream(new ArraySampleSource(samples), config);
    }

    private DtmfStream(SampleSource source, DtmfConfig config) {
        this.source = source;
        this.detector = new DtmfDetector(config);
        this.detector.onTone(pending::addLast);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Pulls samples from the source, one read at a time, into the internal
     * buffer; each read feeds the detector and any emitted tones are queued.
     * Returns {@code true} as soon as the queue becomes non-empty; returns
     * {@code false} after the source signals EOS, {@link DtmfDetector#flush()}
     * has been called, and the queue is empty.
     */
    @Override
    public boolean hasNext() {
        if (!pending.isEmpty()) {
            return true;
        }
        if (closed) {
            return false;
        }
        while (pending.isEmpty() && !sourceExhausted) {
            int n = source.readInto(readBuffer, 0, readBuffer.length);
            if (n < 0) {
                sourceExhausted = true;
                break;
            }
            if (n > 0) {
                detector.process(readBuffer, 0, n);
            }
        }
        if (pending.isEmpty() && sourceExhausted && !flushed) {
            detector.flush();
            flushed = true;
        }
        return !pending.isEmpty();
    }

    /**
     * {@inheritDoc}
     *
     * @throws NoSuchElementException if no further tones are available
     */
    @Override
    public DtmfTone next() {
        if (pending.isEmpty() && !hasNext()) {
            throw new NoSuchElementException("no more tones");
        }
        return pending.pollFirst();
    }

    /**
     * Flush the underlying detector and release references to the
     * {@link SampleSource}. Idempotent: safe to call multiple times, safe to
     * call after the iterator is already exhausted.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (!flushed) {
            detector.flush();
            flushed = true;
        }
        source = null;
    }

    /**
     * {@link SampleSource} implementation backing
     * {@link DtmfStream#fromSamples(double[], DtmfConfig)}. Hands the caller's
     * array back in successive slices without copying.
     *
     * <p>The source does not retain a reference to the array after
     * end-of-stream is signalled: callers who want to mutate the source
     * buffer can do so safely once iteration completes.
     */
    private static final class ArraySampleSource implements SampleSource {
        private final double[] samples;
        private int position;

        ArraySampleSource(double[] samples) {
            this.samples = samples;
        }

        @Override
        public int readInto(double[] buffer, int offset, int length) {
            if (position >= samples.length) {
                return -1;
            }
            int remaining = samples.length - position;
            int n = Math.min(length, remaining);
            System.arraycopy(samples, position, buffer, offset, n);
            position += n;
            return n;
        }
    }
}
