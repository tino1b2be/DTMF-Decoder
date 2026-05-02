package com.tino1b2be.dtmf.io;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tino1b2be.dtmf.ChannelMode;
import com.tino1b2be.dtmf.DtmfConfig;
import com.tino1b2be.dtmf.DtmfDecoder;
import com.tino1b2be.dtmf.DtmfGenerator;
import com.tino1b2be.dtmf.DtmfTone;

/**
 * Unit tests for {@link DtmfFileDecoder} (Task 5.2).
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>null-parameter rejection on every public overload
 *       (Req 8.13, 12.1);</li>
 *   <li>channel-count rejection for sources with more than two channels
 *       (Req 8.10);</li>
 *   <li>channel-mode rejection when a mono source is paired with a stereo
 *       {@link ChannelMode} (Req 8.9);</li>
 *   <li>sample-rate rejection outside the supported
 *       {@code [4000, 192000]} Hz range (Req 17.3);</li>
 *   <li>caller-stream ownership: {@link
 *       DtmfFileDecoder#decode(AudioSource, DtmfConfig)} never closes the
 *       supplied source (Req 8.12);</li>
 *   <li>channel handling: stereo source paired with {@link ChannelMode#MONO}
 *       downmixes via averaging before {@link DtmfDecoder} (Req 8.7), and
 *       stereo source paired with {@link ChannelMode#STEREO_DOWNMIX}
 *       forwards the interleaved buffer unchanged (Req 8.8);</li>
 *   <li>happy-path round-trip through {@link RawPcmAudioSource} of a single
 *       {@link DtmfGenerator}-produced tone.</li>
 * </ul>
 *
 * <p><b>Path-overload close lifecycle (Req 8.11):</b> exercised by
 * <em>Property&nbsp;9</em> in
 * {@code DtmfFileDecoderCloseSemanticsPropertyTest} (Task&nbsp;5.3), which
 * drives every overload through a close-tracking {@link AudioSource} double
 * on normal and exceptional return paths. Writing a second unit test for
 * the same contract here would either require a bundled WAV fixture (the
 * WAV provider arrives in Stage&nbsp;6) or a test-only
 * {@link AudioSourceProvider} registered via {@code META-INF/services}
 * that would pollute every other test's {@code ServiceLoader}-visible
 * provider list. The property test covers both normal and exceptional
 * returns for all four overloads, so the belt-and-braces unit variant here
 * is deliberately omitted. The {@link
 * #decodeAudioSourceDoesNotCloseCallerSuppliedSource()} test below anchors
 * the complementary "does not close" half of the contract (Req 8.12).
 *
 * <p>Validates: Requirements 8.7, 8.8, 8.9, 8.10, 8.11, 8.12, 8.13, 17.3.
 */
class DtmfFileDecoderTest {

    // ---------------------------------------------------------------------
    // Shared fixtures
    // ---------------------------------------------------------------------

    /** Standard telephony config: 8 kHz, MONO, RECTANGULAR, etc. */
    private static final DtmfConfig TELEPHONY = DtmfConfig.forTelephony();

    /**
     * A minimal valid mono PCM16 payload (two zero-valued frames) used where
     * the test only needs a non-null buffer — the decoder guards are
     * exercised before any samples are read.
     */
    private static byte[] twoMonoZeroFramesPcm16() {
        return new byte[] { 0, 0, 0, 0 };
    }

    // ---------------------------------------------------------------------
    // Null-parameter rejection on every overload — Req 8.13, 12.1
    // ---------------------------------------------------------------------

    @Test
    void decodePathOverloadThrowsNpeWhenPathIsNull() {
        NullPointerException npe = assertThrows(
                NullPointerException.class,
                () -> DtmfFileDecoder.decode((Path) null, TELEPHONY));
        assertNpeNames(npe, "path");
    }

    @Test
    void decodePathOverloadThrowsNpeWhenConfigIsNull() throws IOException {
        Path any = Paths.get("does-not-matter.wav");
        NullPointerException npe = assertThrows(
                NullPointerException.class,
                () -> DtmfFileDecoder.decode(any, null));
        assertNpeNames(npe, "config");
    }

    @Test
    void decodeInputStreamOverloadThrowsNpeWhenStreamIsNull() {
        NullPointerException npe = assertThrows(
                NullPointerException.class,
                () -> DtmfFileDecoder.decode(null, /* hint */ "x.wav", TELEPHONY));
        assertNpeNames(npe, "stream");
    }

