package com.tino1b2be.dtmf.io.wav;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * Test-only WAV encoder that produces a minimal
 * {@code RIFF/WAVE/fmt /data} byte layout for use as decoder fixtures in
 * this module's unit tests and property tests (Task 6.7).
 *
 * <p>This class deliberately lives under {@code src/test/java} &mdash;
 * not {@code src/main/java} &mdash; because Requirement 18.4 puts WAV
 * encoding (alongside MP3 encoding, microphone capture, and every other
 * non-read-only I/O concern) explicitly out of scope for {@code dtmf-io}
 * v2.0. Shipping an encoder in production code would be a requirement
 * regression. The encoder exists purely so tests can feed controlled
 * byte sequences into {@link WavAudioSource} and
 * {@link WavAudioSourceProvider} without reaching for
 * {@code javax.sound.sampled} (which Requirement 9.12 forbids in this
 * module's main source set, and which is awkward to wire into property
 * tests besides) and without checking pre-built binary fixtures into
 * source control.
 *
 * <h2>Output layout</h2>
 *
 * Every method emits exactly three chunks in this order, with no
 * {@code LIST} chunk, no {@code fact} chunk, no {@code junk} chunk,
 * no {@code PEAK} chunk, no metadata, and no trailing bytes:
 *
 * <ol>
 *   <li>12-byte outer header: ASCII {@code "RIFF"} + little-endian
 *       {@code uint32} {@code chunkSize = 4 + (8 + fmtSize) + (8 + dataSize)}
 *       + ASCII {@code "WAVE"}.</li>
 *   <li>24-byte {@code fmt } chunk: ASCII {@code "fmt "} +
 *       {@code uint32 size = 16} + {@code uint16 wFormatTag} +
 *       {@code uint16 nChannels} + {@code uint32 nSamplesPerSec} +
 *       {@code uint32 nAvgBytesPerSec} + {@code uint16 nBlockAlign} +
 *       {@code uint16 wBitsPerSample}. The {@code fmt } payload size is
 *       always 16 bytes (the classic {@code PCMWAVEFORMAT} shape),
 *       which is the minimum the parser accepts for both
 *       {@code WAVE_FORMAT_PCM} ({@code 0x0001}) and
 *       {@code WAVE_FORMAT_IEEE_FLOAT} ({@code 0x0003}).</li>
 *   <li>{@code data} chunk: ASCII {@code "data"} +
 *       {@code uint32 dataSize} + the packed little-endian sample
 *       payload.</li>
 * </ol>
 *
 * <p>Sample counts are constrained so {@code dataSize} always stays at
 * or below {@code Integer.MAX_VALUE} bytes &mdash; this keeps the
 * encoder's output compatible with the classic 32-bit RIFF form (no
 * RF64 escape) and avoids overflow when computing the outer size
 * field. Callers that need pathological sizes must build those fixtures
 * by hand.
 *
 * <h2>PCM16 quantization</h2>
 *
 * The two integer-encoding entry points
 * ({@link #encodePcm16Mono(double[], int)} and
 * {@link #encodePcm16Stereo(double[], double[], int)}) quantize each
 * normalised {@code double} sample to a signed 16-bit value via
 * {@code q = round(sample * 32768.0)} and clamp the result to
 * {@code [Short.MIN_VALUE, Short.MAX_VALUE] = [-32768, 32767]}. The
 * divisor used at decode time is also {@code 2^15 = 32768.0} (see
 * {@code SampleConversion#decodePcm16LE}), so a round-trip of an input
 * that already lands exactly on a quantization grid point reproduces
 * the input value exactly, and the worst-case round-trip error for any
 * input in {@code [-1.0, 1.0)} is bounded by {@code 1 / 32768.0}. An
 * input of exactly {@code +1.0} quantises to {@code 32768} and clamps
 * to {@code 32767}, decoding to {@code 32767 / 32768.0 = 0.999969…}
 * rather than {@code 1.0}; this is the standard PCM16 convention and
 * matches what any mainstream encoder (including
 * {@code javax.sound.sampled}) produces. Callers that care about the
 * exact ceiling should arrange their inputs to stay in
 * {@code [-1.0, 1.0 - 1/32768)}.
 *
 * <h2>IEEE float mono</h2>
 *
 * {@link #encodePcmFloatMono(double[], int)} widens each {@code double}
 * to a {@code float} via the JLS narrowing conversion and writes it as
 * four little-endian bytes. No clamping is applied because IEEE float
 * WAVs conventionally admit values outside {@code [-1.0, 1.0]} (they
 * simply clip at the D/A converter), and the decoder reads {@code float}
 * samples without scaling. A round-trip of a value exactly
 * representable as a {@code float} is therefore bit-exact.
 *
 * <h2>Endianness and encoding</h2>
 *
 * RIFF is little-endian everywhere (Requirement 9.2 of the RIFF spec),
 * so every multi-byte field and every sample is emitted in little-endian
 * byte order. Four-character chunk IDs are US-ASCII.
 *
 * <h2>Not part of the published API</h2>
 *
 * This class is test-only. It is visible to the unit- and property-test
 * source sets of {@code dtmf-io-wav} (and only those) because it lives
 * under {@code src/test/java}; it is not packaged into the published
 * {@code dtmf-io-wav} jar. External consumers MUST NOT depend on it, at
 * any version, via any mechanism.
 *
 * @since 2.0.0
 */
final class WavEncoder {

    // ------------------------------------------------------------------
    // WAV header constants
    // ------------------------------------------------------------------

    /** Signed integer PCM format tag ({@code WAVE_FORMAT_PCM}). */
    private static final short WAVE_FORMAT_PCM = 0x0001;

    /** IEEE 754 float format tag ({@code WAVE_FORMAT_IEEE_FLOAT}). */
    private static final short WAVE_FORMAT_IEEE_FLOAT = 0x0003;

    /**
     * Size of the classic {@code PCMWAVEFORMAT} {@code fmt } chunk
     * payload: two {@code uint16}s, one {@code uint32}, one
     * {@code uint32}, two {@code uint16}s = 16 bytes. The parser
     * accepts 16 as the minimum {@code fmt } payload size for
     * non-extensible encodings (see
     * {@code WavAudioSourceProvider#parseFmtChunk}).
     */
    private static final int FMT_CHUNK_PAYLOAD_SIZE = 16;

    /** Quantization divisor for PCM16 (2<sup>15</sup>). */
    private static final double PCM16_SCALE = 32768.0;

    private WavEncoder() {
        // Not instantiable.
    }

    // ------------------------------------------------------------------
    // Public encoding entry points
    // ------------------------------------------------------------------

    /**
     * Encode a mono {@code double[]} as a minimal 16-bit signed PCM
     * WAV file.
     *
     * @param samples    per-frame samples in the nominal range
     *                   {@code [-1.0, 1.0]}; must be non-null
     * @param sampleRate the sample rate in Hertz to advertise in the
     *                   {@code fmt } chunk; must be strictly positive
     * @return the full WAV file as a fresh {@code byte[]}
     * @throws NullPointerException     if {@code samples} is {@code null}
     * @throws IllegalArgumentException if {@code sampleRate <= 0}
     */
    public static byte[] encodePcm16Mono(double[] samples, int sampleRate) {
        Objects.requireNonNull(samples, "samples");
        requirePositive(sampleRate, "sampleRate");

        final int channels = 1;
        final int bitsPerSample = 16;
        final int bytesPerSample = bitsPerSample / 8;
        final int frameCount = samples.length;
        final int dataSize = Math.multiplyExact(
                Math.multiplyExact(frameCount, channels), bytesPerSample);

        ByteBuffer buf = allocateWavBuffer(dataSize);
        writeHeader(buf, WAVE_FORMAT_PCM, channels, sampleRate,
                bitsPerSample, dataSize);
        for (int i = 0; i < frameCount; i++) {
            buf.putShort(quantizePcm16(samples[i]));
        }
        return buf.array();
    }

    /**
     * Encode two equal-length {@code double[]}s (left, right) as a
     * minimal 16-bit signed PCM stereo WAV file. Samples are
     * interleaved frame-by-frame as {@code L, R, L, R, …} per the
     * {@code AudioSource} interleaving contract (Requirement 3.5).
     *
     * @param left       left-channel samples in the nominal range
     *                   {@code [-1.0, 1.0]}; must be non-null
     * @param right      right-channel samples, same length as
     *                   {@code left}; must be non-null
     * @param sampleRate the sample rate in Hertz to advertise in the
     *                   {@code fmt } chunk; must be strictly positive
     * @return the full WAV file as a fresh {@code byte[]}
     * @throws NullPointerException     if {@code left} or {@code right}
     *                                  is {@code null}
     * @throws IllegalArgumentException if {@code left.length != right.length}
     *                                  or if {@code sampleRate <= 0}
     */
    public static byte[] encodePcm16Stereo(double[] left, double[] right, int sampleRate) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        if (left.length != right.length) {
            throw new IllegalArgumentException(
                    "left and right must have the same length, got left.length="
                            + left.length + ", right.length=" + right.length);
        }
        requirePositive(sampleRate, "sampleRate");

        final int channels = 2;
        final int bitsPerSample = 16;
        final int bytesPerSample = bitsPerSample / 8;
        final int frameCount = left.length;
        final int dataSize = Math.multiplyExact(
                Math.multiplyExact(frameCount, channels), bytesPerSample);

        ByteBuffer buf = allocateWavBuffer(dataSize);
        writeHeader(buf, WAVE_FORMAT_PCM, channels, sampleRate,
                bitsPerSample, dataSize);
        for (int i = 0; i < frameCount; i++) {
            buf.putShort(quantizePcm16(left[i]));
            buf.putShort(quantizePcm16(right[i]));
        }
        return buf.array();
    }

    /**
     * Encode a mono {@code double[]} as a minimal 32-bit IEEE float
     * WAV file (format tag {@code 0x0003}). Each sample is narrowed to
     * a {@code float} and written as four little-endian bytes with no
     * scaling or clamping; IEEE-float WAVs conventionally carry
     * normalised samples in {@code [-1.0, 1.0]} but admit out-of-range
     * values without distortion until the D/A stage.
     *
     * @param samples    per-frame samples; must be non-null
     * @param sampleRate the sample rate in Hertz to advertise in the
     *                   {@code fmt } chunk; must be strictly positive
     * @return the full WAV file as a fresh {@code byte[]}
     * @throws NullPointerException     if {@code samples} is {@code null}
     * @throws IllegalArgumentException if {@code sampleRate <= 0}
     */
    public static byte[] encodePcmFloatMono(double[] samples, int sampleRate) {
        Objects.requireNonNull(samples, "samples");
        requirePositive(sampleRate, "sampleRate");

        final int channels = 1;
        final int bitsPerSample = 32;
        final int bytesPerSample = bitsPerSample / 8;
        final int frameCount = samples.length;
        final int dataSize = Math.multiplyExact(
                Math.multiplyExact(frameCount, channels), bytesPerSample);

        ByteBuffer buf = allocateWavBuffer(dataSize);
        writeHeader(buf, WAVE_FORMAT_IEEE_FLOAT, channels, sampleRate,
                bitsPerSample, dataSize);
        for (int i = 0; i < frameCount; i++) {
            buf.putFloat((float) samples[i]);
        }
        return buf.array();
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Quantize a single normalised sample to a signed 16-bit value,
     * clamping the result to {@code [Short.MIN_VALUE, Short.MAX_VALUE]}.
     *
     * <p>{@code Math.round} on a {@code double} returns a {@code long};
     * the result is then narrowed to {@code short} with explicit
     * saturation. In particular:
     * <ul>
     *   <li>{@code NaN} rounds to {@code 0} and is written as
     *       {@code 0} &mdash; the standard JLS narrowing-conversion
     *       behaviour.</li>
     *   <li>{@code +Infinity} and any value {@code >= 1.0} saturate at
     *       {@code +32767}.</li>
     *   <li>{@code -Infinity} and any value {@code <= -1.0 - 1/32768}
     *       saturate at {@code -32768}; the exact boundary value
     *       {@code -1.0} rounds to {@code -32768} which does not need
     *       clamping.</li>
     * </ul>
     *
     * @param sample normalised input sample
     * @return signed 16-bit quantized value
     */
    private static short quantizePcm16(double sample) {
        long quantized = Math.round(sample * PCM16_SCALE);
        if (quantized > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        if (quantized < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        return (short) quantized;
    }

    /**
     * Allocate a little-endian {@link ByteBuffer} sized to hold the
     * complete WAV file (outer header + {@code fmt } chunk + {@code data}
     * chunk header + {@code data} payload).
     */
    private static ByteBuffer allocateWavBuffer(int dataSize) {
        // 12 (outer) + 8 (fmt header) + FMT_CHUNK_PAYLOAD_SIZE + 8 (data header) + dataSize
        int total = Math.addExact(12 + 8 + FMT_CHUNK_PAYLOAD_SIZE + 8, dataSize);
        ByteBuffer buf = ByteBuffer.allocate(total);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        return buf;
    }

    /**
     * Write the 44-byte prefix (outer {@code RIFF} header + {@code fmt }
     * chunk + 8-byte {@code data} header). On return the buffer's
     * position is at the start of the {@code data} payload.
     */
    private static void writeHeader(
            ByteBuffer buf,
            short formatTag,
            int channels,
            int sampleRate,
            int bitsPerSample,
            int dataSize) {

        int bytesPerSample = bitsPerSample / 8;
        int blockAlign = channels * bytesPerSample;
        int avgBytesPerSec = sampleRate * blockAlign;

        // Outer RIFF size: total file size - 8 bytes (the 'RIFF' tag
        // and the size field itself).
        int riffSize = 4                           // 'WAVE'
                + 8 + FMT_CHUNK_PAYLOAD_SIZE       // 'fmt ' + size field + fmt payload
                + 8 + dataSize;                    // 'data' + size field + payload

        // --- Outer RIFF/WAVE header (12 bytes) ---
        putAscii(buf, "RIFF");
        buf.putInt(riffSize);
        putAscii(buf, "WAVE");

        // --- fmt  chunk (8-byte header + 16-byte payload) ---
        putAscii(buf, "fmt ");
        buf.putInt(FMT_CHUNK_PAYLOAD_SIZE);
        buf.putShort(formatTag);
        buf.putShort((short) channels);
        buf.putInt(sampleRate);
        buf.putInt(avgBytesPerSec);
        buf.putShort((short) blockAlign);
        buf.putShort((short) bitsPerSample);

        // --- data chunk header (8 bytes); payload written by the caller. ---
        putAscii(buf, "data");
        buf.putInt(dataSize);
    }

    /**
     * Write exactly four ASCII bytes of a fixed-width chunk ID or
     * form-type marker. Non-ASCII characters would be silently
     * corrupted by the narrow-to-byte cast, so callers must use
     * literal four-character strings.
     */
    private static void putAscii(ByteBuffer buf, String id) {
        for (int i = 0; i < id.length(); i++) {
            buf.put((byte) id.charAt(i));
        }
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    name + " must be > 0, got " + value);
        }
    }
}
