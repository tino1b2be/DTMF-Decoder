package com.tino1b2be.dtmf.io.wav.internal;

import java.util.Objects;

/**
 * Header metadata for a single RIFF chunk inside a WAV container.
 *
 * <p>A RIFF chunk is a length-prefixed, ID-tagged run of bytes: four ASCII
 * characters of {@code id} (for example {@code "fmt "}, {@code "data"},
 * {@code "LIST"}, or {@code "ds64"}), a 32-bit little-endian {@code size}
 * field giving the number of payload bytes that follow the 8-byte header,
 * and then {@code size} bytes of payload &mdash; plus one zero-byte pad
 * when {@code size} is odd, carried at the next higher level by
 * {@link RiffReader}.
 *
 * <p>This record describes only the chunk's <em>header</em>. It does not
 * hold the payload itself: the WAV reader streams the payload directly
 * through {@link RiffReader#skip(long)} for ignored chunks and through
 * {@code fmt }/{@code ds64} parsers for chunks the provider understands.
 * {@link #dataStartOffset()} is the absolute byte position of the first
 * payload byte within the enclosing source (file or stream), measured from
 * the same origin as {@link RiffReader#position()}.
 *
 * <p><strong>Contract:</strong>
 * <ul>
 *   <li>{@code id} is the four-character chunk identifier, exactly four
 *       ASCII bytes interpreted as {@link java.nio.charset.StandardCharsets#US_ASCII}.
 *       Must be non-null. {@link RiffReader} produces IDs by calling
 *       {@link RiffReader#readAscii(int) readAscii(4)}, which in turn
 *       enforces the length invariant, so callers constructing a
 *       {@code RiffChunk} by hand (test fixtures, for example) must keep
 *       the same shape.</li>
 *   <li>{@code size} is the declared payload length in bytes, from the
 *       chunk's 32-bit little-endian size field, promoted to {@code long}
 *       because WAV's size field is <em>unsigned</em> 32-bit and the
 *       natural Java {@code int} would sign-flip at {@code 2 GiB}. For
 *       RF64 files the outer {@code RIFF}/{@code RF64} and {@code data}
 *       chunks use an {@code 0xFFFFFFFF} sentinel here and the real size
 *       is pulled from the {@code ds64} chunk by the caller. Must be
 *       non-negative.</li>
 *   <li>{@code dataStartOffset} is the absolute byte position of the
 *       chunk's payload within the underlying source, i.e. the position
 *       immediately <em>after</em> the 8-byte ID+size header. Must be
 *       non-negative.</li>
 * </ul>
 *
 * <p><strong>This record is not part of the published API.</strong> It
 * lives in {@code com.tino1b2be.dtmf.io.wav.internal}, whose stability
 * contract (see the package Javadoc) explicitly allows breakage between
 * any two releases. It is {@code public} at the type level purely so
 * classes in the parent {@code com.tino1b2be.dtmf.io.wav} package can
 * reach it; external callers MUST NOT depend on it.
 *
 * @param id              the four-character chunk identifier; must be
 *                        non-null
 * @param size            declared payload length in bytes; must be
 *                        non-negative
 * @param dataStartOffset absolute byte position of the first payload
 *                        byte within the enclosing source; must be
 *                        non-negative
 * @since 2.1.0
 */
public record RiffChunk(String id, long size, long dataStartOffset) {

    /**
     * Compact constructor validating the non-null and non-negative
     * invariants. Note that this constructor deliberately does not check
     * the length of {@code id}: {@link RiffReader} is the only production
     * path that creates {@code RiffChunk} instances and always passes a
     * four-character string, and leaving the length unconstrained keeps
     * test fixtures free to exercise degenerate IDs.
     *
     * @throws NullPointerException     if {@code id} is {@code null}
     * @throws IllegalArgumentException if {@code size} or
     *                                  {@code dataStartOffset} is
     *                                  negative
     */
    public RiffChunk {
        Objects.requireNonNull(id, "id");
        if (size < 0L) {
            throw new IllegalArgumentException("size must be >= 0, got " + size);
        }
        if (dataStartOffset < 0L) {
            throw new IllegalArgumentException(
                    "dataStartOffset must be >= 0, got " + dataStartOffset);
        }
    }
}