    @Test
    void decodeInputStreamOverloadThrowsNpeWhenConfigIsNull() {
        NullPointerException npe = assertThrows(
                NullPointerException.class,
                () -> DtmfFileDecoder.decode(
                        new ByteArrayInputStream(new byte[0]),
                        /* hint */ "x.wav",
                        null));
        assertNpeNames(npe, "config");
    }

    @Test
    void decodeUrlOverloadThrowsNpeWhenUrlIsNull() {
        NullPointerException npe = assertThrows(
                NullPointerException.class,
                () -> DtmfFileDecoder.decode((URL) null, TELEPHONY));
        assertNpeNames(npe, "url");
    }

    @Test
    void decodeUrlOverloadThrowsNpeWhenConfigIsNull() throws Exception {
        URL url = new URL("file:/does-not-matter.wav");
        NullPointerException npe = assertThrows(
                NullPointerException.class,
                () -> DtmfFileDecoder.decode(url, null));
        assertNpeNames(npe, "config");
    }

    @Test
    void decodeAudioSourceOverloadThrowsNpeWhenSourceIsNull() {
        NullPointerException npe = assertThrows(
                NullPointerException.class,
                () -> DtmfFileDecoder.decode((AudioSource) null, TELEPHONY));
        assertNpeNames(npe, "source");
    }

    @Test
    void decodeAudioSourceOverloadThrowsNpeWhenConfigIsNull() {
        RawPcmAudioSource source = RawPcmAudioSource.fromPcm16LittleEndian(
                twoMonoZeroFramesPcm16(), 8_000, 1);
        try {
            NullPointerException npe = assertThrows(
                    NullPointerException.class,
                    () -> DtmfFileDecoder.decode(source, null));
            assertNpeNames(npe, "config");
        } finally {
            closeQuietly(source);
        }
    }

    // ---------------------------------------------------------------------
    // Channel count > 2 — Req 8.10
    // ---------------------------------------------------------------------

    @Test
    void decodeRejectsSourceWithChannelCountGreaterThanTwo() throws IOException {
        // 3 channels, 16 bit mono PCM → 6 bytes/frame; 12 bytes = 2 frames.
        byte[] data = new byte[12];
        RawPcmAudioSource source = new RawPcmAudioSource(
                data, 8_000, 16, ByteOrder.LITTLE_ENDIAN, 3, PcmEncoding.SIGNED_INT);
        try {
            UnsupportedAudioFormatException ex = assertThrows(
                    UnsupportedAudioFormatException.class,
                    () -> DtmfFileDecoder.decode(source, TELEPHONY));
            String msg = ex.getMessage();
            assertNotNull(msg, "UAFE message must not be null");
            assertAll(
                    () -> assertTrue(msg.contains("3"),
                            "Message must name the offending channel count '3'; was: " + msg),
                    () -> assertTrue(msg.contains("1") && msg.contains("2"),
                            "Message must state that only 1 and 2 are supported; was: " + msg));
        } finally {
            source.close();
        }
    }

    // ---------------------------------------------------------------------
    // Mono source + STEREO channel modes — Req 8.9
    // ---------------------------------------------------------------------

    @Test
    void decodeRejectsMonoSourceWithStereoIndependentMode() throws IOException {
        RawPcmAudioSource source = RawPcmAudioSource.fromPcm16LittleEndian(
                twoMonoZeroFramesPcm16(), 8_000, 1);
        DtmfConfig stereoCfg = DtmfConfig.advanced()
                .channelMode(ChannelMode.STEREO_INDEPENDENT)
                .build();
        try {
            UnsupportedAudioFormatException ex = assertThrows(
                    UnsupportedAudioFormatException.class,
                    () -> DtmfFileDecoder.decode(source, stereoCfg));
            String msg = ex.getMessage();
            assertNotNull(msg, "UAFE message must not be null");
            assertAll(
                    () -> assertTrue(msg.contains("mono") || msg.contains("1"),
                            "Message must identify the mono source; was: " + msg),
                    () -> assertTrue(msg.contains("STEREO_INDEPENDENT"),
                            "Message must identify the requested channel mode; was: " + msg),
                    () -> assertTrue(msg.contains("MONO"),
                            "Message must point the caller at ChannelMode.MONO; was: " + msg));
        } finally {
            source.close();
        }
    }

