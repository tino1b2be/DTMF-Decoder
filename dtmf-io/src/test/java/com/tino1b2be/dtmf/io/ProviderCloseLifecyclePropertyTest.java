package com.tino1b2be.dtmf.io;

// Feature: dtmf-io, Property 8: Provider close lifecycle and caller-stream ownership

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for the provider-close lifecycle contract and
 * caller-stream ownership rules.
 *
 * <p><strong>Property 8: Provider close lifecycle and caller-stream
 * ownership.</strong> <strong>Validates: Requirements 4.10,
 * 3.14.</strong>
 *
 * <p>The provider SPI draws a hard line between streams the caller
 * owns and streams the provider owns:
 *
 * <ul>
 *   <li><strong>Caller-supplied {@link InputStream} (Req 4.10).</strong>
 *       When the caller hands an {@code InputStream} to
 *       {@link AudioSources#open(InputStream, String)}, closing the
 *       returned {@link AudioSource} must not close the caller's
 *       stream. The caller opened it; the caller closes it. This is
 *       the symmetry every {@code try (InputStream in = ...)}
 *       caller relies on.</li>
 *   <li><strong>Path-opened internal stream (Req 3.14 lifecycle).</strong>
 *       When the caller hands a {@link Path} to
 *       {@link AudioSources#open(Path)}, the provider opens whatever
 *       backing stream it needs internally (a
 *       {@link java.nio.channels.FileChannel}, an
 *       {@link InputStream}, etc.) and hands the {@code AudioSource}
 *       back. Closing that {@code AudioSource} must close the
 *       provider's internal stream because nobody else has a handle
 *       on it &mdash; leaking it would leak a file descriptor per
 *       open/close cycle.</li>
 *   <li><strong>Idempotence (Req 3.14).</strong> Closing a returned
 *       {@code AudioSource} twice must not throw and must not double-
 *       close any underlying stream. This matches the
 *       {@link java.io.Closeable} convention and the same invariant
 *       Property 2 exercises against {@link RawPcmAudioSource}; here
 *       it is re-anchored from the facade's point of view so a
 *       provider that tracks its {@code closed} flag incorrectly
 *       would show up as a failing property here rather than only
 *       inside the provider's own module tests.</li>
 * </ul>
 *
 * <p>The property drives a {@link FakeProvider} stub through the
 * package-private {@link AudioSources#openForTesting(InputStream,
 * String, List)} and {@link AudioSources#openForTesting(Path, List)}
 * seams so the lifecycle invariants can be exercised without pulling
 * in a real format module. Each {@code FakeProvider} returns a
 * {@link LifecycleAudioSource} whose {@code close()} delegates to
 * closing the stream the provider was constructed with &mdash;
 * mirroring exactly what a real provider does: its
 * {@code AudioSource.close()} closes the backing stream. The test
 * then inspects the stream's close flag to decide whether the
 * facade / provider pair honoured Requirement 4.10 vs 3.14.
 *
 * <h2>Invariants asserted</h2>
 *
 * <ol>
 *   <li><em>Arm 1 (Req 4.10).</em> Calling {@link AudioSource#close()
 *       close()} on the {@code AudioSource} returned by
 *       {@code open(InputStream, String)} does not close the
 *       caller-supplied {@link TrackingInputStream}.</li>
 *   <li><em>Arm 2 (Req 3.14 lifecycle).</em> Calling
 *       {@code AudioSource.close()} on the source returned by
 *       {@code open(Path)} closes the {@link TrackingInputStream}
 *       the provider opened internally exactly once.</li>
 *   <li><em>Arm 3 (Req 3.14 idempotence).</em> Closing the returned
 *       {@code AudioSource} twice does not throw, does not double-
 *       close the backing stream in the Path arm, and leaves the
 *       caller-supplied stream untouched in the {@code InputStream}
 *       arm.</li>
 * </ol>
 *
 * <h2>Scope</h2>
 *
 * <p>This property targets Requirements 4.10 and 3.14 only. Adjacent
 * behaviours are covered elsewhere:
 *
 * <ul>
 *   <li>Post-close {@code read(...)} and {@code seek(...)} raising
 *       {@code IOException} is covered by
 *       {@code AudioSourceLifecyclePropertyTest}'s Invariants A and
 *       B, exercised against {@code RawPcmAudioSource} and a
 *       non-seekable stub. Re-testing it here would duplicate that
 *       coverage without adding a facade-level constraint.</li>
 *   <li>{@code URL}-based close semantics (Req 11.4) are out of scope
 *       here; that close path closes the
 *       {@code URL.openStream()}-opened stream even on the
 *       caller-stream path, which is a different contract from
 *       Req 4.10.</li>
 * </ul>
 */
class ProviderCloseLifecyclePropertyTest {

    // ------------------------------------------------------------------
    // Arm 1 — caller-supplied InputStream is not closed by
    // AudioSource.close() (Requirement 4.10)
    // ------------------------------------------------------------------

    /**
     * {@link AudioSources#open(InputStream, String)} returns an
     * {@link AudioSource} whose {@link AudioSource#close() close()}
     * does not close the caller-supplied stream. The provider
     * receives the caller's stream (possibly wrapped in a
     * {@link java.io.BufferedInputStream} if it was not markable),
     * but the {@code AudioSource} the provider returns owns only its
     * own resources. Closing it must leave the caller's stream
     * untouched so the caller's own {@code try (InputStream in = ...)}
     * block still does the work of releasing the underlying file
     * descriptor / socket / byte array.
     *
     * <p>The property varies:
     *
     * <ul>
     *   <li>The payload handed to the tracking stream — exercised at
     *       both empty and non-empty sizes so a provider that
     *       accidentally {@code read()}s to EOF and notices the
     *       stream is drained does not get to skip the close step
     *       silently.</li>
     *   <li>Whether the caller-supplied stream reports
     *       {@code markSupported() == true} or {@code false} — the
     *       facade's non-markable wrap path (Req 5.12) must not
     *       affect the Req 4.10 ownership invariant; both paths
     *       leave the caller's stream open.</li>
     *   <li>The nullable {@code hint} argument — it threads through
     *       unchanged, independent of the close invariant.</li>
     *   <li>The score the provider's {@code canOpen(InputStream,
     *       String)} returns — a single provider is always the
     *       unique winner in this property (no tie-break to compute)
     *       because the close invariant applies regardless of how
     *       the winner was chosen.</li>
     * </ul>
     */
    @Property(tries = 100)
    void callerSuppliedStreamIsNotClosedByAudioSourceClose(
            @ForAll("payloads") byte[] payload,
            @ForAll boolean markable,
            @ForAll("hints") String hint,
            @ForAll @IntRange(min = 0, max = 100) int score) throws IOException {

        TrackingInputStream caller = new TrackingInputStream(payload, markable);
        LifecycleAudioSource providerSource = new LifecycleAudioSource(
                /* backing */ null);
        FakeProvider provider = FakeProvider.forStream(score, providerSource);

        AudioSource opened = AudioSources.openForTesting(
                caller, hint, List.of(provider));

        assertSame(providerSource, opened,
                "open(InputStream, String) must return the provider's "
                        + "AudioSource unchanged so close() semantics are "
                        + "exercised against the provider-owned source");
        assertFalse(caller.closed(),
                "Pre-close check: caller-supplied stream must still be "
                        + "open after open(InputStream, String) returns "
                        + "(Req 4.10)");

        opened.close();

        assertFalse(caller.closed(),
                () -> "AudioSource.close() must NOT close the caller-"
                        + "supplied InputStream (Req 4.10); caller stream "
                        + "reported closed after opened.close()"
                        + " [payloadLen=" + payload.length
                        + ", markable=" + markable
                        + ", hint=" + hint + "]");
        assertTrue(providerSource.closed(),
                "Sanity check: the provider's own AudioSource must report "
                        + "closed after close() — otherwise the assertion "
                        + "on the caller stream is vacuous");
    }

    // ------------------------------------------------------------------
    // Arm 2 — Path-opened AudioSource closes the provider's internal
    // stream on close() (Requirement 3.14 lifecycle)
    // ------------------------------------------------------------------

    /**
     * {@link AudioSources#open(Path)} returns an {@link AudioSource}
     * whose {@link AudioSource#close() close()} closes any stream
     * the provider opened internally. The provider in this property
     * is modelled as returning a {@link LifecycleAudioSource} whose
     * {@code close()} delegates to closing a {@link TrackingInputStream}
     * instance the provider constructed itself. That's exactly the
     * shape a real {@code WavAudioSourceProvider.open(Path)}
     * implementation follows: open a {@link java.io.FileInputStream}
     * (or a {@link java.nio.channels.FileChannel}), hand it to an
     * {@code AudioSource} that treats the stream as owned, and close
     * the stream when the source is closed.
     *
     * <p>A leak of that internal stream would leak a file descriptor
     * per {@code open(Path)} / {@code close()} cycle — one of the
     * observable failure modes Req 3.14's "close releases any
     * underlying resources" language names directly.
     *
     * <p>The property varies:
     *
     * <ul>
     *   <li>The payload behind the provider-internal tracking stream
     *       so a shrink on a small payload still reproduces the
     *       close path.</li>
     *   <li>The score the provider returns from {@code canOpen(Path)}
     *       — unique winner for the same reason as Arm 1.</li>
     * </ul>
     */
    @Property(tries = 100)
    void pathOpenedAudioSourceClosesProviderInternalStream(
            @ForAll("payloads") byte[] providerInternalPayload,
            @ForAll @IntRange(min = 0, max = 100) int score) throws IOException {

        // The stream the provider would open internally inside
        // open(Path). Tracked here so we can assert close() propagation.
        TrackingInputStream internal = new TrackingInputStream(
                providerInternalPayload, /* markable */ false);
        LifecycleAudioSource providerSource = new LifecycleAudioSource(internal);
        FakeProvider provider = FakeProvider.forPath(score, providerSource);

        // A dummy Path: the provider's canOpen(Path) / open(Path) do
        // not read from it, so any existing file is fine. We create
        // an empty temp file so Path validation in AudioSources does
        // not trip on a non-existent file even though no real
        // provider path I/O runs.
        Path dummy = Files.createTempFile("provider-close-lifecycle-prop-", ".bin");
        try {
            AudioSource opened = AudioSources.openForTesting(
                    dummy, List.of(provider));

            assertSame(providerSource, opened,
                    "open(Path) must return the provider's AudioSource "
                            + "unchanged so close() semantics are exercised "
                            + "against the provider-owned source");
            assertFalse(internal.closed(),
                    "Pre-close check: provider-internal stream must still "
                            + "be open after open(Path) returns — close() "
                            + "is what triggers release (Req 3.14)");

            opened.close();

            assertTrue(internal.closed(),
                    () -> "AudioSource.close() on a Path-opened source "
                            + "must close the provider's internal stream "
                            + "(Req 3.14); internal stream reported open "
                            + "after opened.close() [payloadLen="
                            + providerInternalPayload.length + "]");
            assertEquals(1, internal.closeCallCount(),
                    "Provider-internal stream must be closed exactly once "
                            + "on a single opened.close() call; any higher "
                            + "count means close() is invoked twice per "
                            + "logical release and will fail on streams "
                            + "that track close-after-close as an error");
        } finally {
            Files.deleteIfExists(dummy);
        }
    }

    // ------------------------------------------------------------------
    // Arm 3 — AudioSource.close() is idempotent (Requirement 3.14)
    // ------------------------------------------------------------------

    /**
     * Calling {@link AudioSource#close() close()} more than once on
     * the {@code AudioSource} returned by either
     * {@link AudioSources#open(InputStream, String) open(InputStream,
     * String)} or {@link AudioSources#open(Path) open(Path)} is a
     * no-op after the first call: neither throws, and the backing
     * stream is closed at most once.
     *
     * <p>jqwik varies the number of repeated {@code close()} calls
     * between 2 and 5 so a regression where {@code close()} flips a
     * state on every call (or, worse, re-closes the backing stream
     * every call) shows up as a failing property with a small shrunk
     * counterexample.
     */
    @Property(tries = 100)
    void audioSourceCloseIsIdempotentAcrossBothArms(
            @ForAll("payloads") byte[] payload,
            @ForAll boolean usePathArm,
            @ForAll @IntRange(min = 2, max = 5) int numCloses) throws IOException {

        // Build the arm-specific (caller stream, provider source,
        // provider) triple. The tracking stream represents whichever
        // side owns the close in this arm:
        //   * usePathArm == true  → provider-internal stream
        //   * usePathArm == false → caller-supplied stream (which
        //                           must NEVER be closed, so
        //                           closeCallCount stays at zero
        //                           regardless of how many times
        //                           close() is called).
        TrackingInputStream tracked;
        FakeProvider provider;
        LifecycleAudioSource providerSource;
        Path dummy = null;
        AudioSource opened;

        if (usePathArm) {
            tracked = new TrackingInputStream(payload, /* markable */ false);
            providerSource = new LifecycleAudioSource(tracked);
            provider = FakeProvider.forPath(/* score */ 50, providerSource);
            dummy = Files.createTempFile(
                    "provider-close-lifecycle-idempotent-", ".bin");
            opened = AudioSources.openForTesting(dummy, List.of(provider));
        } else {
            tracked = new TrackingInputStream(payload, /* markable */ true);
            providerSource = new LifecycleAudioSource(/* backing */ null);
            provider = FakeProvider.forStream(/* score */ 50, providerSource);
            opened = AudioSources.openForTesting(
                    tracked, /* hint */ null, List.of(provider));
        }

        try {
            for (int i = 0; i < numCloses; i++) {
                final int attempt = i;
                try {
                    opened.close();
                } catch (IOException | RuntimeException ex) {
                    fail(() -> "close() call #" + (attempt + 1)
                            + " on the " + (usePathArm ? "Path" : "InputStream")
                            + "-opened AudioSource threw "
                            + ex.getClass().getSimpleName() + ": "
                            + ex.getMessage()
                            + " (close() must be idempotent per Req 3.14)");
                }
            }

            assertTrue(providerSource.closed(),
                    "After at least one close() call the provider's "
                            + "AudioSource must report closed");

            if (usePathArm) {
                // Path arm: internal stream owned by provider; close
                // exactly once despite repeated AudioSource.close()
                // calls.
                assertEquals(1, tracked.closeCallCount(),
                        () -> "Path-opened AudioSource.close() must be "
                                + "idempotent at the internal-stream "
                                + "level (Req 3.14): expected exactly 1 "
                                + "close on the backing stream, got "
                                + tracked.closeCallCount() + " after "
                                + numCloses + " AudioSource.close() calls");
            } else {
                // InputStream arm: caller's stream must never be
                // closed (Req 4.10), idempotence or not.
                assertEquals(0, tracked.closeCallCount(),
                        () -> "InputStream-opened AudioSource.close() must "
                                + "never close the caller-supplied stream "
                                + "(Req 4.10): expected exactly 0 closes, "
                                + "got " + tracked.closeCallCount()
                                + " after " + numCloses
                                + " AudioSource.close() calls");
            }
        } finally {
            if (dummy != null) {
                Files.deleteIfExists(dummy);
            }
        }
    }

    // ------------------------------------------------------------------
    // Arbitraries
    // ------------------------------------------------------------------

    /**
     * Random byte payload in {@code [0, 1024]}. Small by design: this
     * property does not exercise the non-markable wrapping buffer
     * (Property 7's job). The payload exists only so the tracking
     * stream has something to report as readable before being closed
     * (or deliberately not closed, per Arm 1).
     */
    @Provide
    Arbitrary<byte[]> payloads() {
        return Arbitraries.bytes().array(byte[].class).ofMinSize(0).ofMaxSize(1024);
    }

    /**
     * Nullable hint generator: either {@code null} or a random short
     * alphanumeric string. The hint flows through {@code canOpen} and
     * {@code open} unchanged; it is varied here only so the property
     * asserts the close invariant holds independent of hint value.
     */
    @Provide
    Arbitrary<String> hints() {
        Arbitrary<String> nonNull = Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(16);
        return Arbitraries.oneOf(
                Arbitraries.just((String) null),
                nonNull);
    }

    // ------------------------------------------------------------------
    // Test doubles
    // ------------------------------------------------------------------

    /**
     * {@link InputStream} that tracks whether — and how many times —
     * it has been closed. Reading, available, and mark/reset
     * behaviour delegates to an internal {@link ByteArrayInputStream}
     * seeded from the payload. {@link #markSupported()} is
     * configurable at construction so the {@code InputStream} arm of
     * the property can exercise both the markable pass-through path
     * and the non-markable {@code BufferedInputStream}-wrapped path
     * (Req 5.12) from {@code AudioSources}.
     *
     * <p>Tracking the close count — not just a boolean flag — is
     * deliberate: Req 3.14's idempotence guarantee reads as "close()
     * releases resources at most once," and a regression where the
     * facade / provider pair double-closes the backing stream would
     * fail a high-signal assertion in Arm 3 rather than silently
     * passing a boolean flag that had already been set by an earlier
     * (legitimate) close call.
     */
    private static final class TrackingInputStream extends InputStream {
        private final ByteArrayInputStream delegate;
        private final boolean markable;
        private final AtomicInteger closeCalls = new AtomicInteger();

        TrackingInputStream(byte[] bytes, boolean markable) {
            this.delegate = new ByteArrayInputStream(bytes);
            this.markable = markable;
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return delegate.read(b, off, len);
        }

        @Override
        public int available() {
            return delegate.available();
        }

        @Override
        public boolean markSupported() {
            return markable;
        }

        @Override
        public synchronized void mark(int readlimit) {
            if (markable) {
                delegate.mark(readlimit);
            }
            // When !markable the contract permits mark() to be a
            // no-op; we do not throw here because the non-markable
            // path goes through BufferedInputStream wrapping and the
            // wrapper manages its own mark/reset state.
        }

        @Override
        public synchronized void reset() throws IOException {
            if (markable) {
                delegate.reset();
            } else {
                throw new IOException(
                        "TrackingInputStream.reset is unsupported "
                                + "when markSupported()==false");
            }
        }

        @Override
        public void close() throws IOException {
            closeCalls.incrementAndGet();
            delegate.close();
        }

        /** @return {@code true} iff {@link #close()} has been called at
         *          least once. */
        boolean closed() {
            return closeCalls.get() > 0;
        }

        /** @return the exact number of times {@link #close()} has been
         *          invoked. Used by the idempotence property to
         *          distinguish "closed exactly once" from "closed
         *          multiple times." */
        int closeCallCount() {
            return closeCalls.get();
        }
    }

    /**
     * Minimal {@link AudioSource} whose {@link #close()} tracks its
     * own closed state and, optionally, closes a provider-owned
     * backing stream. The backing stream argument mirrors the two
     * arms of the property:
     *
     * <ul>
     *   <li>Arm 1 (InputStream) passes {@code null}: the
     *       {@code AudioSource} has no stream to close itself
     *       because ownership of the caller-supplied stream stays
     *       with the caller (Req 4.10).</li>
     *   <li>Arm 2 (Path) passes the provider-internal
     *       {@link TrackingInputStream}: the {@code AudioSource}
     *       delegates {@code close()} to closing that stream so the
     *       property can assert Req 3.14's "close releases resources"
     *       guarantee from the outside.</li>
     * </ul>
     *
     * <p>{@code close()} is idempotent by design — a first call
     * closes {@code backing} (if any) and flips {@code closed} to
     * {@code true}; subsequent calls short-circuit. That matches
     * what {@link RawPcmAudioSource} does and is the model every
     * real provider implementation must follow.
     */
    private static final class LifecycleAudioSource implements AudioSource {
        private final InputStream backing;
        private volatile boolean closed;

        LifecycleAudioSource(InputStream backing) {
            this.backing = backing;
        }

        /** @return {@code true} once {@link #close()} has been called. */
        boolean closed() {
            return closed;
        }

        @Override
        public int sampleRate() {
            return 8_000;
        }

        @Override
        public int channelCount() {
            return 1;
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
        public boolean canSeek() {
            return false;
        }

        @Override
        public long currentFrame() {
            return 0L;
        }

        @Override
        public int read(double[] buffer, int offset, int length) {
            Objects.requireNonNull(buffer, "buffer");
            return -1;
        }

        @Override
        public void seek(long frameIndex) {
            throw new UnsupportedOperationException(
                    "LifecycleAudioSource is not seekable");
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            if (backing != null) {
                backing.close();
            }
        }
    }

    /**
     * Minimal {@link AudioSourceProvider} test double parameterised
     * by the {@code canOpen} score and the single
     * {@link AudioSource} instance it hands back from the arm-
     * specific {@code open} overload. Exactly one overload
     * (either {@link #open(Path)} or {@link #open(InputStream,
     * String)}) is active per instance; the other throws to surface
     * wiring bugs during development.
     */
    private static final class FakeProvider implements AudioSourceProvider {

        private final int score;
        private final LifecycleAudioSource source;
        private final boolean pathArm;

        private FakeProvider(int score, LifecycleAudioSource source, boolean pathArm) {
            this.score = score;
            this.source = source;
            this.pathArm = pathArm;
        }

        static FakeProvider forPath(int score, LifecycleAudioSource source) {
            return new FakeProvider(score, source, /* pathArm */ true);
        }

        static FakeProvider forStream(int score, LifecycleAudioSource source) {
            return new FakeProvider(score, source, /* pathArm */ false);
        }

        @Override
        public String formatName() {
            return "FAKE";
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public int canOpen(Path path) {
            return pathArm ? score : -1;
        }

        @Override
        public int canOpen(InputStream stream, String hint) {
            return pathArm ? -1 : score;
        }

        @Override
        public AudioSource open(Path path) {
            if (!pathArm) {
                throw new AssertionError(
                        "FakeProvider.open(Path) must not be invoked when "
                                + "configured for the InputStream arm");
            }
            return source;
        }

        @Override
        public AudioSource open(InputStream stream, String hint) {
            if (pathArm) {
                throw new AssertionError(
                        "FakeProvider.open(InputStream, String) must not be "
                                + "invoked when configured for the Path arm");
            }
            return source;
        }
    }
}
