package com.tino1b2be.dtmf.io;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * Integration-test-only minimal WAV encoder.
 *
 * <p>The {@code dtmf-io-wav} module ships a {@code WavEncoder} under
 * {@code src/test/java}, but because that source set is test-scope only for
 * {@code dtmf-io-wav}, it is not visible on this module's
 * {@code integrationTest} classpath — even though {@code dtmf-io-wav} is
 * {@code integrationTestRuntimeOnly} here. To keep the round-trip tests
 * in Stage 8 self-contained, this class re-implements the minimum a WAV
 * fixture needs to look like a valid PCM16 mono RIFF/WAVE file: a 12-byte
 * {@code RIFF/WAVE} header, a 24-byte {@code fmt } chunk with payload size
 * {@code 16}, and a {@code data} chunk whose payload is the quantised
 * samples.
 *
 * <p>Sample quantisation matches {@code WavEncoder.quantizePcm16}:
 * {@code q = round(sample * 32768.0)} clamped to
 * {@code [Short.MIN_VALUE, Short.MAX_VALUE]}. The result round-trips
 * bit-exactly through the {@code WavAudioSource} provider because the
 * decoder uses divisor {@code 32768.0}.
 *
 * <p>Not part of the published API. Used only by
 * {@code OpenWavIT} / {@code UnrecognizedInputIT} helpers.
 *
 * @since 2.0.0
 */
final class IntegrationWavEncoder {

    /** Signed integer PCM format tag. */
    private static final short WAVE_FORMAT_PCM = 0x0001;

    /** Classic {@code PCMWAVEFORMAT} {@code fmt } chunk payload size. */
    private static final int FMT_CHUNK_PAYLOAD_SIZE = 16;

    /** Quantisation divisor for PCM16. */
    private static final double PCM16_SCALE = 32768.0;

    private IntegrationWavEncoder() {
        // Not instantiable.
    }

    /**
     * Encode a mono {@code double[]} as a minimal PCM16 little-endian WAV
     * byte array.
     *
     * @param samples    per-frame samples in {@code [-1.0, 1.0]}; non-null
     * @param sampleRate sample rate in Hertz; must be {@code > 0}
     * @return the full WAV file as a fresh {@code byte[]}
     */
    static byte[] encodePcm16Mono(double[] samples, int sampleRate) {
        Objects.requireNonNull(samples, "samples");
        if (sampleRate <= 0) {
            throw new IllegalArgumentException(
                    "sampleRate must be > 0, was " + sampleRate);
        }

        final int channels = 1;
        final int bitsPerSample = 16;
        final int bytesPerSample = bitsPerSample / 8;
        final int frameCount = samples.length;
        final int dataSize = Math.multiplyExact(
                Math.multiplyExact(frameCount, channels), bytesPerSample);

        // 12 (outer) + 8 (fmt header) + 16 (fmt payload) + 8 (data header)
        // + dataSize
        int total = Math.addExact(12 + 8 + FMT_CHUNK_PAYLOAD_SIZE + 8, dataSize);
        ByteBuffer buf = ByteBuffer.allocate(total);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        int blockAlign = channels * bytesPerSample;
        int avgBytesPerSec = sampleRate * blockAlign;
        int riffSize = 4 + 8 + FMT_CHUNK_PAYLOAD_SIZE + 8 + dataSize;

        // Outer RIFF/WAVE header.
        putAscii(buf, "RIFF");
        buf.putInt(riffSize);
        putAscii(buf, "WAVE");

        // fmt  chunk.
        putAscii(buf, "fmt ");
        buf.putInt(FMT_CHUNK_PAYLOAD_SIZE);
        buf.putShort(WAVE_FORMAT_PCM);
        buf.putShort((short) channels);
        buf.putInt(sampleRate);
        buf.putInt(avgBytesPerSec);
        buf.putShort((short) blockAlign);
        buf.putShort((short) bitsPerSample);

        // data chunk header + payload.
        putAscii(buf, "data");
        buf.putInt(dataSize);
        for (int i = 0; i < frameCount; i++) {
            buf.putShort(quantizePcm16(samples[i]));
        }
        return buf.array();
    }

    /**
     * Quantise one normalised sample to a signed 16-bit value, clamping to
     * {@code [Short.MIN_VALUE, Short.MAX_VALUE]}.
     */
    private static short quantizePcm16(double sample) {
        long quantised = Math.round(sample * PCM16_SCALE);
        if (quantised > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        if (quantised < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        return (short) quantised;
    }

    /** Write exactly four ASCII bytes of a fixed-width chunk ID. */
    private static void putAscii(ByteBuffer buf, String id) {
        for (int i = 0; i < id.length(); i++) {
            buf.put((byte) id.charAt(i));
        }
    }
}