    @Test
    void decodeRejectsMonoSourceWithStereoDownmixMode() throws IOException {
        RawPcmAudioSource source = RawPcmAudioSource.fromPcm16LittleEndian(
                twoMonoZeroFramesPcm16(), 8_000, 1);
        DtmfConfig stereoCfg = DtmfConfig.advanced()
                .channelMode(ChannelMode.STEREO_DOWNMIX)
                .build();
        try {
            UnsupportedAudioFormatException ex = assertThrows(
                    UnsupportedAudioFormatException.class,
                    () -> DtmfFileDecoder.decode(source, stereoCfg));
            String msg = ex.getMessage();
            assertNotNull(msg, "UAFE message must not be null");
            assertAll(
                    () -> assertTrue(msg.contains("mono") || msg.contains("1"),
                            "Message must identify the mono source; was: " + msg),
                    () -> assertTrue(msg.contains("STEREO_DOWNMIX"),
                            "Message must identify the requested channel mode; was: " + msg),
                    () -> assertTrue(msg.contains("MONO"),
                            "Message must point the caller at ChannelMode.MONO; was: " + msg));
        } finally {
            source.close();
        }
    }

    // ---------------------------------------------------------------------
    // Sample rate outside [4000, 192000] — Req 17.3
    // ---------------------------------------------------------------------

    @Test
    void decodeRejectsSourceSampleRateBelowLowerBound() throws IOException {
        // RawPcmAudioSource accepts [1, 384000] so we can construct a source
        // at 2000 Hz; DtmfFileDecoder's own guard then rejects it.
        RawPcmAudioSource source = RawPcmAudioSource.fromPcm16LittleEndian(
                twoMonoZeroFramesPcm16(), 2_000, 1);
        try {
            UnsupportedAudioFormatException ex = assertThrows(
                    UnsupportedAudioFormatException.class,
                    () -> DtmfFileDecoder.decode(source, TELEPHONY));
            String msg = ex.getMessage();
            assertNotNull(msg, "UAFE message must not be null");
            assertAll(
                    () -> assertTrue(msg.contains("2000"),
                            "Message must name the offending rate '2000'; was: " + msg),
                    () -> assertTrue(msg.contains("4000") && msg.contains("192000"),
                            "Message must name the valid range [4000, 192000]; was: " + msg));
        } finally {
            source.close();
        }
    }

    // ---------------------------------------------------------------------
    // AudioSource overload does NOT close caller-supplied source — Req 8.12
    // ---------------------------------------------------------------------

    @Test
    void decodeAudioSourceDoesNotCloseCallerSuppliedSource() throws IOException {
        // Build a tiny decodable source (one generated "5" tone at 8 kHz) so
        // decode() runs its full happy path — the guards pass, read-all-frames
        // succeeds, DtmfDecoder produces at least one tone — and still the
        // caller's source stays open on return.
        byte[] pcm = pcm16LeBytesFor(DtmfGenerator.generate("5", TELEPHONY));
        RawPcmAudioSource backing = RawPcmAudioSource.fromPcm16LittleEndian(
                pcm, 8_000, 1);
        CloseCountingAudioSource tracked = new CloseCountingAudioSource(backing);
        try {
            DtmfFileDecoder.decode(tracked, TELEPHONY);

            assertEquals(0, tracked.closeCount(),
                    "decode(AudioSource, cfg) must not close the caller-supplied source "
                            + "(Req 8.12)");
            assertFalse(tracked.isClosed(),
                    "Caller-supplied source must remain open after decode returns "
                            + "(Req 8.12)");

            // Subsequent reads must still succeed on the caller's source:
            // a closed source would throw IOException from read(...). We
            // seek to 0 first to put the cursor back at the head.
            tracked.seek(0L);
            double[] scratch = new double[8];
            int read = tracked.read(scratch, 0, scratch.length);
            assertTrue(read >= 0,
                    "Caller-supplied source must still be readable after decode returns");
        } finally {
            tracked.close();
        }
    }

    // ---------------------------------------------------------------------
    // Stereo source + MONO config → downmix-via-averaging — Req 8.7
    // ---------------------------------------------------------------------

