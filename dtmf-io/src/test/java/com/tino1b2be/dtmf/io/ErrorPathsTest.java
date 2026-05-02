package com.tino1b2be.dtmf.io;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tino1b2be.dtmf.DtmfConfig;
import com.tino1b2be.dtmf.io.wav.WavAudioSourceProvider;

/**
 * Error-path coverage test suite for {@code dtmf-io} (Task 9.1).
 *
 * <p>Anchors the error-handling contract of Requirement 12 in one
 * place, exercising every public entry point on this module:
 *
 * <ul>
 *   <li>{@link AudioSources#open(Path)},
 *       {@link AudioSources#open(InputStream, String)},
 *       {@link AudioSources#open(URL)} — facade overloads;</li>
 *   <li>{@link DtmfFileDecoder#decode(Path, DtmfConfig)},
 *       {@link DtmfFileDecoder#decode(InputStream, String, DtmfConfig)},
 *       {@link DtmfFileDecoder#decode(URL, DtmfConfig)},
 *       {@link DtmfFileDecoder#decode(AudioSource, DtmfConfig)} — glue
 *       overloads;</li>
 *   <li>{@link RawPcmAudioSource}'s six-argument constructor and
 *       {@link RawPcmAudioSource#fromPcm16LittleEndian(byte[], int, int)
 *       fromPcm16LittleEndian} factory.</li>
 * </ul>
 *
 * <p>Each requirement exercised:
 *
 * <dl>
 *   <dt><b>Req 12.1 — {@link NullPointerException}s name the parameter.</b></dt>
 *   <dd>Every public entry point is called with each required argument
 *       null in turn; the thrown NPE's message must contain the
 *       parameter name.</dd>
 *
 *   <dt><b>Req 12.2 — {@link IllegalArgumentException}s state the
 *       domain.</b></dt>
 *   <dd>Out-of-range numeric inputs to {@link RawPcmAudioSource} and
 *       {@link RawPcmAudioSource#seek(long)} throw IAE whose message
 *       identifies the offending value and the accepted range or set.</dd>
 *
 *   <dt><b>Req 12.3 — {@link IOException} is surfaced, not wrapped.</b></dt>
 *   <dd>Two sub-cases:
 *       <ol>
 *         <li>{@link AudioSources#open(Path)} against a missing file
 *             propagates as {@link NoSuchFileException} (a subclass of
 *             {@code IOException}) rather than being folded into
 *             {@link UnsupportedAudioFormatException} — the design's
 *             "File-not-found special case" (see {@link AudioSources}'
 *             class Javadoc). This is verified through the
 *             package-private
 *             {@link AudioSources#openForTesting(Path, List)} seam with a
 *             fake provider that reads the file header via
 *             {@link Files#newByteChannel(Path, java.nio.file.OpenOption...)
 *             Files.newByteChannel} so the missing-file signal surfaces
 *             naturally; the unit-test source set does not have real
 *             providers registered via {@link java.util.ServiceLoader}.</li>
 *         <li>A structurally malformed WAV (missing {@code data} chunk)
 *             fed to the real {@link WavAudioSourceProvider} throws a
 *             plain {@link IOException}, not
 *             {@link UnsupportedAudioFormatException} — per Requirement
 *             9.11 structural defects are I/O failures, not
 *             format-level failures.</li>
 *       </ol></dd>
 *
 *   <dt><b>Req 12.4 — {@link UnsupportedAudioFormatException} is
 *       distinct.</b></dt>
 *   <dd>A WAV file with {@code wFormatTag == 0x0007} (μ-law) fed to the
 *       real {@link WavAudioSourceProvider} throws
 *       {@link UnsupportedAudioFormatException} — per Requirement 9.10
 *       compressed encodings are format-level rejections, not I/O
 *       failures.</dd>
 *
 *   <dt><b>Req 12.5 — {@link IOException} is not swallowed or
 *       logged.</b></dt>
 *   <dd>An {@link IOException} thrown from an
 *       {@link AudioSourceProvider#open(InputStream, String) open}
 *       method propagates through
 *       {@link DtmfFileDecoder#decodeForTesting(InputStream, String,
 *       DtmfConfig, List) DtmfFileDecoder} verbatim, with its
 *       cause chain intact.</dd>
 *
 *   <dt><b>Req 12.6 — Logger name is
 *       {@code com.tino1b2be.dtmf.io.AudioSources}.</b></dt>
 *   <dd>Provider-discovery warnings (raised when a provider's
 *       {@code canOpen(InputStream, String)} throws {@link IOException})
 *       land on the logger named exactly
 *       {@code com.tino1b2be.dtmf.io.AudioSources} — verified by
 *       attaching a {@link Handler} to that named logger and triggering
 *       a warning through the
 *       {@link AudioSources#openForTesting(InputStream, String, List)}
 *       seam with a fake provider that throws from {@code canOpen}.</dd>
 * </dl>
 *
 * <p>Some null/out-of-range checks are already covered by
 * {@code DtmfFileDecoderTest}, {@code RawPcmAudioSourceTest}, and
 * {@code AudioSourcesTest}. Deduplication is fine but not required;
 * this test anchors the full Req 12 contract in one place so a
 * future change that accidentally loosens any error invariant fails
 * a single test class rather than the reader having to cross-reference
 * five others.
 *
 * <p>Validates: Requirements 12.1, 12.2, 12.3, 12.4, 12.5, 12.6.
 */
