package com.tino1b2be.dtmf.io;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link AudioSources} (Task 4.4).
 *
 * <p>Lives in the {@code com.tino1b2be.dtmf.io} package so it can reach
 * the package-private {@code openForTesting(...)} /
 * {@code registeredFormatsForTesting(...)} seams on {@code AudioSources}.
 * The seams let these tests inject a hand-rolled provider list directly
 * into the scoring loop, so the {@code ServiceLoader}-driven discovery
 * path is left to the integration tests in
 * {@code src/integrationTest/java} where both real providers are on the
 * runtime classpath (Req 16.1, 16.2). The unit tests here cover the
 * hard-to-reproduce negative paths: empty provider list, every provider
 * returning {@code -1}, one provider throwing from {@code canOpen},
 * ties on score broken by priority, non-markable-stream wrapping,
 * URL-hint derivation, and the file-not-found special case in which a
 * captured {@link IOException} is re-thrown verbatim rather than being
 * folded into {@link UnsupportedAudioFormatException}.
 *
 * <p>Validates: Requirements 5.7, 5.8, 5.9, 5.10, 5.11, 5.12, 11.1,
 * 11.2, 11.3, 11.4, 12.3, 12.4.
 */
class AudioSourcesTest {

    /** Logger that {@code AudioSources} writes WARNING records to
     *  (Requirement 12.6). Captured during tests that assert on log output. */
    private static final Logger AUDIO_SOURCES_LOGGER =
            Logger.getLogger(AudioSources.class.getName());

    private CapturingHandler capturingHandler;
    private Level priorLevel;
    private boolean priorUseParent;