    /**
     * Builds a stereo PCM16 payload whose left channel carries a generated
     * "5" tone and whose right channel carries the same tone scaled by
     * {@code 0.3}. Feeding that payload through {@code DtmfFileDecoder} with
     * {@link ChannelMode#MONO} must produce the exact tone list that
     * {@link DtmfDecoder#decode(double[], DtmfConfig)} produces when the two
     * channels are averaged by hand and fed in as mono — i.e., the decoder
     * really is averaging {@code (L + R) / 2} (Req 8.7). Both paths route
     * through the same PCM16 ↔ double round-trip so sample-quantisation
     * differences cancel.
     */
    @Test
    void stereoSourceWithMonoConfigDownmixesViaAveraging() throws IOException {
        double[] mono = DtmfGenerator.generate("5", TELEPHONY);
        double[] left = mono;
        double[] right = scale(mono, 0.3);
        byte[] interleavedBytes = stereoInterleavedPcm16LeBytes(left, right);

        // Path A: decode stereo → MONO via DtmfFileDecoder (downmixes internally).
        List<DtmfTone> viaFileDecoder;
        try (RawPcmAudioSource src = RawPcmAudioSource.fromPcm16LittleEndian(
                interleavedBytes, 8_000, 2)) {
            viaFileDecoder = DtmfFileDecoder.decode(src, TELEPHONY);
        }

        // Path B: read the same stereo bytes as interleaved doubles, average
        // them by hand, then feed the resulting mono buffer to DtmfDecoder
        // directly. Any divergence between A and B would mean DtmfFileDecoder
        // is doing something other than (L + R) / 2.
        double[] interleavedDoubles = readAllInterleaved(interleavedBytes, 8_000, 2);
        double[] manuallyDownmixed = averageAdjacentPairs(interleavedDoubles);
        List<DtmfTone> viaManualDownmix = DtmfDecoder.decode(manuallyDownmixed, TELEPHONY);

        // Sanity: both paths detected at least one tone (round-trip works).
        assertFalse(viaFileDecoder.isEmpty(),
                "DtmfFileDecoder (MONO) must detect the generated '5' tone");
        assertFalse(viaManualDownmix.isEmpty(),
                "Manual-downmix reference must also detect the generated '5' tone");

        // Structural equality on the observable tone fields.
        assertTonesEqualStructurally(viaManualDownmix, viaFileDecoder,
                "DtmfFileDecoder with MONO config must produce the same tones as "
                        + "manual (L + R) / 2 downmix fed into DtmfDecoder directly (Req 8.7)");
    }

    // ---------------------------------------------------------------------
    // Stereo source + STEREO_DOWNMIX config → forward interleaved unchanged — Req 8.8
    // ---------------------------------------------------------------------

    /**
     * Builds a stereo PCM16 payload and decodes it twice: once through
     * {@code DtmfFileDecoder} with {@link ChannelMode#STEREO_DOWNMIX}, and
     * once directly through {@code DtmfDecoder.decode(double[], cfg)} on
     * the same interleaved buffer (read back through {@link
     * RawPcmAudioSource}) with the same config. If {@code DtmfFileDecoder}
     * is forwarding the interleaved buffer to {@code DtmfDecoder} unchanged
     * (Req 8.8), the two paths produce identical tone lists.
     */
    @Test
    void stereoSourceWithStereoDownmixConfigForwardsInterleavedUnchanged() throws IOException {
        double[] mono = DtmfGenerator.generate("5", TELEPHONY);
        double[] left = mono;
        double[] right = scale(mono, 0.3);
        byte[] interleavedBytes = stereoInterleavedPcm16LeBytes(left, right);

        DtmfConfig stereoDownmixCfg = DtmfConfig.advanced()
                .channelMode(ChannelMode.STEREO_DOWNMIX)
                .build();

        // Path A: DtmfFileDecoder with STEREO_DOWNMIX.
        List<DtmfTone> viaFileDecoder;
        try (RawPcmAudioSource src = RawPcmAudioSource.fromPcm16LittleEndian(
                interleavedBytes, 8_000, 2)) {
            viaFileDecoder = DtmfFileDecoder.decode(src, stereoDownmixCfg);
        }

        // Path B: feed the raw interleaved doubles directly into DtmfDecoder
        // with the same STEREO_DOWNMIX config — no DtmfFileDecoder in the
        // loop at all. DtmfDecoder does its own averaging for STEREO_DOWNMIX.
        double[] interleavedDoubles = readAllInterleaved(interleavedBytes, 8_000, 2);
        List<DtmfTone> viaDirectDecoder =
                DtmfDecoder.decode(interleavedDoubles, stereoDownmixCfg);

        assertFalse(viaFileDecoder.isEmpty(),
                "DtmfFileDecoder (STEREO_DOWNMIX) must detect the generated '5' tone");
        assertFalse(viaDirectDecoder.isEmpty(),
                "Direct DtmfDecoder reference must also detect the generated '5' tone");
        assertTonesEqualStructurally(viaDirectDecoder, viaFileDecoder,
                "DtmfFileDecoder with STEREO_DOWNMIX must forward the interleaved "
                        + "buffer unchanged; mismatched tone lists would mean the buffer "
                        + "was transformed before dispatch (Req 8.8)");
    }