class ErrorPathsTest {

    // =========================================================================
    // Shared fixtures
    // =========================================================================

    /** Standard telephony config: 8 kHz, MONO, RECTANGULAR, etc. */
    private static final DtmfConfig TELEPHONY = DtmfConfig.forTelephony();

    /** Logger that {@code AudioSources} publishes WARNING records to (Req 12.6). */
    private static final Logger AUDIO_SOURCES_LOGGER =
            Logger.getLogger(AudioSources.class.getName());

    /** Log capture plumbing used by the Req 12.6 test. */
    private CapturingHandler capturingHandler;
    private Level priorLevel;
    private boolean priorUseParent;

    @BeforeEach
    void attachLoggingCapture() {
        capturingHandler = new CapturingHandler();
        priorLevel = AUDIO_SOURCES_LOGGER.getLevel();
        priorUseParent = AUDIO_SOURCES_LOGGER.getUseParentHandlers();
        AUDIO_SOURCES_LOGGER.setLevel(Level.ALL);
        AUDIO_SOURCES_LOGGER.setUseParentHandlers(false);
        AUDIO_SOURCES_LOGGER.addHandler(capturingHandler);
    }

    @AfterEach
    void detachLoggingCapture() {
        AUDIO_SOURCES_LOGGER.removeHandler(capturingHandler);
        AUDIO_SOURCES_LOGGER.setUseParentHandlers(priorUseParent);
        AUDIO_SOURCES_LOGGER.setLevel(priorLevel);
    }

    // =========================================================================
    // Requirement 12.1 — NullPointerException names the parameter
    // =========================================================================

    /**
     * {@link AudioSources#open(Path) AudioSources.open} / {@code open(InputStream,
     * String)} / {@code open(URL)} all null-check every required argument
     * via {@code Objects.requireNonNull} and raise {@link NullPointerException}
     * identifying the parameter.
     */
    @Nested
    @DisplayName("Req 12.1 — NPE naming the parameter on every public entry point")
    class NullPointerExceptionsNameParameters {

        // --- AudioSources --------------------------------------------------

        @Test
        @DisplayName("AudioSources.open(Path) rejects null path")
        void audioSourcesOpenPathNullPath() {
            NullPointerException npe = assertThrows(
                    NullPointerException.class,
                    () -> AudioSources.open((Path) null));
            assertNpeNames(npe, "path");
        }

        @Test
        @DisplayName("AudioSources.open(InputStream, String) rejects null stream")
        void audioSourcesOpenStreamNullStream() {
            NullPointerException npe = assertThrows(
                    NullPointerException.class,
                    () -> AudioSources.open((InputStream) null, /* hint */ "x.wav"));
            assertNpeNames(npe, "stream");
        }

        @Test
        @DisplayName("AudioSources.open(URL) rejects null URL")
        void audioSourcesOpenUrlNullUrl() {
            NullPointerException npe = assertThrows(
                    NullPointerException.class,
                    () -> AudioSources.open((URL) null));
            assertNpeNames(npe, "url");
        }

        // --- DtmfFileDecoder -----------------------------------------------

        @Test
        @DisplayName("DtmfFileDecoder.decode(Path, DtmfConfig) rejects null path")
        void decodePathNullPath() {
            NullPointerException npe = assertThrows(
                    NullPointerException.class,
                    () -> DtmfFileDecoder.decode((Path) null, TELEPHONY));
            assertNpeNames(npe, "path");
        }

        @Test
        @DisplayName("DtmfFileDecoder.decode(Path, DtmfConfig) rejects null config")
        void decodePathNullConfig() {
            Path any = Path.of("does-not-matter.wav");
            NullPointerException npe = assertThrows(
                    NullPointerException.class,
                    () -> DtmfFileDecoder.decode(any, null));
            assertNpeNames(npe, "config");
        }

        @Test
        @DisplayName("DtmfFileDecoder.decode(InputStream, String, DtmfConfig) rejects null stream")
        void decodeInputStreamNullStream() {
            NullPointerException npe = assertThrows(
                    NullPointerException.class,
                    () -> DtmfFileDecoder.decode(
                            (InputStream) null, /* hint */ "x.wav", TELEPHONY));
            assertNpeNames(npe, "stream");
        }

        @Test
        @DisplayName("DtmfFileDecoder.decode(InputStream, String, DtmfConfig) rejects null config")
        void decodeInputStreamNullConfig() {
            NullPointerException npe = assertThrows(
                    NullPointerException.class,
                    () -> DtmfFileDecoder.decode(
                            new ByteArrayInputStream(new byte[0]),
                            /* hint */ "x.wav",
                            null));
            assertNpeNames(npe, "config");
        }