    @BeforeEach
    void attachLoggingCapture() {
        capturingHandler = new CapturingHandler();
        priorLevel = AUDIO_SOURCES_LOGGER.getLevel();
        priorUseParent = AUDIO_SOURCES_LOGGER.getUseParentHandlers();
        // Ensure our handler receives everything and that we don't double
        // up through the parent (console) during the test run.
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

    // ---------------------------------------------------------------------
    // Requirement 5.8 — empty provider list
    // ---------------------------------------------------------------------

    /**
     * An empty provider list SHALL throw {@link UnsupportedAudioFormatException}
     * whose message states that no {@link AudioSourceProvider} implementations
     * are registered on the classpath (Requirement 5.8). Exercised against
     * all three {@code open(...)} overloads so the no-providers path is
     * covered uniformly.
     */
    @Test
    void openPathWithEmptyProviderListThrowsNoImplementationsRegistered(
            @TempDir Path tempDir) throws IOException {
        Path someFile = Files.writeString(tempDir.resolve("anything.bin"), "content");

        UnsupportedAudioFormatException ex = assertThrows(
                UnsupportedAudioFormatException.class,
                () -> AudioSources.openForTesting(someFile, List.of()));

        assertEmptyDiagnosticsWithNoImplementationsMessage(ex);
    }

    @Test
    void openInputStreamWithEmptyProviderListThrowsNoImplementationsRegistered()
            throws IOException {
        UnsupportedAudioFormatException ex = assertThrows(
                UnsupportedAudioFormatException.class,
                () -> AudioSources.openForTesting(
                        new ByteArrayInputStream(new byte[0]), null, List.of()));

        assertEmptyDiagnosticsWithNoImplementationsMessage(ex);
    }

    // ---------------------------------------------------------------------
    // Requirement 5.7 — all providers return -1
    // ---------------------------------------------------------------------

    /**
     * When every registered provider returns {@code -1} from
     * {@code canOpen(...)}, {@link UnsupportedAudioFormatException} is
     * thrown with {@link UnsupportedAudioFormatException#providersConsulted()}
     * listing every provider's {@code formatName()} in discovery order and
     * {@link UnsupportedAudioFormatException#providerScores()} recording
     * {@code -1} for each (Requirements 5.7, 6.4, 6.5, 6.6).
     */
    @Test
    void openPathWhenEveryProviderReturnsMinusOnePopulatesDiagnostics(
            @TempDir Path tempDir) throws IOException {
        Path someFile = Files.writeString(tempDir.resolve("mystery.bin"), "not audio");

        FakeProvider wav = FakeProvider.scoring("WAV", 0, -1);
        FakeProvider mp3 = FakeProvider.scoring("MP3", 0, -1);

        UnsupportedAudioFormatException ex = assertThrows(
                UnsupportedAudioFormatException.class,
                () -> AudioSources.openForTesting(someFile, List.of(wav, mp3)));

        // Providers were consulted in discovery order.
        assertEquals(List.of("WAV", "MP3"), ex.providersConsulted(),
                "providersConsulted() must list every provider asked to score, "
                        + "in discovery order (Req 6.4)");
        // Every consulted provider's score is recorded.
        assertEquals(
                Map.of("WAV", -1, "MP3", -1),
                ex.providerScores(),
                "providerScores() must contain one entry per consulted provider, "
                        + "keyed by formatName() (Req 6.5)");
        // Message mentions every provider's name and its score (Req 5.7
        // "lists every discovered provider's formatName() and its
        // returned score").
        String message = ex.getMessage();
        assertNotNull(message, "UnsupportedAudioFormatException must carry a detail message");
        assertAll(
                () -> assertTrue(message.contains("WAV"),
                        "Message must identify the 'WAV' provider; was: " + message),
                () -> assertTrue(message.contains("MP3"),
                        "Message must identify the 'MP3' provider; was: " + message),
                () -> assertTrue(message.contains("-1"),
                        "Message must include each provider's returned score '-1'; was: " + message));
    }

    // ---------------------------------------------------------------------
    // Requirement 5.9 — IOException from canOpen is treated as -1 and logged
    // ---------------------------------------------------------------------

    /**
     * When one provider's {@code canOpen(Path)} throws {@link IOException}
     * and another returns {@code 100}, the non-throwing provider SHALL win
     * dispatch, and the facade SHALL log a {@link Level#WARNING WARNING}
     * naming the throwing provider's {@code formatName()} (Requirement 5.9).
     * The subsequent {@code open(Path)} call on the winner SHALL be
     * invoked with the same path.
     */
    @Test
    void oneProviderThrowsFromCanOpenAnotherReturnsHundredWinnerIsTheNonThrowingOne(
            @TempDir Path tempDir) throws IOException {
        Path someFile = Files.writeString(tempDir.resolve("input.bin"), "payload");

        FakeProvider brokenWav = FakeProvider.throwingFromCanOpen(
                "WAV", 0, new IOException("disk error while reading header"));
        FakeProvider goodMp3 = FakeProvider.scoring("MP3", 0, 100);
        AudioSource stubSource = new StubAudioSource();
        goodMp3.whenOpenPathReturn(stubSource);

        AudioSource returned;
        try {
            returned = AudioSources.openForTesting(someFile, List.of(brokenWav, goodMp3));
        } finally {
            // Clean up regardless of outcome.
            capturingHandler.flush();
        }

        assertSame(stubSource, returned,
                "The non-throwing provider must win dispatch, so its open(Path) result "
                        + "must be what the facade returns");
        // open(Path) was dispatched to the MP3 provider only.
        assertEquals(1, goodMp3.openPathCallCount(),
                "Winner's open(Path) must be invoked exactly once");
        assertEquals(0, brokenWav.openPathCallCount(),
                "Losing provider's open(Path) must not be invoked");
        // Warning logged for the throwing provider.
        List<LogRecord> warnings = capturingHandler.warnings();
        assertFalse(warnings.isEmpty(),
                "A WARNING must be logged when canOpen(Path) throws IOException "
                        + "(Req 5.9); captured records: " + warnings);
        assertTrue(
                warnings.stream()
                        .anyMatch(r -> r.getMessage() != null
                                && r.getMessage().contains("WAV")),
                "At least one WARNING record must identify the throwing provider's "
                        + "formatName() 'WAV' (Req 5.9); captured records: "
                        + warnings);
    }

    // ---------------------------------------------------------------------
    // Requirement 5.6 — tie-break by priority (stated indirectly by 5.6 via
    // AudioSourceProvider.priority(); Req 4.3 in the SPI)
    // ---------------------------------------------------------------------

    /**
     * Two providers tied at score {@code 100} SHALL be disambiguated by
     * the greater {@link AudioSourceProvider#priority()} (Req 5.6). The
     * higher-priority provider's {@code open(Path)} SHALL be the one
     * dispatched.
     */
    @Test
    void twoProvidersTiedAtHundredDifferentPrioritiesHigherPriorityWins(
            @TempDir Path tempDir) throws IOException {
        Path someFile = Files.writeString(tempDir.resolve("input.bin"), "payload");

        FakeProvider lowerPriority = FakeProvider.scoring("ALPHA", 0, 100);
        FakeProvider higherPriority = FakeProvider.scoring("BETA", 5, 100);
        AudioSource betaSource = new StubAudioSource();
        AudioSource alphaSource = new StubAudioSource();
        lowerPriority.whenOpenPathReturn(alphaSource);
        higherPriority.whenOpenPathReturn(betaSource);

        AudioSource returned = AudioSources.openForTesting(
                someFile, List.of(lowerPriority, higherPriority));

        assertSame(betaSource, returned,
                "Tie at score 100 must be broken by the greater priority (Req 5.6) — "
                        + "higher-priority 'BETA' (priority=5) must win over 'ALPHA' (priority=0)");
        assertEquals(1, higherPriority.openPathCallCount(),
                "Higher-priority provider's open(Path) must be invoked exactly once");
        assertEquals(0, lowerPriority.openPathCallCount(),
                "Lower-priority provider's open(Path) must NOT be invoked");
    }

    // ---------------------------------------------------------------------
    // Requirement 5.12 / 11.2 — non-markable stream wrapping
    // ---------------------------------------------------------------------

    /**
     * {@link AudioSources#open(InputStream, String)} on a non-markable
     * stream SHALL wrap the stream in a {@link BufferedInputStream}
     * sized to at least {@code 16384} bytes before scoring, so providers
     * always see a markable stream (Requirements 5.12, 11.2). Verified
     * by inspecting the stream instance each provider is handed during
     * {@code canOpen(InputStream, String)}.
     */
    @Test
    void openInputStreamWrapsNonMarkableStreamBeforeScoring() throws IOException {
        byte[] payload = new byte[64];
        Arrays.fill(payload, (byte) 0x42);
        NonMarkableStream rawStream = new NonMarkableStream(payload);

        FakeProvider recorder = FakeProvider.scoring("REC", 0, 100);
        AudioSource stubSource = new StubAudioSource();
        recorder.whenOpenStreamReturn(stubSource);

        AudioSource returned = AudioSources.openForTesting(
                rawStream, "hint.bin", List.of(recorder));

        assertSame(stubSource, returned,
                "Returned source must be the winner's open(InputStream, String) result");

        // The provider must have been handed a markable wrapper, NOT the
        // raw non-markable stream.
        InputStream streamSeenByCanOpen = recorder.lastCanOpenStream();
        assertNotNull(streamSeenByCanOpen,
                "canOpen(InputStream, String) should have been called once");
        assertTrue(streamSeenByCanOpen.markSupported(),
                "Facade must wrap non-markable streams so providers see markSupported() == true "
                        + "(Req 5.12, 11.2)");
        assertTrue(streamSeenByCanOpen instanceof BufferedInputStream,
                "Non-markable stream must be wrapped in a BufferedInputStream "
                        + "(Req 5.12 names the wrapper type); got: "
                        + streamSeenByCanOpen.getClass().getName());
        // The same wrapper must have been handed to open(InputStream, String)
        // so providers see the header bytes they mark/reset'd over.
        InputStream streamSeenByOpen = recorder.lastOpenStream();
        assertSame(streamSeenByCanOpen, streamSeenByOpen,
                "open(InputStream, String) must receive the same wrapped stream as "
                        + "canOpen(InputStream, String), so mark/reset semantics are consistent");

        // The facade must NOT have been handed the raw stream after
        // wrapping — confirm the raw stream was not consumed by the
        // facade itself (the scoring loop reads through the wrapper only).
        assertFalse(rawStream.closed(),
                "Facade must not close the caller-supplied stream (Req 4.10)");
    }

    // ---------------------------------------------------------------------
    // Requirement 5.11 — registeredFormats() preserves discovery order
    // ---------------------------------------------------------------------

    /**
     * {@link AudioSources#registeredFormats()} SHALL return every loaded
     * provider's {@link AudioSourceProvider#formatName()} in discovery
     * order (Requirement 5.11). The test injects the order via the
     * package-private seam and asserts it is preserved verbatim.
     */
    @Test
    void registeredFormatsReturnsNamesInDiscoveryOrder() {
        FakeProvider wav = FakeProvider.scoring("WAV", 0, -1);
        FakeProvider mp3 = FakeProvider.scoring("MP3", 0, -1);
        FakeProvider flac = FakeProvider.scoring("FLAC", 0, -1);

        // Injected in WAV, MP3, FLAC order.
        List<String> names = AudioSources.registeredFormatsForTesting(
                List.of(wav, mp3, flac));
        assertEquals(List.of("WAV", "MP3", "FLAC"), names,
                "registeredFormats() must preserve the order providers were discovered in "
                        + "(Req 5.11)");

        // Reverse the order and confirm the snapshot reflects the new order.
        List<String> reversed = AudioSources.registeredFormatsForTesting(
                List.of(flac, mp3, wav));
        assertEquals(List.of("FLAC", "MP3", "WAV"), reversed,
                "registeredFormats() must reflect the caller's discovery order, not a "
                        + "hard-coded order");
    }

    // ---------------------------------------------------------------------
    // Requirements 11.1, 11.3, 11.4 — URL dispatch hint derivation and
    // stream lifecycle on provider exception
    // ---------------------------------------------------------------------

    /**
     * {@link AudioSources#open(URL)} with a {@code file://} URL whose path
     * ends in {@code /sample.wav} SHALL derive the hint {@code "sample.wav"}
     * from the URL's last path segment (Requirement 11.1). When the
     * winning provider's {@code open(InputStream, String)} throws, the
     * raw URL stream SHALL be closed before the exception propagates so
     * no network/file connection leaks (Requirement 11.4). Using a
     * custom {@link URLStreamHandler} lets the test track the stream
     * instance directly rather than fishing for file-handle counts.
     */
    @Test
    void openUrlFileUrlDerivesHintFromLastPathSegmentAndClosesStreamOnProviderException()
            throws IOException {
        // Tracked in-memory stream backing the custom URL so we can
        // observe its close() state.
        TrackingInputStream urlStream = new TrackingInputStream(wavMagicPrefix());
        List<TrackingInputStream> openedStreams = new CopyOnWriteArrayList<>();
        openedStreams.add(urlStream);

        URL url = new URL(
                "file",
                "",
                -1,
                "/fixtures/sample.wav",
                new URLStreamHandler() {
                    @Override
                    protected URLConnection openConnection(URL u) {
                        return new URLConnection(u) {
                            @Override
                            public void connect() {
                                // No-op; bytes delivered directly via getInputStream().
                            }

                            @Override
                            public InputStream getInputStream() {
                                // Return the pre-created stream so the test
                                // retains a direct reference for assertion.
                                return urlStream;
                            }
                        };
                    }
                });

        // Provider whose canOpen returns 100 on the WAV magic prefix but
        // throws from open(InputStream, String); this exercises the
        // "stream closed on provider exception" branch of Req 11.4.
        FakeProvider wavLike = FakeProvider.scoring("WAV", 0, 100);
        IOException openFailure = new IOException("synthetic open failure");
        wavLike.whenOpenStreamThrow(openFailure);

        IOException thrown = assertThrows(
                IOException.class,
                () -> AudioSources.openForTesting(url, List.of(wavLike)));

        // The original provider exception propagates verbatim (Req 12.5 —
        // don't swallow IOException).
        assertSame(openFailure, thrown,
                "Provider's IOException must propagate with its identity preserved; "
                        + "no swallowing or wrapping allowed (Req 12.5)");

        // The provider saw the derived hint "sample.wav" (the last path
        // segment; Req 11.1).
        assertEquals("sample.wav", wavLike.lastCanOpenHint(),
                "Hint passed to canOpen(InputStream, String) must be the URL's last "
                        + "path segment 'sample.wav' (Req 11.1); got: "
                        + wavLike.lastCanOpenHint());
        assertEquals("sample.wav", wavLike.lastOpenHint(),
                "Hint passed to open(InputStream, String) must be the URL's last "
                        + "path segment 'sample.wav' (Req 11.1); got: "
                        + wavLike.lastOpenHint());

        // The raw URL stream was closed by the facade when the provider
        // open threw (Req 11.4 — closing the URL-owned stream on failure
        // to prevent connection leaks).
        assertTrue(urlStream.closed(),
                "Facade must close the URL-opened stream when the provider's open(...) "
                        + "throws (Req 11.4); stream was NOT closed");
    }

    // ---------------------------------------------------------------------
    // Requirement 12.3 / 12.4 — file-not-found special case
    // ---------------------------------------------------------------------

    /**
     * On {@link AudioSources#open(Path)} against a single provider whose
     * {@code canOpen(Path)} opens the file and consequently throws
     * {@link NoSuchFileException} (a subclass of {@link IOException}),
     * the facade SHALL re-throw the captured exception verbatim rather
     * than folding it into {@link UnsupportedAudioFormatException}
     * (design "File-not-found special case"; Requirements 12.3, 12.4).
     * This preserves {@link NoSuchFileException} as a distinct error
     * signal instead of masquerading it as "format not supported."
     */
    @Test
    void openPathFileNotFoundWithSingleWavProviderPropagatesNoSuchFileException(
            @TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist.wav");
        // Sanity: the file genuinely does not exist.
        assertFalse(Files.exists(missing),
                "Fixture precondition: the path must not exist on disk");

        FakeProvider realReadingWav = FakeProvider.readingHeaderFromPath("WAV", 0);

        NoSuchFileException thrown = assertThrows(
                NoSuchFileException.class,
                () -> AudioSources.openForTesting(missing, List.of(realReadingWav)));

        // The missing filename appears on the exception so callers can
        // identify which file is gone.
        String message = thrown.getMessage();
        assertNotNull(message, "NoSuchFileException must carry a detail message");
        assertTrue(message.contains("does-not-exist.wav"),
                "NoSuchFileException's message must identify the missing file; was: " + message);

        // Sanity: the provider's canOpen WAS invoked, so the IOException
        // did originate from the provider (rather than an earlier facade
        // guard).
        assertEquals(1, realReadingWav.canOpenPathCallCount(),
                "The provider's canOpen(Path) must have been invoked once");
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * Assert the no-providers-registered message and the empty
     * diagnostics collections Req 5.8 / Req 6.4 both require.
     */
    private static void assertEmptyDiagnosticsWithNoImplementationsMessage(
            UnsupportedAudioFormatException ex) {
        String message = ex.getMessage();
        assertNotNull(message, "UnsupportedAudioFormatException must carry a detail message");
        assertAll(
                () -> assertTrue(
                        message.toLowerCase(Locale.ROOT).contains("no audiosourceprovider"),
                        "Message must identify the missing providers (Req 5.8); was: " + message),
                () -> assertTrue(
                        message.toLowerCase(Locale.ROOT).contains("registered")
                                || message.toLowerCase(Locale.ROOT).contains("implementations"),
                        "Message must state that no implementations are registered "
                                + "(Req 5.8); was: " + message),
                () -> assertTrue(ex.providersConsulted().isEmpty(),
                        "providersConsulted() must be empty when no providers were registered "
                                + "(Req 6.4)"),
                () -> assertTrue(ex.providerScores().isEmpty(),
                        "providerScores() must be empty when no providers were consulted "
                                + "(Req 6.5)"));
    }

    /**
     * Twelve-byte RIFF/WAVE magic prefix. Content beyond the magic is
     * irrelevant for the URL-hint test because that provider short-circuits
     * in {@code open(InputStream, String)} before reading further.
     */
    private static byte[] wavMagicPrefix() {
        return new byte[] {
                'R', 'I', 'F', 'F',
                0x00, 0x00, 0x00, 0x00,
                'W', 'A', 'V', 'E'
        };
    }

    // ---------------------------------------------------------------------
    // Test doubles
    // ---------------------------------------------------------------------

    /**
     * Configurable {@link AudioSourceProvider} test double. Constructor-
     * injected scoring and priority; per-call behaviour (throwing from
     * canOpen, returning a prepared {@link AudioSource} from open) is
     * configured after construction via the various {@code when...}
     * helpers.
     */
    private static final class FakeProvider implements AudioSourceProvider {

        enum CanOpenPathMode { RETURN_FIXED, THROW_FIXED, READ_HEADER_FROM_PATH }

        private final String formatName;
        private final int priority;
        private final int fixedScore;
        private final CanOpenPathMode canOpenPathMode;
        private final IOException canOpenPathException;

        // Per-call state used by assertions.
        private final AtomicInteger canOpenPathCalls = new AtomicInteger();
        private final AtomicInteger canOpenStreamCalls = new AtomicInteger();
        private final AtomicInteger openPathCalls = new AtomicInteger();
        private final AtomicInteger openStreamCalls = new AtomicInteger();
        private volatile InputStream lastCanOpenStream;
        private volatile InputStream lastOpenStream;
        private volatile String lastCanOpenHint;
        private volatile String lastOpenHint;

        // Open-side behaviour: pick one of returnStubSource / throwOnOpen*.
        private volatile AudioSource openPathResult;
        private volatile AudioSource openStreamResult;
        private volatile IOException openStreamException;

        private FakeProvider(String formatName, int priority, int fixedScore,
                             CanOpenPathMode mode, IOException canOpenPathException) {
            this.formatName = formatName;
            this.priority = priority;
            this.fixedScore = fixedScore;
            this.canOpenPathMode = mode;
            this.canOpenPathException = canOpenPathException;
        }

        /** Provider whose {@code canOpen(...)} returns a fixed score. */
        static FakeProvider scoring(String formatName, int priority, int score) {
            return new FakeProvider(formatName, priority, score,
                    CanOpenPathMode.RETURN_FIXED, null);
        }

        /** Provider whose {@code canOpen(Path)} always throws the given exception. */
        static FakeProvider throwingFromCanOpen(
                String formatName, int priority, IOException toThrow) {
            return new FakeProvider(formatName, priority, -1,
                    CanOpenPathMode.THROW_FIXED, toThrow);
        }

        /**
         * Provider whose {@code canOpen(Path)} actually opens the file via
         * {@link Files#newByteChannel}, reads a header, and scores. Lets
         * the file-not-found test observe the {@link NoSuchFileException}
         * surfaced by the underlying filesystem call.
         */
        static FakeProvider readingHeaderFromPath(String formatName, int priority) {
            return new FakeProvider(formatName, priority, -1,
                    CanOpenPathMode.READ_HEADER_FROM_PATH, null);
        }

        FakeProvider whenOpenPathReturn(AudioSource source) {
            this.openPathResult = source;
            return this;
        }

        FakeProvider whenOpenStreamReturn(AudioSource source) {
            this.openStreamResult = source;
            return this;
        }

        FakeProvider whenOpenStreamThrow(IOException ex) {
            this.openStreamException = ex;
            return this;
        }

        int openPathCallCount() {
            return openPathCalls.get();
        }

        int canOpenPathCallCount() {
            return canOpenPathCalls.get();
        }

        InputStream lastCanOpenStream() {
            return lastCanOpenStream;
        }

        InputStream lastOpenStream() {
            return lastOpenStream;
        }

        String lastCanOpenHint() {
            return lastCanOpenHint;
        }

        String lastOpenHint() {
            return lastOpenHint;
        }

        @Override
        public String formatName() {
            return formatName;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public int canOpen(Path path) throws IOException {
            canOpenPathCalls.incrementAndGet();
            switch (canOpenPathMode) {
                case THROW_FIXED:
                    throw canOpenPathException;
                case READ_HEADER_FROM_PATH:
                    // Actually open the file so NoSuchFileException / AccessDeniedException
                    // etc. surface naturally. The channel itself is not
                    // read from — triggering the open is sufficient to
                    // surface NoSuchFileException.
                    Files.newByteChannel(path).close();
                    return 100;
                case RETURN_FIXED:
                default:
                    return fixedScore;
            }
        }

        @Override
        public int canOpen(InputStream stream, String hint) {
            canOpenStreamCalls.incrementAndGet();
            lastCanOpenStream = stream;
            lastCanOpenHint = hint;
            return fixedScore;
        }

        @Override
        public AudioSource open(Path path) throws IOException {
            openPathCalls.incrementAndGet();
            if (openPathResult == null) {
                throw new IOException(formatName + ".open(Path) not configured");
            }
            return openPathResult;
        }

        @Override
        public AudioSource open(InputStream stream, String hint) throws IOException {
            openStreamCalls.incrementAndGet();
            lastOpenStream = stream;
            lastOpenHint = hint;
            if (openStreamException != null) {
                throw openStreamException;
            }
            if (openStreamResult == null) {
                throw new IOException(formatName + ".open(InputStream, String) not configured");
            }
            return openStreamResult;
        }
    }

    /** Minimal {@link AudioSource} used only for identity assertions in dispatch tests. */
    private static final class StubAudioSource implements AudioSource {
        @Override public int sampleRate() { return 8_000; }
        @Override public int channelCount() { return 1; }
        @Override public int bitDepth() { return 16; }
        @Override public long totalFrames() { return 0L; }
        @Override public boolean canSeek() { return false; }
        @Override public long currentFrame() { return 0L; }
        @Override public int read(double[] buffer, int offset, int length) { return -1; }
        @Override public void seek(long frameIndex) {
            throw new UnsupportedOperationException("StubAudioSource");
        }
        @Override public void close() { /* nothing to release */ }
    }

    /**
     * {@link InputStream} that reports {@code markSupported() == false}
     * and tracks whether it has been closed, so the non-markable-wrapping
     * test can verify the facade never closes caller-owned streams.
     */
    private static final class NonMarkableStream extends InputStream {
        private final ByteArrayInputStream delegate;
        private volatile boolean closed;

        NonMarkableStream(byte[] bytes) {
            this.delegate = new ByteArrayInputStream(bytes);
        }

        @Override public int read() {
            return delegate.read();
        }

        @Override public int read(byte[] b, int off, int len) {
            return delegate.read(b, off, len);
        }

        @Override public boolean markSupported() {
            return false;
        }

        @Override public void close() throws IOException {
            closed = true;
            delegate.close();
        }

        boolean closed() {
            return closed;
        }
    }

    /**
     * {@link InputStream} backed by a byte array that records its own
     * {@link InputStream#close()} invocations. Used by the URL test to
     * verify the facade closes the URL-owned stream on provider
     * exception (Req 11.4).
     */
    private static final class TrackingInputStream extends InputStream {
        private final ByteArrayInputStream delegate;
        private volatile boolean closed;

        TrackingInputStream(byte[] bytes) {
            this.delegate = new ByteArrayInputStream(bytes);
        }

        @Override public int read() {
            return delegate.read();
        }

        @Override public int read(byte[] b, int off, int len) {
            return delegate.read(b, off, len);
        }

        @Override public int available() {
            return delegate.available();
        }

        @Override public void close() throws IOException {
            closed = true;
            delegate.close();
        }

        boolean closed() {
            return closed;
        }
    }

    /**
     * {@link Handler} that captures {@link LogRecord}s for later assertion.
     * Attached to {@link #AUDIO_SOURCES_LOGGER} in {@link #attachLoggingCapture()}
     * and removed in {@link #detachLoggingCapture()}.
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
                if (r.getLevel() != null && r.getLevel().intValue() >= Level.WARNING.intValue()) {
                    out.add(r);
                }
            }
            return out;
        }
    }
}