    // ---------------------------------------------------------------------
    // Happy-path round-trip anchor
    // ---------------------------------------------------------------------

    @Test
    void roundTripThroughRawPcmAudioSourceDetectsGeneratedKey() throws IOException {
        // Generate the audio for a single "5" tone at 8 kHz, encode to PCM16
        // little-endian bytes, wrap in a RawPcmAudioSource, and decode.
        double[] audio = DtmfGenerator.generate("5", TELEPHONY);
        byte[] pcm = pcm16LeBytesFor(audio);

        List<DtmfTone> tones;
        try (RawPcmAudioSource src = RawPcmAudioSource.fromPcm16LittleEndian(
                pcm, 8_000, 1)) {
            tones = DtmfFileDecoder.decode(src, TELEPHONY);
        }

        assertFalse(tones.isEmpty(),
                "Round-trip through RawPcmAudioSource must detect the generated tone");
        assertEquals('5', tones.get(0).key(),
                "First detected tone's key must match the generated '5'");
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * Convert a normalised {@code double[]} in {@code [-1, 1]} to
     * little-endian signed PCM16 bytes. Uses the clamp-then-round scheme
     * shared with {@code WavEncoder} and other test fixtures.
     */
    private static byte[] pcm16LeBytesFor(double[] audio) {
        ByteBuffer bb = ByteBuffer.allocate(audio.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (double v : audio) {
            bb.putShort(toPcm16Sample(v));
        }
        return bb.array();
    }

    /** Build interleaved L,R,L,R,... PCM16 LE bytes from two equal-length
     *  channel buffers. */
    private static byte[] stereoInterleavedPcm16LeBytes(double[] left, double[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException(
                    "left/right length mismatch: " + left.length + " vs " + right.length);
        }
        ByteBuffer bb = ByteBuffer.allocate(left.length * 2 * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < left.length; i++) {
            bb.putShort(toPcm16Sample(left[i]));
            bb.putShort(toPcm16Sample(right[i]));
        }
        return bb.array();
    }

    /**
     * Clamp a normalised sample into the PCM16 signed range and round to
     * the nearest integer. Full-scale positive saturates at
     * {@link Short#MAX_VALUE}; full-scale negative saturates at
     * {@link Short#MIN_VALUE}.
     */
    private static short toPcm16Sample(double v) {
        long rounded = Math.round(v * 32_768.0);
        long clamped = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, rounded));
        return (short) clamped;
    }

    /**
     * Read every frame out of {@code bytes} via a fresh
     * {@link RawPcmAudioSource}, returning the interleaved {@code double[]}
     * that {@code DtmfFileDecoder} would have seen before any downmix.
     * Used by the Req 8.7 / 8.8 tests to build a reference path that shares
     * the PCM16 ↔ double round-trip with {@code DtmfFileDecoder} itself.
     */
    private static double[] readAllInterleaved(byte[] bytes, int sampleRate, int channelCount)
            throws IOException {
        try (RawPcmAudioSource src = RawPcmAudioSource.fromPcm16LittleEndian(
                bytes, sampleRate, channelCount)) {
            int totalFrames = (int) src.totalFrames();
            double[] buffer = new double[totalFrames * channelCount];
            int offset = 0;
            int remaining = totalFrames;
            while (remaining > 0) {
                int read = src.read(buffer, offset, remaining);
                if (read < 0) {
                    break;
                }
                offset += read * channelCount;
                remaining -= read;
            }
            return buffer;
        }
    }