        @Test
        @DisplayName("DtmfFileDecoder.decode(URL, DtmfConfig) rejects null url")
        void decodeUrlNullUrl() {
            NullPointerException npe = assertThrows(
                    NullPointerException.class,
                    () -> DtmfFileDecoder.decode((URL) null, TELEPHONY));
            assertNpeNames(npe, "url");
        }

        @Test
        @DisplayName("DtmfFileDecoder.decode(URL, DtmfConfig) rejects null config")
        void decodeUrlNullConfig() throws Exception {
            URL url = new URL("file:/does-not-matter.wav");
            NullPointerException npe = assertThrows(
                    NullPointerException.class,
                    () -> DtmfFileDecoder.decode(url, null));
            assertNpeNames(npe, "config");
        }

        @Test
        @DisplayName("DtmfFileDecoder.decode(AudioSource, DtmfConfig) rejects null source")
        void decodeAudioSourceNullSource() {
            NullPointerException npe = assertThrows(
                    NullPointerException.class,
                    () -> DtmfFileDecoder.decode((AudioSource) null, TELEPHONY));
            assertNpeNames(npe, "source");
        }

        @Test
        @DisplayName("DtmfFileDecoder.decode(AudioSource, DtmfConfig) rejects null config")
        void decodeAudioSourceNullConfig() {
            RawPcmAudioSource source = RawPcmAudioSource.fromPcm16LittleEndian(
                    zeroFramePcm16Bytes(), 8_000, 1);
            try {
                NullPointerException npe = assertThrows(
                        NullPointerException.class,
                        () -> DtmfFileDecoder.decode(source, null));
                assertNpeNames(npe, "config");
            } finally {
                closeQuietly(source);
            }
        }

        // --- RawPcmAudioSource constructor ---------------------------------

        @Test
        @DisplayName("RawPcmAudioSource constructor rejects null data")
        void rawPcmConstructorNullData() {
            NullPointerException npe = assertThrows(
                    NullPointerException.class,
                    () -> new RawPcmAudioSource(
                            /* data */ null, 8_000, 16,
                            ByteOrder.LITTLE_ENDIAN, 1, PcmEncoding.SIGNED_INT));
            assertNpeNames(npe, "data");
        }

        @Test
        @DisplayName("RawPcmAudioSource constructor rejects null byteOrder")
        void rawPcmConstructorNullByteOrder() {
            NullPointerException npe = assertThrows(
                    NullPointerException.class,
                    () -> new RawPcmAudioSource(
                            new byte[2], 8_000, 16,
                            /* byteOrder */ null, 1, PcmEncoding.SIGNED_INT));
            assertNpeNames(npe, "byteOrder");
        }

        @Test
        @DisplayName("RawPcmAudioSource constructor rejects null encoding")
        void rawPcmConstructorNullEncoding() {
            NullPointerException npe = assertThrows(
                    NullPointerException.class,
                    () -> new RawPcmAudioSource(
                            new byte[2], 8_000, 16,
                            ByteOrder.LITTLE_ENDIAN, 1, /* encoding */ null));
            assertNpeNames(npe, "encoding");
        }

        // --- RawPcmAudioSource.fromPcm16LittleEndian -----------------------

        @Test
        @DisplayName("RawPcmAudioSource.fromPcm16LittleEndian rejects null data")
        void rawPcmFactoryNullData() {
            NullPointerException npe = assertThrows(
                    NullPointerException.class,
                    () -> RawPcmAudioSource.fromPcm16LittleEndian(
                            /* data */ null, 8_000, 1));
            assertNpeNames(npe, "data");
        }
    }

    // =========================================================================
    // Requirement 12.2 — IllegalArgumentException states the domain
    // =========================================================================

    /**
     * Out-of-domain numeric inputs on {@link RawPcmAudioSource} and
     * {@link RawPcmAudioSource#seek(long)} throw {@link IllegalArgumentException}
     * whose message identifies the offending value and the accepted range
     * or set.
     */
    @Nested
    @DisplayName("Req 12.2 — IAE names parameter and accepted domain")
    class IllegalArgumentExceptionsStateDomain {

        @Test
        @DisplayName("sampleRate outside [1, 384000]")
        void sampleRateOutOfRange() {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> new RawPcmAudioSource(
                            new byte[2], -1, 16,
                            ByteOrder.LITTLE_ENDIAN, 1, PcmEncoding.SIGNED_INT));
            assertIaeNamesValueAndRange(ex, "sampleRate", "-1",
                    /* rangeHints */ "1", "384000");
        }

