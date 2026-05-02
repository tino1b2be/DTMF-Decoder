package com.tino1b2be.dtmf.io;

// Feature: dtmf-io, Property 9: DtmfFileDecoder close semantics

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import com.tino1b2be.dtmf.DtmfConfig;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for {@link DtmfFileDecoder}'s close-semantics
 * contract.
 *
 * <p><strong>Property 9: {@code DtmfFileDecoder} close
 * semantics.</strong> <strong>Validates: Requirements 8.11, 8.12, 11.3,
 * 11.4.</strong>
 *
 * <p>{@code DtmfFileDecoder} draws a hard line between the
 * {@link AudioSource}s it opens itself and the {@code AudioSource}s a
 * caller hands it:
 *
 * <ul>
 *   <li><strong>Internally opened sources (Req 8.11, 11.3, 11.4).</strong>
 *       {@link DtmfFileDecoder#decode(Path, DtmfConfig)},
 *       {@link DtmfFileDecoder#decode(InputStream, String, DtmfConfig)}
 *       and {@link DtmfFileDecoder#decode(URL, DtmfConfig)} open an
 *       {@code AudioSource} via {@link AudioSources#open(Path)
 *       AudioSources.open(...)}. That source is the decoder's
 *       responsibility. The try-with-resources block in each overload
 *       must close it before the method returns — on the normal path,
 *       and on every exceptional path. Req 11.3 and 11.4 name the
 *       specific "InputStream we received" and "URL connection we
 *       opened" sub-cases; Req 8.11 generalises the rule to every
 *       internally-owned source.</li>
 *   <li><strong>Caller-supplied source (Req 8.12).</strong>
 *       {@link DtmfFileDecoder#decode(AudioSource, DtmfConfig)} accepts
 *       an already-opened {@code AudioSource} from the caller.
 *       Ownership stays with the caller; the decoder must never close
 *       it, not on normal return and not on any exceptional path.
 *       Closing a caller-owned source would pull the rug out from
 *       under code like
 *       {@code try (AudioSource src = AudioSources.open(path)) { ...
 *       DtmfFileDecoder.decode(src, cfg); ... }} — the caller's
 *       try-with-resources would then close a source the decoder
 *       already closed, and any other use of the source after the
 *       decode call would fail.</li>
 * </ul>
 *
 * <h2>Seam</h2>
 *
 * <p>Exercising the three internally-opened overloads without pulling
 * in a real format module (which would also need a committed WAV/MP3
 * fixture and a {@code ServiceLoader}-registered provider that would
 * pollute every other test's registration state) requires a per-call
 * seam. {@code DtmfFileDecoder} exposes three package-private
 * {@code decodeForTesting(...)} methods that mirror the production
 * overloads byte-for-byte, except they route through
 * {@link AudioSources#openForTesting(Path, List)
 * AudioSources.openForTesting(...)} against a caller-supplied list of
 * providers instead of the cached {@code ServiceLoader} results.
 * Closing semantics travel the same try-with-resources block as the
 * production overloads, so a regression on the Req 8.11 close
 * guarantee would fail this property identically to a regression on
 * the production path.
 *
 * <p>The {@link DtmfFileDecoder#decode(AudioSource, DtmfConfig)}
 * overload needs no seam: it already accepts the {@code AudioSource}
 * directly, so the property injects a {@link CloseTrackingAudioSource}
 * and inspects its close count after the call returns or throws.
 *
 * <h2>Driving normal vs exceptional paths</h2>
 *
 * <p>The design calls for "normal returns and exceptional returns
 * (thrown by a stubbed {@code DtmfDecoder} delegate)" — because
 * {@link com.tino1b2be.dtmf.DtmfDecoder DtmfDecoder} is a static
 * utility with no injection seam, the property drives the exceptional
 * path by injecting a {@link CloseTrackingAudioSource} whose
 * {@link AudioSource#read(double[], int, int) read(...)} throws an
 * {@link IOException} on first invocation. The exception propagates
 * out through {@code decodeInternal}'s read loop, up through the
 * {@code decode(...)} method, and the try-with-resources block still
 * fires {@code close()} on the way out. That is exactly the code path
 * the "(exceptionally) throw from the delegate" language in the task
 * description is reaching for; the {@code DtmfDecoder} delegate itself
 * only runs if {@code read(...)} returns -1 without throwing, so a
 * throwing {@code read(...)} is the simpler, equivalent way to
 * exercise the same close-on-exception branch.
 *
 * <h2>Invariants asserted</h2>
 *
 * <ol>
 *   <li><em>Internally-opened arm, normal return (Req 8.11, 11.3,
 *       11.4).</em> After
 *       {@code decode(Path/InputStream/URL, ...)} returns normally,
 *       the {@code AudioSource} the decoder opened via
 *       {@code AudioSources.openForTesting(...)} has had
 *       {@code close()} called exactly once.</li>
 *   <li><em>Internally-opened arm, exceptional return (Req 8.11,
 *       11.3, 11.4).</em> After {@code decode(...)} throws (because
 *       the injected source's {@code read(...)} threw), the
 *       {@code AudioSource} the decoder opened has had
 *       {@code close()} called exactly once — propagated through the
 *       try-with-resources block.</li>
 *   <li><em>Caller-supplied arm, normal return (Req 8.12).</em> After
 *       {@code decode(AudioSource, ...)} returns normally, the
 *       caller-supplied source's close count is zero.</li>
 *   <li><em>Caller-supplied arm, exceptional return (Req 8.12).</em>
 *       After {@code decode(AudioSource, ...)} throws (because the
 *       caller-supplied source's {@code read(...)} threw), the
 *       caller-supplied source's close count is still zero. The
 *       decoder must not "helpfully" close the source on its way
 *       out.</li>
 * </ol>
 *
 * <h2>Scope</h2>
 *
 * <p>This property targets Req 8.11, 8.12, 11.3, and 11.4. Adjacent
 * invariants are covered elsewhere:
 *
 * <ul>
 *   <li>Provider-level close lifecycle (Req 4.10, 3.14) — "caller
 *       stream stays open when provider's source is closed" and
 *       "close() is idempotent" — is {@code ProviderCloseLifecyclePropertyTest}'s
 *       job (Property 8).</li>
 *   <li>Null-parameter rejection (Req 8.13) is covered by the unit
 *       tests in {@code DtmfFileDecoderTest}.</li>
 *   <li>Auto-resolve and channel/sample-rate rejection paths are
 *       Properties 10 and 11.</li>
 * </ul>
 */
class DtmfFileDecoderCloseSemanticsPropertyTest {

    /** Standard telephony config used throughout. Config fields are
     *  irrelevant to the close invariant; the decoder enforces close
     *  semantics identically regardless of config. */
    private static final DtmfConfig TELEPHONY = DtmfConfig.forTelephony();

    // ==================================================================
    // Arm A: Path / InputStream / URL — internally-opened sources must
    // be closed after return (normal or exceptional). Req 8.11, 11.3, 11.4.
    // ==================================================================

    /**
     * The three internally-opened overloads close the
     * {@link AudioSource} they obtained from
     * {@link AudioSources#openForTesting(Path, List)
     * AudioSources.openForTesting(...)} after a <em>normal</em> return.
     *
     * <p>The property injects a {@link CloseTrackingAudioSource} whose
     * {@code read(...)} returns {@code -1} immediately — the decoder's
     * read loop therefore exits cleanly on the first iteration, the
     * happy path through {@code decodeInternal} completes without
     * throwing, and the try-with-resources block in the selected
     * overload closes the source on the way out.
     *
     * <p>jqwik varies:
     *
     * <ul>
     *   <li>Which of the three overloads is exercised — {@code Path},
     *       {@code InputStream}, or {@code URL} — so a regression on
     *       any single overload shrinks to a small counterexample
     *       identifying that overload.</li>
     *   <li>The score the provider returns from {@code canOpen(...)}
     *       in {@code [0, 100]} — the scoring path itself is Property
     *       5's concern, but re-varying it here is free insurance
     *       that the close invariant holds regardless of whether the
     *       winning provider's score was maximal or merely positive.
     *       A single provider is always the unique winner in this
     *       property because the close invariant applies
     *       independent of tie-breaking.</li>
     *   <li>The sample rate and channel count of the injected source
     *       across values that all pass the
     *       {@code [4000, 192000]} / {@code {1, 2}} guards — so the
     *       guards never fire and the close branch is the one
     *       exercised. Guard-triggered exceptional paths are covered
     *       by Arm B below.</li>
     * </ul>
     */
    @Property(tries = 50)
    void internallyOpenedSourceIsClosedAfterNormalReturn(
            @ForAll Overload overload,
            @ForAll @IntRange(min = 0, max = 100) int score,
            @ForAll("validSampleRates") int sampleRate,
            @ForAll @IntRange(min = 1, max = 2) int channelCount) throws IOException {

        CloseTrackingAudioSource tracked = CloseTrackingAudioSource.forImmediateEos(
                sampleRate, channelCount);
        RecordingProvider provider = RecordingProvider.returning(score, tracked);

        invokeOverload(overload, provider, TELEPHONY);

        assertEquals(1, tracked.closeCount(),
                () -> "Internally-opened AudioSource must be closed exactly once "
                        + "after normal return from " + overload + ".decode(..., cfg) "
                        + "(Req 8.11, 11.3, 11.4); observed close count = "
                        + tracked.closeCount()
                        + " [score=" + score
                        + ", sampleRate=" + sampleRate
                        + ", channelCount=" + channelCount + "]");
        assertTrue(tracked.isClosed(),
                () -> "isClosed() must report true after close() was called on "
                        + overload + " overload");
    }

    /**
     * The three internally-opened overloads close the
     * {@link AudioSource} they obtained from
     * {@link AudioSources#openForTesting(Path, List)
     * AudioSources.openForTesting(...)} after an <em>exceptional</em>
     * return.
     *
     * <p>The property injects a {@link CloseTrackingAudioSource} whose
     * {@code read(...)} throws a sentinel {@link IOException} on the
     * first invocation. The exception propagates up through
     * {@code decodeInternal}'s read loop; the try-with-resources block
     * in the selected overload still fires {@code close()} on the way
     * out (that is exactly the close-on-exception behaviour Req 8.11's
     * "or propagating an exception" clause requires). The property
     * asserts both (a) the sentinel {@code IOException} actually
     * propagated — otherwise the test would pass vacuously against a
     * decoder that swallowed the exception — and (b) the close count
     * on the tracked source is exactly one.
     *
     * <p>This is the "exceptional returns (thrown by a stubbed
     * {@code DtmfDecoder} delegate)" arm. Because {@code DtmfDecoder}
     * is a static utility without an injection seam, a throwing
     * {@code AudioSource.read(...)} is the substitute: it drives the
     * same exceptional exit through the try-with-resources block,
     * which is the structure Req 8.11 is protecting.
     */
    @Property(tries = 50)
    void internallyOpenedSourceIsClosedAfterExceptionalReturn(
            @ForAll Overload overload,
            @ForAll @IntRange(min = 0, max = 100) int score,
            @ForAll("validSampleRates") int sampleRate,
            @ForAll @IntRange(min = 1, max = 2) int channelCount) throws IOException {

        String sentinel = "sentinel IOException from CloseTrackingAudioSource.read";
        CloseTrackingAudioSource tracked = CloseTrackingAudioSource.forThrowingRead(
                sampleRate, channelCount, sentinel);
        RecordingProvider provider = RecordingProvider.returning(score, tracked);

        IOException thrown = assertThrows(
                IOException.class,
                () -> invokeOverload(overload, provider, TELEPHONY),
                () -> "Internally-opened decode(...) on " + overload
                        + " must propagate the IOException thrown by "
                        + "AudioSource.read(...); close-on-exception is the "
                        + "behaviour under test and requires the exception "
                        + "to actually propagate (Req 12.5)");
        assertEquals(sentinel, thrown.getMessage(),
                () -> "Expected sentinel IOException to surface unchanged "
                        + "(Req 12.5: cause chain intact). Got: "
                        + thrown.getMessage());

        assertEquals(1, tracked.closeCount(),
                () -> "Internally-opened AudioSource must be closed exactly once "
                        + "after an exceptional return from " + overload
                        + ".decode(..., cfg) (Req 8.11, 11.3, 11.4); observed "
                        + "close count = " + tracked.closeCount()
                        + " [score=" + score
                        + ", sampleRate=" + sampleRate
                        + ", channelCount=" + channelCount + "]");
        assertTrue(tracked.isClosed(),
                () -> "isClosed() must report true after close() was called on "
                        + overload + " overload (exceptional path)");
    }

    // ==================================================================
    // Arm B: AudioSource overload — caller-supplied source must NOT be
    // closed after return (normal or exceptional). Req 8.12.
    // ==================================================================

    /**
     * {@link DtmfFileDecoder#decode(AudioSource, DtmfConfig)} does not
     * close the caller-supplied {@link AudioSource} after a
     * <em>normal</em> return. Ownership stays with the caller (Req
     * 8.12).
     *
     * <p>The property injects a {@link CloseTrackingAudioSource} whose
     * {@code read(...)} returns {@code -1} immediately — the decoder
     * completes normally with an empty tone list, and the
     * caller-supplied source's close count must stay at zero.
     */
    @Property(tries = 50)
    void callerSuppliedSourceIsNotClosedAfterNormalReturn(
            @ForAll("validSampleRates") int sampleRate,
            @ForAll @IntRange(min = 1, max = 2) int channelCount) throws IOException {

        CloseTrackingAudioSource tracked = CloseTrackingAudioSource.forImmediateEos(
                sampleRate, channelCount);

        DtmfFileDecoder.decode(tracked, TELEPHONY);

        assertEquals(0, tracked.closeCount(),
                () -> "decode(AudioSource, cfg) must NOT close the caller-supplied "
                        + "source on normal return (Req 8.12); observed close count = "
                        + tracked.closeCount()
                        + " [sampleRate=" + sampleRate
                        + ", channelCount=" + channelCount + "]");
        assertFalse(tracked.isClosed(),
                "isClosed() must remain false after normal return — caller "
                        + "retains ownership of the source");
    }

    /**
     * {@link DtmfFileDecoder#decode(AudioSource, DtmfConfig)} does not
     * close the caller-supplied {@link AudioSource} after an
     * <em>exceptional</em> return — the decoder has no right to close
     * a resource the caller may want to inspect, drain, or retry after
     * the exception (Req 8.12).
     *
     * <p>The property injects a {@link CloseTrackingAudioSource} whose
     * {@code read(...)} throws a sentinel {@link IOException} on first
     * invocation. The exception propagates out of
     * {@code decode(AudioSource, ...)}; the property asserts both that
     * the sentinel exception surfaced and that the caller-supplied
     * source's close count is still zero.
     */
    @Property(tries = 50)
    void callerSuppliedSourceIsNotClosedAfterExceptionalReturn(
            @ForAll("validSampleRates") int sampleRate,
            @ForAll @IntRange(min = 1, max = 2) int channelCount) throws IOException {

        String sentinel = "sentinel IOException from caller-supplied AudioSource.read";
        CloseTrackingAudioSource tracked = CloseTrackingAudioSource.forThrowingRead(
                sampleRate, channelCount, sentinel);

        IOException thrown = assertThrows(
                IOException.class,
                () -> DtmfFileDecoder.decode(tracked, TELEPHONY),
                () -> "decode(AudioSource, cfg) must propagate the IOException "
                        + "thrown by AudioSource.read(...); the non-close "
                        + "invariant is the behaviour under test and requires "
                        + "the exception to actually propagate (Req 12.5)");
        assertEquals(sentinel, thrown.getMessage(),
                () -> "Expected sentinel IOException to surface unchanged "
                        + "(Req 12.5). Got: " + thrown.getMessage());

        assertEquals(0, tracked.closeCount(),
                () -> "decode(AudioSource, cfg) must NOT close the caller-supplied "
                        + "source on exceptional return (Req 8.12); observed close "
                        + "count = " + tracked.closeCount()
                        + " [sampleRate=" + sampleRate
                        + ", channelCount=" + channelCount + "]");
        assertFalse(tracked.isClosed(),
                "isClosed() must remain false after exceptional return — the "
                        + "decoder must not 'helpfully' close a caller-owned "
                        + "source on its way out");
    }

    // ==================================================================
    // Arbitraries and helpers
    // ==================================================================

    /**
     * Sample rates sampled from the supported {@code [4000, 192000]}
     * range. Uses a small set of representative values rather than the
     * full interval so the property focuses on the close invariant and
     * not on the sample-rate guard itself (Property 11's concern).
     */
    @Provide
    Arbitrary<Integer> validSampleRates() {
        return Arbitraries.of(4_000, 8_000, 16_000, 22_050, 44_100, 48_000, 96_000, 192_000);
    }

    /**
     * Invoke the selected internally-opened overload through the
     * package-private {@code decodeForTesting(...)} seam, routing
     * through {@link AudioSources#openForTesting(Path, List)
     * AudioSources.openForTesting(...)} against the caller-supplied
     * {@link RecordingProvider}.
     *
     * <p>The {@code Path} overload needs a real file on disk because
     * {@link AudioSources#openForTesting(Path, List)} passes the path
     * to each provider's {@code canOpen(Path)} — a non-existent path
     * would not necessarily fail (our {@link RecordingProvider}
     * ignores the path and returns the configured score) but this
     * test uses a real temp file anyway for fidelity to the
     * production path shape. The file is deleted in a {@code finally}
     * block so a failing assertion does not leak temp files.
     *
     * <p>The {@code InputStream} overload uses an empty
     * {@link ByteArrayInputStream}. The {@link RecordingProvider}
     * ignores the stream too — it only records the score — so the
     * stream contents are irrelevant; only the fact that an
     * {@code InputStream} was passed matters.
     *
     * <p>The {@code URL} overload uses the temp file's
     * {@link Path#toUri() toUri()} URL. This exercises the
     * {@link AudioSources#openForTesting(URL, List)
     * URL openForTesting} path all the way down to
     * {@code url.openStream()}; without a valid URL the
     * {@code openStream()} call would fail before the provider was
     * consulted.
     */
    private static void invokeOverload(
            Overload overload,
            RecordingProvider provider,
            DtmfConfig config) throws IOException {

        List<AudioSourceProvider> providers = List.<AudioSourceProvider>of(provider);

        switch (overload) {
            case PATH -> {
                Path tmp = Files.createTempFile("dtmf-close-prop-path-", ".bin");
                try {
                    DtmfFileDecoder.decodeForTesting(tmp, config, providers);
                } finally {
                    Files.deleteIfExists(tmp);
                }
            }
            case INPUT_STREAM -> {
                InputStream empty = new ByteArrayInputStream(new byte[0]);
                DtmfFileDecoder.decodeForTesting(empty, "dummy.bin", config, providers);
            }
            case URL -> {
                Path tmp = Files.createTempFile("dtmf-close-prop-url-", ".bin");
                try {
                    URL url = tmp.toUri().toURL();
                    DtmfFileDecoder.decodeForTesting(url, config, providers);
                } finally {
                    Files.deleteIfExists(tmp);
                }
            }
            default -> fail("Unknown overload: " + overload);
        }
    }

    /** The three internally-opened overloads. Enumerated so jqwik's
     *  shrinker can report which overload the counterexample came
     *  from. */
    enum Overload {
        PATH,
        INPUT_STREAM,
        URL
    }

    // ==================================================================
    // Test doubles
    // ==================================================================

    /**
     * {@link AudioSource} test double that tracks {@link #close()}
     * invocations and exposes a configurable behaviour for
     * {@link #read(double[], int, int) read(...)}:
     *
     * <ul>
     *   <li>{@link #forImmediateEos(int, int)} — {@code read(...)}
     *       always returns {@code -1} (end of stream reached on first
     *       call). Drives the normal-return arm of every close
     *       property.</li>
     *   <li>{@link #forThrowingRead(int, int, String)} —
     *       {@code read(...)} always throws {@link IOException} with
     *       the supplied sentinel message. Drives the
     *       exceptional-return arm of every close property.</li>
     * </ul>
     *
     * <p>Tracks the close count (not just a boolean flag) so a
     * regression where the decoder double-closes the source on some
     * exceptional paths would shrink to a counterexample identifying
     * the offending overload rather than silently passing a boolean
     * that had already been set by a legitimate close call.
     *
     * <p>Sample rate and channel count are caller-configurable so the
     * property can vary them across the full {@code [4000, 192000]} /
     * {@code {1, 2}} supported range without tripping the decoder's
     * guards.
     */
    private static final class CloseTrackingAudioSource implements AudioSource {
        private final int sampleRate;
        private final int channelCount;
        private final IOException readException; // null ⇒ return -1
        private java.io.Closeable backing;        // lazily attached by provider
        private int closeCount;
        private volatile boolean closed;

        private CloseTrackingAudioSource(
                int sampleRate, int channelCount, IOException readException) {
            this.sampleRate = sampleRate;
            this.channelCount = channelCount;
            this.readException = readException;
        }

        /**
         * Attach a backing {@link java.io.Closeable} (typically the raw
         * stream that {@link AudioSources#openForTesting(java.net.URL,
         * java.util.List)} opened via {@code url.openStream()}) so this
         * source's {@link #close()} cascade-closes it. Keeps test-time
         * file descriptors bounded even though the close count this
         * source reports refers only to <em>this</em> source's close
         * — the property under test does not care about the backing's
         * close count. Called by {@link RecordingProvider#open(
         * InputStream, String)}.
         */
        void attachBacking(java.io.Closeable backing) {
            this.backing = backing;
        }

        /** Source that reports immediate EOS from {@code read(...)}. */
        static CloseTrackingAudioSource forImmediateEos(int sampleRate, int channelCount) {
            return new CloseTrackingAudioSource(sampleRate, channelCount, null);
        }

        /** Source whose {@code read(...)} throws an {@link IOException}
         *  with the supplied sentinel message. */
        static CloseTrackingAudioSource forThrowingRead(
                int sampleRate, int channelCount, String message) {
            return new CloseTrackingAudioSource(
                    sampleRate, channelCount, new IOException(message));
        }

        int closeCount() {
            return closeCount;
        }

        boolean isClosed() {
            return closed;
        }

        @Override
        public int sampleRate() {
            return sampleRate;
        }

        @Override
        public int channelCount() {
            return channelCount;
        }

        @Override
        public int bitDepth() {
            return 16;
        }

        @Override
        public long totalFrames() {
            return 0L;
        }

        @Override
        public int read(double[] buffer, int offset, int length) throws IOException {
            Objects.requireNonNull(buffer, "buffer");
            if (readException != null) {
                // Throw a fresh copy each time so the "exception" is
                // not shared across retries (jqwik may reuse the
                // same CloseTrackingAudioSource instance for a
                // shrunk sequence). Preserve the sentinel message
                // for the assertion.
                throw new IOException(readException.getMessage());
            }
            return -1;
        }

        @Override
        public boolean canSeek() {
            return false;
        }

        @Override
        public void seek(long frameIndex) {
            throw new UnsupportedOperationException(
                    "CloseTrackingAudioSource is not seekable");
        }

        @Override
        public long currentFrame() {
            return 0L;
        }

        @Override
        public void close() {
            closeCount++;
            closed = true;
            if (backing != null) {
                try {
                    backing.close();
                } catch (IOException ignored) {
                    // Test double: do not let backing close errors
                    // mask the close-count invariant under test. A
                    // real AudioSource would rethrow; this is a test
                    // harness and the assertion surface is different.
                }
            }
        }
    }

    /**
     * Minimal {@link AudioSourceProvider} test double that returns a
     * fixed score from every {@code canOpen(...)} overload and hands
     * back the preconfigured {@link CloseTrackingAudioSource} from
     * every {@code open(...)} overload.
     *
     * <p>Supports both the {@code Path} and {@code InputStream} arms
     * in a single instance — {@code AudioSources.openForTesting(Path,
     * List)} only calls {@code canOpen(Path)} / {@code open(Path)},
     * while {@code openForTesting(InputStream, ...)} and
     * {@code openForTesting(URL, ...)} only call
     * {@code canOpen(InputStream, ...)} / {@code open(InputStream,
     * ...)}. A single provider returning the same tracked source from
     * both arms keeps the test harness small and still exercises the
     * three overloads uniformly.
     */
    private static final class RecordingProvider implements AudioSourceProvider {
        private final int score;
        private final CloseTrackingAudioSource source;

        private RecordingProvider(int score, CloseTrackingAudioSource source) {
            this.score = score;
            this.source = source;
        }

        static RecordingProvider returning(int score, CloseTrackingAudioSource source) {
            return new RecordingProvider(score, source);
        }

        @Override
        public String formatName() {
            return "RECORDING";
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public int canOpen(Path path) {
            return score;
        }

        @Override
        public int canOpen(InputStream stream, String hint) {
            return score;
        }

        @Override
        public AudioSource open(Path path) {
            return source;
        }

        @Override
        public AudioSource open(InputStream stream, String hint) {
            // Attach the stream so CloseTrackingAudioSource.close()
            // cascade-closes the underlying stream on URL-driven
            // flows (where AudioSources.openForTesting(URL, ...)
            // opened the raw stream via url.openStream()). Without
            // this the raw URL stream would leak one file
            // descriptor per property try. The InputStream overload
            // passes a caller-supplied stream that the property
            // creates fresh per try and does not keep a reference
            // to, so cascading close on that arm is still safe.
            source.attachBacking(stream);
            return source;
        }
    }
}