    /** Average adjacent sample pairs of an interleaved stereo {@code double[]}
     *  into a mono buffer of half the length. */
    private static double[] averageAdjacentPairs(double[] interleaved) {
        if ((interleaved.length & 1) != 0) {
            throw new IllegalArgumentException(
                    "interleaved length must be even, was " + interleaved.length);
        }
        double[] mono = new double[interleaved.length / 2];
        for (int i = 0; i < mono.length; i++) {
            mono[i] = (interleaved[2 * i] + interleaved[2 * i + 1]) * 0.5;
        }
        return mono;
    }

    /** Return a fresh {@code double[]} equal to {@code in} scaled by {@code k}. */
    private static double[] scale(double[] in, double k) {
        double[] out = new double[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = in[i] * k;
        }
        return out;
    }

    /**
     * Assert two tone lists carry the same observable tone sequence: same
     * size, same {@code (key, startSample, endSample, sampleRate, channel)}
     * pairs in order. Confidence is a derived numeric quantity and is
     * allowed to differ up to floating-point rounding; for this test we do
     * not compare it.
     */
    private static void assertTonesEqualStructurally(
            List<DtmfTone> expected, List<DtmfTone> actual, String why) {
        assertEquals(expected.size(), actual.size(),
                why + " — tone-count mismatch (expected " + expected.size()
                        + ", actual " + actual.size() + ")");
        for (int i = 0; i < expected.size(); i++) {
            DtmfTone e = expected.get(i);
            DtmfTone a = actual.get(i);
            final int index = i;
            assertAll(why + " — tone[" + index + "] mismatch",
                    () -> assertEquals(e.key(), a.key(),
                            "key at index " + index),
                    () -> assertEquals(e.startSample(), a.startSample(),
                            "startSample at index " + index),
                    () -> assertEquals(e.endSample(), a.endSample(),
                            "endSample at index " + index),
                    () -> assertEquals(e.sampleRate(), a.sampleRate(),
                            "sampleRate at index " + index),
                    () -> assertEquals(e.channel(), a.channel(),
                            "channel at index " + index));
        }
    }

    /** Assert an NPE names the given parameter. */
    private static void assertNpeNames(NullPointerException npe, String param) {
        String msg = npe.getMessage();
        assertNotNull(msg, "NPE message must not be null for parameter '" + param + "'");
        assertTrue(msg.contains(param),
                "NPE message must identify the '" + param
                        + "' parameter; was: " + msg);
    }

    private static void closeQuietly(AudioSource s) {
        try {
            s.close();
        } catch (IOException ignored) {
            // Not of interest in these tests.
        }
    }

    // ---------------------------------------------------------------------
    // Test-only AudioSource wrapper used by the "does not close" test
    // ---------------------------------------------------------------------

    /**
     * {@link AudioSource} decorator that counts {@link #close()} invocations
     * and exposes whether the source is currently observed as closed.
     * Delegates everything else verbatim to the wrapped source so the
     * decoder sees exactly the behaviour of the underlying
     * {@link RawPcmAudioSource} in every test that uses this double.
     *
     * <p>Kept as a static nested class on the test so it is only wired up
     * where the test actually needs it — production code has no reason to
     * wrap an {@code AudioSource} for close tracking.
     */
    private static final class CloseCountingAudioSource implements AudioSource {

        private final AudioSource delegate;
        private int closeCount = 0;

        CloseCountingAudioSource(AudioSource delegate) {
            this.delegate = delegate;
        }

        int closeCount() {
            return closeCount;
        }

        boolean isClosed() {
            return closeCount > 0;
        }

        @Override
        public int sampleRate() {
            return delegate.sampleRate();
        }

        @Override
        public int channelCount() {
            return delegate.channelCount();
        }

        @Override
        public int bitDepth() {
            return delegate.bitDepth();
        }

        @Override
        public long totalFrames() {
            return delegate.totalFrames();
        }

        @Override
        public int read(double[] buffer, int offset, int length) throws IOException {
            return delegate.read(buffer, offset, length);
        }

        @Override
        public boolean canSeek() {
            return delegate.canSeek();
        }

        @Override
        public void seek(long frameIndex) throws IOException {
            delegate.seek(frameIndex);
        }

        @Override
        public long currentFrame() {
            return delegate.currentFrame();
        }

        @Override
        public void close() throws IOException {
            closeCount++;
            delegate.close();
        }
    }
}