        @Test
        @DisplayName("channelCount outside [1, 8]")
        void channelCountOutOfRange() {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> new RawPcmAudioSource(
                            new byte[2], 8_000, 16,
                            ByteOrder.LITTLE_ENDIAN, 9, PcmEncoding.SIGNED_INT));
            assertIaeNamesValueAndRange(ex, "channelCount", "9", "1", "8");
        }

        @Test
        @DisplayName("bitDepth outside {16, 24, 32, 64}")
        void bitDepthOutOfSet() {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> new RawPcmAudioSource(
                            new byte[2], 8_000, 8,
                            ByteOrder.LITTLE_ENDIAN, 1, PcmEncoding.SIGNED_INT));
            assertIaeNamesValueAndSet(ex, "bitDepth", "8",
                    /* setHints */ "16", "24", "32", "64");
        }

        @Test
        @DisplayName("IEEE_FLOAT with bitDepth outside {32, 64}")
        void ieeeFloatInvalidBitDepth() {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> new RawPcmAudioSource(
                            new byte[2], 8_000, 16,
                            ByteOrder.LITTLE_ENDIAN, 1, PcmEncoding.IEEE_FLOAT));
            String message = ex.getMessage();
            assertNotNull(message, "IAE must carry a detail message");
            assertAll(
                    () -> assertTrue(message.contains("IEEE_FLOAT"),
                            "Message must identify the encoding; was: " + message),
                    () -> assertTrue(message.contains("32") && message.contains("64"),
                            "Message must state the {32, 64} valid bit-depth set; "
                                    + "was: " + message),
                    () -> assertTrue(message.contains("16"),
                            "Message must identify the offending bitDepth 16; was: "
                                    + message));
        }

        @Test
        @DisplayName("data.length is not a multiple of bytesPerFrame")
        void dataLengthNotMultipleOfFrame() {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> new RawPcmAudioSource(
                            new byte[3], 8_000, 16,
                            ByteOrder.LITTLE_ENDIAN, 1, PcmEncoding.SIGNED_INT));
            String message = ex.getMessage();
            assertNotNull(message, "IAE must carry a detail message");
            assertAll(
                    () -> assertTrue(message.contains("3"),
                            "Message must identify data.length=3; was: " + message),
                    () -> assertTrue(message.contains("2") || message.contains("bytesPerFrame"),
                            "Message must identify the frame size; was: " + message));
        }

        @Test
        @DisplayName("seek(-1) on a seekable source — IAE names the frameIndex and range")
        void seekNegativeFrameIndex() throws IOException {
            RawPcmAudioSource source = RawPcmAudioSource.fromPcm16LittleEndian(
                    zeroFramePcm16Bytes(), 8_000, 1);
            try {
                IllegalArgumentException ex = assertThrows(
                        IllegalArgumentException.class,
                        () -> source.seek(-1L));
                String message = ex.getMessage();
                assertNotNull(message, "IAE must carry a detail message");
                assertAll(
                        () -> assertTrue(message.contains("-1"),
                                "Message must identify the offending frameIndex '-1'; "
                                        + "was: " + message),
                        () -> assertTrue(
                                message.toLowerCase().contains("frame")
                                        || message.contains("0"),
                                "Message must identify the valid range starting at 0; "
                                        + "was: " + message));
            } finally {
                source.close();
            }
        }
    }

    // =========================================================================
    // Requirement 12.3 — file-not-found propagates as NoSuchFileException
    // =========================================================================

    /**
     * {@link AudioSources#open(Path)} against a non-existent path SHALL
     * propagate the underlying {@link NoSuchFileException} (a subclass
     * of {@link IOException}) rather than folding it into
     * {@link UnsupportedAudioFormatException}. This is the design's
     * "File-not-found special case": callers can distinguish missing
     * files from format-level rejections without unwrapping
     * {@link UnsupportedAudioFormatException#getCause()}.
     *
     * <p>Exercised through the
     * {@link AudioSources#openForTesting(Path, List)} seam with a fake
     * provider that actually opens the file in {@code canOpen(Path)} —
     * the unit-test source set does not have a {@link java.util.ServiceLoader}-
     * registered provider that touches the filesystem, so the "real" WAV
     * provider's header-reading behaviour is reproduced here by a
     * {@link FilesystemTouchingProvider} double. The
     * {@code FilesystemTouchingProvider} opens the file via
     * {@link Files#newByteChannel}, which raises
     * {@link NoSuchFileException} for a missing path; the facade's
     * special case then re-throws that captured exception verbatim.
     *
     * <p>Validates: Requirement 12.3 (first of two sub-cases; the
     * "missing data chunk" case is verified in
     * {@link StructurallyMalformedWavPropagatesAsIoException}).
     */
    @Test
    @DisplayName("Req 12.3 — AudioSources.open(missing path) propagates as NoSuchFileException")
    void noSuchFilePropagatesVerbatim(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist.wav");

        FilesystemTouchingProvider wav = new FilesystemTouchingProvider("WAV");

        NoSuchFileException thrown = assertThrows(
                NoSuchFileException.class,
                () -> AudioSources.openForTesting(missing, List.of(wav)),
                "AudioSources.open(Path) against a missing file must propagate "
                        + "NoSuchFileException verbatim (design 'File-not-found "
                        + "special case'; Req 12.3, 12.4) rather than wrapping "
                        + "it as UnsupportedAudioFormatException");

        // The provider's canOpen was invoked (otherwise we'd have seen an
        // UnsupportedAudioFormatException from the 'empty providers' path).
        assertEquals(1, wav.canOpenPathCallCount(),
                "The provider's canOpen(Path) must have been invoked exactly once");

        // Message identifies the missing file.
        String message = thrown.getMessage();
        assertNotNull(message, "NoSuchFileException must carry a detail message");
        assertTrue(message.contains("does-not-exist.wav"),
                "NoSuchFileException's message must identify the missing file; "
                        + "was: " + message);
    }

    // =========================================================================
    // Requirement 12.4 — μ-law WAV → UnsupportedAudioFormatException
    // =========================================================================

    /**
     * A WAV file with {@code wFormatTag == 0x0007} (μ-law compression)
     * SHALL be rejected by the real {@link WavAudioSourceProvider} with
     * {@link UnsupportedAudioFormatException}, NOT with a plain
     * {@link IOException}. This anchors the format-level vs
     * disk-level distinction of Requirement 12.4.
     *
     * <p>The byte fixture is hand-built in-memory with the canonical
     * {@code RIFF / WAVE / fmt  / data} layout, {@code wFormatTag} set
     * to {@code 0x0007}, and 8-bit samples (μ-law's native container);
     * no {@link java.io.File} is involved. The provider is instantiated
     * directly via {@code new} (Requirement 4.1: public no-arg
     * constructor) and fed the bytes through its public stream overload.
     *
     * <p>Validates: Requirement 12.4.
     */
    @Test
    @DisplayName("Req 12.4 — μ-law WAV throws UnsupportedAudioFormatException, not IOException")
    void mulawWavThrowsUnsupportedAudioFormatException() {
        byte[] mulawWav = buildMulawWavBytes();

        WavAudioSourceProvider wav = new WavAudioSourceProvider();

        UnsupportedAudioFormatException ex = assertThrows(
                UnsupportedAudioFormatException.class,
                () -> {
                    try (AudioSource source = wav.open(
                            new ByteArrayInputStream(mulawWav), /* hint */ null)) {
                        // open() must throw before we ever read; close in
                        // case the assertion is tripped by a future
                        // regression that lets the source escape.
                        source.read(new double[1]);
                    }
                },
                "μ-law WAV must be rejected by UnsupportedAudioFormatException "
                        + "(format-level rejection; Req 9.10, 12.4), not by a bare "
                        + "IOException (disk-level)");

        // Anchor the subtype invariant. UnsupportedAudioFormatException
        // extends IOException, so `instanceof IOException` is also true;
        // the point of this assertion is that the thrown type is
        // *strictly* the subclass — callers who catch
        // UnsupportedAudioFormatException ahead of IOException must see
        // this case land in the subclass branch.
        assertTrue(ex instanceof IOException,
                "UnsupportedAudioFormatException must remain an IOException subtype (Req 6.1)");

        String message = ex.getMessage();
        assertNotNull(message, "Message must not be null");
        assertAll(
                () -> assertTrue(message.contains("0x0007"),
                        "Message must identify the compression tag '0x0007'; was: "
                                + message),
                () -> assertTrue(message.toLowerCase().contains("mu-law")
                                || message.toLowerCase().contains("mulaw")
                                || message.toLowerCase().contains("μ-law"),
                        "Message must identify the compression as μ-law; was: "
                                + message));
    }

    // =========================================================================
    // Requirement 12.3 — missing data chunk → IOException (NOT UAFE)
    // =========================================================================

    /**
     * A structurally malformed WAV — one with a valid {@code RIFF/WAVE}
     * header and a {@code fmt } chunk but no {@code data} chunk — SHALL
     * be rejected by the real {@link WavAudioSourceProvider} with a
     * plain {@link IOException}, NOT
     * {@link UnsupportedAudioFormatException}. This is the inverse of
     * the μ-law case: structural defects are I/O-level failures
     * (Requirement 9.11), not format-level ones; folding them into
     * {@code UnsupportedAudioFormatException} would make it impossible
     * for callers to distinguish a real disk failure from a corrupted
     * file at the type level.
     *
     * <p>Validates: Requirement 12.3 (second sub-case).
     */
    @Test
    @DisplayName("Req 12.3 — malformed WAV (missing data chunk) throws IOException, not UAFE")
    void malformedWavMissingDataChunkThrowsIoException() {
        byte[] malformed = buildWavMissingDataChunkBytes();

        WavAudioSourceProvider wav = new WavAudioSourceProvider();

        IOException ex = assertThrows(
                IOException.class,
                () -> {
                    try (AudioSource source = wav.open(
                            new ByteArrayInputStream(malformed), /* hint */ null)) {
                        source.read(new double[1]);
                    }
                },
                "Missing data chunk must be rejected by IOException "
                        + "(structural defect; Req 9.11, 12.3), not by "
                        + "UnsupportedAudioFormatException (format-level)");

        assertFalse(ex instanceof UnsupportedAudioFormatException,
                "Structural defect must NOT be surfaced as "
                        + "UnsupportedAudioFormatException (Req 12.3 vs 12.4)");

        String message = ex.getMessage();
        assertNotNull(message, "IOException must carry a detail message (Req 9.11)");
        assertTrue(message.contains("data"),
                "Message must identify the missing 'data' chunk; was: " + message);
    }

    // =========================================================================
    // Requirement 12.5 — IOException propagates with cause chain intact
    // =========================================================================

    /**
     * An {@link IOException} thrown from an
     * {@link AudioSourceProvider#open(InputStream, String) AudioSourceProvider.open}
     * method SHALL propagate through
     * {@link DtmfFileDecoder#decode(InputStream, String, DtmfConfig)
     * DtmfFileDecoder} verbatim — same exception instance, same message,
     * same cause — rather than being swallowed or logged or wrapped.
     *
     * <p>The test drives a seeded cause chain {@code IOException ->
     * RuntimeException("root")} through the
     * {@link DtmfFileDecoder#decodeForTesting(InputStream, String,
     * DtmfConfig, List) decodeForTesting} seam (which exercises the same
     * try-with-resources block as the production
     * {@code decode(InputStream, String, DtmfConfig)} overload) and
     * asserts the thrown exception is the same instance with its
     * original cause preserved.
     *
     * <p>Validates: Requirement 12.5.
     */
    @Test
    @DisplayName("Req 12.5 — IOException from provider.open propagates with cause chain intact")
    void ioExceptionFromOpenIsNotSwallowedOrWrapped() {
        RuntimeException root = new RuntimeException("root cause");
        IOException thrown = new IOException("disk error while decoding", root);

        ThrowingOpenProvider provider = new ThrowingOpenProvider("WAV", thrown);

        IOException propagated = assertThrows(
                IOException.class,
                () -> DtmfFileDecoder.decodeForTesting(
                        new ByteArrayInputStream(new byte[] { 'R', 'I', 'F', 'F' }),
                        /* hint */ null,
                        TELEPHONY,
                        List.of(provider)),
                "IOException from provider.open(...) must propagate verbatim (Req 12.5)");

        assertSame(thrown, propagated,
                "The exact IOException instance thrown by provider.open(...) must be the "
                        + "one caught by the caller — no wrapping, no replacement (Req 12.5)");
        assertSame(root, propagated.getCause(),
                "The original cause chain must be preserved (Req 12.5)");
        assertEquals("disk error while decoding", propagated.getMessage(),
                "The original message must be preserved (Req 12.5)");
    }

    // =========================================================================
    // Requirement 12.6 — Logger name is com.tino1b2be.dtmf.io.AudioSources
    // =========================================================================

    /**
     * Provider-discovery warnings SHALL land on the logger named
     * {@code com.tino1b2be.dtmf.io.AudioSources} (Requirement 12.6). The
     * test attaches a {@link CapturingHandler} to the named logger
     * before the call and triggers a warning by driving an
     * {@link AudioSourceProvider} whose {@code canOpen(InputStream,
     * String)} throws {@link IOException} through the
     * {@link AudioSources#openForTesting(InputStream, String, List)}
     * seam. The facade catches the exception, scores the provider as
     * {@code -1}, and logs a {@code WARNING} on the named logger
     * (Requirement 5.9). The handler must then see at least one record
     * whose source logger is exactly the named logger.
     *
     * <p>Validates: Requirement 12.6.
     */
    @Test
    @DisplayName("Req 12.6 — provider-discovery warnings log under "
            + "com.tino1b2be.dtmf.io.AudioSources")
    void providerDiscoveryWarningsUseNamedLogger() throws IOException {
        // Provider whose canOpen(InputStream, String) throws IOException
        // so the facade's WARNING-logging branch fires (Req 5.9).
        ThrowingCanOpenStreamProvider throwing =
                new ThrowingCanOpenStreamProvider("WAV",
                        new IOException("synthetic canOpen failure"));

        // Drive the facade through the testing seam. Every provider
        // returns -1 (by throwing), so the call throws UAFE — but the
        // point of this test is the log, not the return path.
        assertThrows(UnsupportedAudioFormatException.class,
                () -> AudioSources.openForTesting(
                        new ByteArrayInputStream(new byte[] { 'R', 'I', 'F', 'F' }),
                        /* hint */ null,
                        List.of(throwing)));

        // Flush for good measure, then assert.
        capturingHandler.flush();

        List<LogRecord> warnings = capturingHandler.warnings();
        assertFalse(warnings.isEmpty(),
                "A WARNING must be logged when canOpen(InputStream, String) throws "
                        + "IOException (Req 5.9, 12.6); captured records: " + warnings);

        // Every captured record must carry the exact named-logger name —
        // that's the Req 12.6 invariant being anchored.
        String expectedLoggerName = "com.tino1b2be.dtmf.io.AudioSources";
        for (LogRecord r : warnings) {
            assertEquals(expectedLoggerName, r.getLoggerName(),
                    "Req 12.6 — provider-discovery warnings must be logged on "
                            + "the logger named '" + expectedLoggerName
                            + "'; found record under logger '"
                            + r.getLoggerName() + "': " + r.getMessage());
        }

        // Belt-and-braces: at least one record must mention the throwing
        // provider by formatName(), per Req 5.9.
        assertTrue(
                warnings.stream()
                        .anyMatch(r -> r.getMessage() != null
                                && r.getMessage().contains("WAV")),
                "At least one WARNING must identify the throwing provider's "
                        + "formatName() 'WAV' (Req 5.9); captured: " + warnings);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** One-frame valid mono PCM16 payload — just enough to construct a source. */
    private static byte[] zeroFramePcm16Bytes() {
        return new byte[] { 0, 0 };
    }

    /** Assert an NPE's message identifies the parameter by name. */
    private static void assertNpeNames(NullPointerException npe, String param) {
        String msg = npe.getMessage();
        assertNotNull(msg, "NPE message must not be null for parameter '" + param + "'");
        assertTrue(msg.contains(param),
                "NPE message must identify the '" + param + "' parameter (Req 12.1); "
                        + "was: " + msg);
    }

    /**
     * Assert an IAE identifies the parameter name, the offending value,
     * and every range endpoint (Req 12.2).
     */
    private static void assertIaeNamesValueAndRange(
            IllegalArgumentException ex, String param, String value,
            String lowBound, String highBound) {
        String msg = ex.getMessage();
        assertNotNull(msg, "IAE message must not be null");
        assertAll(
                () -> assertTrue(msg.contains(param),
                        "Message must identify the '" + param + "' parameter; was: " + msg),
                () -> assertTrue(msg.contains(value),
                        "Message must identify the offending value '" + value + "'; was: "
                                + msg),
                () -> assertTrue(msg.contains(lowBound),
                        "Message must identify the lower bound '" + lowBound + "'; was: "
                                + msg),
                () -> assertTrue(msg.contains(highBound),
                        "Message must identify the upper bound '" + highBound + "'; was: "
                                + msg));
    }

    /**
     * Assert an IAE identifies the parameter name, the offending value,
     * and every element of the valid set (Req 12.2).
     */
    private static void assertIaeNamesValueAndSet(
            IllegalArgumentException ex, String param, String value, String... setHints) {
        String msg = ex.getMessage();
        assertNotNull(msg, "IAE message must not be null");
        assertAll(
                () -> assertTrue(msg.contains(param),
                        "Message must identify the '" + param + "' parameter; was: " + msg),
                () -> assertTrue(msg.contains(value),
                        "Message must identify the offending value '" + value + "'; was: "
                                + msg));
        for (String s : setHints) {
            assertTrue(msg.contains(s),
                    "Message must identify valid-set element '" + s + "'; was: " + msg);
        }
    }

    private static void closeQuietly(AudioSource s) {
        try {
            s.close();
        } catch (IOException ignored) {
            // not of interest in these tests
        }
    }

    // =========================================================================
    // WAV byte fixtures (μ-law and missing data chunk)
    // =========================================================================
    //
    // Kept inline rather than delegated to `WavEncoder` (which lives in
    // dtmf-io-wav/src/test and is not packaged for cross-module reuse)
    // because this test module deliberately avoids any dependency on
    // another module's test source set. Both fixtures are small enough
    // that the parallel-implementation risk is negligible.

    private static final short WAVE_FORMAT_PCM = 0x0001;
    private static final short WAVE_FORMAT_MULAW = 0x0007;

    /**
     * Build a WAV byte fixture with {@code wFormatTag == 0x0007} (μ-law).
     * Layout: {@code RIFF | size | WAVE | fmt  | 16 | <classic fmt
     * payload> | data | <payload>}. 8-bit samples matching μ-law's
     * natural container, so the block-align and bit-depth checks pass
     * before the format-tag rejection fires.
     */
    private static byte[] buildMulawWavBytes() {
        byte[] samples = new byte[] { 0x00, 0x00 };
        int fmtBlockSize = 8 + 16;
        int dataBlockSize = 8 + samples.length;
        int total = 12 + fmtBlockSize + dataBlockSize;

        ByteBuffer buf = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        putAscii(buf, "RIFF");
        buf.putInt(total - 8);
        putAscii(buf, "WAVE");
        putClassicFmt(buf, WAVE_FORMAT_MULAW, /* channels */ 1,
                /* sampleRate */ 8_000, /* bitsPerSample */ 8);
        putAscii(buf, "data");
        buf.putInt(samples.length);
        buf.put(samples);
        return buf.array();
    }

    /**
     * Build a WAV byte fixture with a valid {@code RIFF/WAVE} outer
     * header and {@code fmt } chunk but NO {@code data} chunk anywhere.
     * The parser must walk to end-of-stream and raise
     * {@link IOException} identifying the missing chunk.
     */
    private static byte[] buildWavMissingDataChunkBytes() {
        int fmtBlockSize = 8 + 16;
        int total = 12 + fmtBlockSize;

        ByteBuffer buf = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        putAscii(buf, "RIFF");
        buf.putInt(total - 8);
        putAscii(buf, "WAVE");
        putClassicFmt(buf, WAVE_FORMAT_PCM, /* channels */ 1,
                /* sampleRate */ 8_000, /* bitsPerSample */ 16);
        return buf.array();
    }

    /** Write a 16-byte classic {@code fmt } chunk (no extension). */
    private static void putClassicFmt(ByteBuffer buf, short formatTag,
            int channels, int sampleRate, int bitsPerSample) {
        int bytesPerSample = bitsPerSample / 8;
        int blockAlign = channels * bytesPerSample;
        int avgBytesPerSec = sampleRate * blockAlign;
        putAscii(buf, "fmt ");
        buf.putInt(16);
        buf.putShort(formatTag);
        buf.putShort((short) channels);
        buf.putInt(sampleRate);
        buf.putInt(avgBytesPerSec);
        buf.putShort((short) blockAlign);
        buf.putShort((short) bitsPerSample);
    }

    private static void putAscii(ByteBuffer buf, String id) {
        for (int i = 0; i < id.length(); i++) {
            buf.put((byte) id.charAt(i));
        }
    }

    // =========================================================================
    // Test doubles
    // =========================================================================

    /**
     * {@link AudioSourceProvider} whose {@code canOpen(Path)} actually
     * opens the path via {@link Files#newByteChannel} — enough to make
     * {@link NoSuchFileException} surface naturally from the filesystem.
     * Mirrors {@code AudioSourcesTest.FakeProvider.readingHeaderFromPath}
     * without importing it (the test doubles live in package-private
     * classes, and this test class needs its own copy).
     */
    private static final class FilesystemTouchingProvider implements AudioSourceProvider {
        private final String name;
        private final AtomicInteger canOpenPathCalls = new AtomicInteger();

        FilesystemTouchingProvider(String name) {
            this.name = name;
        }

        int canOpenPathCallCount() {
            return canOpenPathCalls.get();
        }

        @Override public String formatName() { return name; }

        @Override
        public int canOpen(Path path) throws IOException {
            canOpenPathCalls.incrementAndGet();
            // Opens the path so filesystem-level signals (NoSuchFileException,
            // AccessDeniedException, etc.) surface naturally. The channel
            // is closed immediately — we never read bytes here.
            Files.newByteChannel(path).close();
            return 100;
        }

        @Override
        public int canOpen(InputStream stream, String hint) {
            return 100;
        }

        @Override
        public AudioSource open(Path path) throws IOException {
            // Unreachable in the file-not-found test: canOpen throws
            // NoSuchFileException first, so the facade re-throws before
            // ever calling open().
            throw new IOException(name + ".open(Path) should not be invoked");
        }

        @Override
        public AudioSource open(InputStream stream, String hint) throws IOException {
            throw new IOException(name + ".open(InputStream, String) should not be invoked");
        }
    }

    /**
     * {@link AudioSourceProvider} whose {@code canOpen(...)} returns
     * {@code 100} and whose {@code open(InputStream, String)} throws a
     * caller-seeded {@link IOException}. Used by the Req 12.5 test to
     * confirm the exception propagates verbatim through
     * {@link DtmfFileDecoder}.
     */
    private static final class ThrowingOpenProvider implements AudioSourceProvider {
        private final String name;
        private final IOException toThrow;

        ThrowingOpenProvider(String name, IOException toThrow) {
            this.name = name;
            this.toThrow = toThrow;
        }

        @Override public String formatName() { return name; }
        @Override public int canOpen(Path path) { return 100; }
        @Override public int canOpen(InputStream stream, String hint) { return 100; }

        @Override
        public AudioSource open(Path path) throws IOException {
            throw toThrow;
        }

        @Override
        public AudioSource open(InputStream stream, String hint) throws IOException {
            throw toThrow;
        }
    }

    /**
     * {@link AudioSourceProvider} whose {@code canOpen(InputStream,
     * String)} always throws a caller-seeded {@link IOException}. Used
     * by the Req 12.6 test to trigger the facade's WARNING-logging
     * branch (Req 5.9) so the logger name can be asserted.
     */
    private static final class ThrowingCanOpenStreamProvider implements AudioSourceProvider {
        private final String name;
        private final IOException toThrow;

        ThrowingCanOpenStreamProvider(String name, IOException toThrow) {
            this.name = name;
            this.toThrow = toThrow;
        }

        @Override public String formatName() { return name; }

        @Override
        public int canOpen(Path path) throws IOException {
            throw toThrow;
        }

        @Override
        public int canOpen(InputStream stream, String hint) throws IOException {
            throw toThrow;
        }

        @Override
        public AudioSource open(Path path) throws IOException {
            throw new IOException(name + ".open(Path) should not be invoked");
        }

        @Override
        public AudioSource open(InputStream stream, String hint) throws IOException {
            throw new IOException(name + ".open(InputStream, String) should not be invoked");
        }
    }

    /**
     * {@link Handler} that captures {@link LogRecord}s for assertion on
     * logger name / level / message (Req 12.6).
     */
    private static final class CapturingHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public synchronized void publish(LogRecord record) {
            records.add(record);
        }

        @Override public void flush() { /* no-op */ }
        @Override public void close() { /* no-op */ }

        synchronized List<LogRecord> warnings() {
            List<LogRecord> out = new ArrayList<>();
            for (LogRecord r : records) {
                if (r.getLevel() != null
                        && r.getLevel().intValue() >= Level.WARNING.intValue()) {
                    out.add(r);
                }
            }
            return out;
        }
    }
}
