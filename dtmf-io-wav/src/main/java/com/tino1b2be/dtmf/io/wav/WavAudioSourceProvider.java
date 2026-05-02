package com.tino1b2be.dtmf.io.wav;

import com.tino1b2be.dtmf.io.AudioSource;
import com.tino1b2be.dtmf.io.AudioSourceProvider;
import com.tino1b2be.dtmf.io.UnsupportedAudioFormatException;
import com.tino1b2be.dtmf.io.wav.internal.RiffReader;
import com.tino1b2be.dtmf.io.wav.internal.WaveFormat;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * {@link AudioSourceProvider} implementation for the
 * {@code RIFF/WAVE} (and {@code RF64/WAVE}) container formats. This is
 * the public entry point of the {@code dtmf-io-wav} module, discovered
 * by {@link java.util.ServiceLoader} through the
 * {@code META-INF/services/com.tino1b2be.dtmf.io.AudioSourceProvider}
 * registration (Requirement 9.2) and normally invoked indirectly via
 * {@code AudioSources.open(...)}.
 *
 * <h2>Design</h2>
 *
 * The provider is a <em>clean-room</em> RIFF parser: it reads the
 * container byte-by-byte through {@link RiffReader} and decodes samples
 * through the shared {@code com.tino1b2be.dtmf.io.internal.SampleConversion}
 * helper. It does not import anything under {@code javax.sound.sampled}
 * (Requirement 9.12) and ships with zero external runtime dependencies
 * beyond {@code dtmf-io} itself. The actual frame-level decoding lives
 * inside {@link WavAudioSource} (returned from {@link #open(Path)} and
 * {@link #open(InputStream, String)}); this class is exclusively
 * responsible for detecting a WAV input and parsing its header.
 *
 * <h2>Detection ({@code canOpen})</h2>
 *
 * Both {@code canOpen} overloads check the same twelve-byte magic pattern
 * (Requirements 9.5, 9.6): bytes {@code 0..3} must be {@code "RIFF"} or
 * {@code "RF64"}, bytes {@code 4..7} are the top-level size field and are
 * not validated at this stage, and bytes {@code 8..11} must be
 * {@code "WAVE"}. A match returns a score of {@code 100}; anything else,
 * including an input shorter than twelve bytes, returns {@code -1}. The
 * {@code Path} overload opens a fresh {@link FileChannel} and closes it
 * before returning; the {@link InputStream} overload uses
 * {@link InputStream#mark(int)} and {@link InputStream#reset()} so the
 * caller's stream is left positioned exactly where it started
 * (Requirement 4.6). Non-markable streams are declined with {@code -1}
 * without consuming any bytes, because reading a header from a
 * non-markable stream would leave it in a state no downstream provider
 * could recover from (Requirement 4.7); the {@code AudioSources} facade
 * wraps non-markable inputs in a {@link BufferedInputStream} before
 * scoring, so this branch mostly protects direct callers.
 *
 * <h2>Full parse ({@code open})</h2>
 *
 * When a caller proceeds to {@link #open(Path)} or
 * {@link #open(InputStream, String)}, the provider walks the RIFF form
 * with {@link RiffReader}, skipping any {@code LIST} / {@code bext} /
 * {@code junk} / {@code PEAK} / unknown chunks and locating the
 * mandatory {@code fmt } and {@code data} chunks (Requirement 9.11).
 * RF64 files additionally require a {@code ds64} chunk <em>before</em>
 * the {@code fmt } chunk: the outer 32-bit size fields are pinned to
 * {@code 0xFFFFFFFF} and the real 64-bit sizes are pulled from
 * {@code ds64}'s {@code riffSize64} and {@code dataSize64}. The parser
 * recognises exactly three {@code wFormatTag} values
 * (Requirements 9.7, 9.8, 9.9):
 * <ul>
 *   <li>{@code 0x0001} {@code WAVE_FORMAT_PCM} &mdash; signed integer
 *       PCM at {@code 16}, {@code 24}, or {@code 32} bits.</li>
 *   <li>{@code 0x0003} {@code WAVE_FORMAT_IEEE_FLOAT} &mdash; IEEE 754
 *       float at {@code 32} or {@code 64} bits.</li>
 *   <li>{@code 0xFFFE} {@code WAVE_FORMAT_EXTENSIBLE} &mdash; dispatches
 *       on the 16-byte {@code SubFormat} GUID; only
 *       {@code KSDATAFORMAT_SUBTYPE_PCM} and
 *       {@code KSDATAFORMAT_SUBTYPE_IEEE_FLOAT} are accepted.</li>
 * </ul>
 * Every other {@code wFormatTag} (including {@code 0x0006} A-law,
 * {@code 0x0007} &micro;-law, {@code 0x0011} IMA ADPCM) is rejected with
 * {@link UnsupportedAudioFormatException} identifying the code
 * (Requirement 9.10). Structural defects &mdash; a missing {@code fmt },
 * a missing {@code data}, a bogus outer magic, or a chunk whose declared
 * size runs off the end of the form &mdash; are rejected with
 * {@link IOException} describing the defect (Requirement 9.11), so
 * callers can tell "not a valid WAV" from "valid WAV with a compression
 * we do not support" (Requirement 12.4).
 *
 * <h2>Stream ownership</h2>
 *
 * The {@link #open(Path)} branch opens a {@link FileChannel} that the
 * returned {@link AudioSource} owns and closes on
 * {@link AudioSource#close()}. The {@link #open(InputStream, String)}
 * branch <em>never</em> closes the caller's stream
 * (Requirement 4.10); it returns a stream-backed {@code WavAudioSource}
 * whose {@link AudioSource#close()} transitions the source into the
 * closed state but leaves the caller's {@link InputStream} untouched.
 *
 * @since 2.1.0
 * @see WavAudioSource
 * @see AudioSourceProvider
 * @see AudioSource
 */
public final class WavAudioSourceProvider implements AudioSourceProvider {

    // ------------------------------------------------------------------
    // Magic-byte sentinels
    // ------------------------------------------------------------------

    /** {@code "RIFF"} as four bytes. */
    private static final byte[] MAGIC_RIFF = { 'R', 'I', 'F', 'F' };
    /** {@code "RF64"} as four bytes. */
    private static final byte[] MAGIC_RF64 = { 'R', 'F', '6', '4' };
    /** {@code "WAVE"} as four bytes. */
    private static final byte[] MAGIC_WAVE = { 'W', 'A', 'V', 'E' };

    /** Size of the outer RIFF/RF64 header (chunkId + size + formType). */
    private static final int OUTER_HEADER_BYTES = 12;

    /** Score returned by {@code canOpen} on a successful magic-byte match. */
    private static final int SCORE_MATCH = 100;

    // ------------------------------------------------------------------
    // wFormatTag constants (from the WAV/RIFF specification)
    // ------------------------------------------------------------------

    /** {@code WAVE_FORMAT_PCM} &mdash; signed integer linear PCM. */
    private static final int WAVE_FORMAT_PCM = 0x0001;
    /** {@code WAVE_FORMAT_IEEE_FLOAT} &mdash; 32/64-bit IEEE float. */
    private static final int WAVE_FORMAT_IEEE_FLOAT = 0x0003;
    /** {@code WAVE_FORMAT_EXTENSIBLE} &mdash; GUID-dispatched. */
    private static final int WAVE_FORMAT_EXTENSIBLE = 0xFFFE;

    // ------------------------------------------------------------------
    // Known SubFormat GUIDs (WAVEFORMATEXTENSIBLE)
    // ------------------------------------------------------------------

    /**
     * {@code KSDATAFORMAT_SUBTYPE_PCM} = {@code 00000001-0000-0010-8000-00AA00389B71}.
     *
     * <p>The 16-byte canonical form is little-endian for the first three
     * fields ({@code Data1}, {@code Data2}, {@code Data3}) and
     * big-endian for the final {@code Data4} byte array, which is how the
     * WAV file stores it on disk.
     */
    private static final byte[] SUBTYPE_PCM_GUID = {
            0x01, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x10, 0x00,
            (byte) 0x80, 0x00,
            0x00, (byte) 0xAA, 0x00, 0x38, (byte) 0x9B, 0x71
    };

    /**
     * {@code KSDATAFORMAT_SUBTYPE_IEEE_FLOAT} = {@code 00000003-0000-0010-8000-00AA00389B71}.
     */
    private static final byte[] SUBTYPE_IEEE_FLOAT_GUID = {
            0x03, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x10, 0x00,
            (byte) 0x80, 0x00,
            0x00, (byte) 0xAA, 0x00, 0x38, (byte) 0x9B, 0x71
    };

    /** Byte length of any 32-bit unsigned chunk size marker sentinel. */
    private static final long RF64_SIZE_OVERFLOW_MARKER = 0xFFFF_FFFFL;

    /**
     * {@link java.util.ServiceLoader} requires a public no-argument
     * constructor (Requirement 4.1). Instances are stateless and cheap
     * to construct; the provider caches no data across calls.
     */
    public WavAudioSourceProvider() {
        // no state
    }

    // ------------------------------------------------------------------
    // Identity / priority
    // ------------------------------------------------------------------

    @Override
    public String formatName() {
        // Requirement 9.3.
        return "WAV";
    }

    @Override
    public int priority() {
        // Requirement 9.4.
        return 0;
    }

    // ------------------------------------------------------------------
    // Detection: canOpen(Path)
    // ------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Opens a read-only {@link FileChannel} on {@code path}, reads the
     * first twelve bytes, and returns {@code 100} when they match the
     * RIFF/WAVE or RF64/WAVE pattern (Requirements 9.5, 9.6). The
     * channel is closed via try-with-resources before returning so the
     * detection call does not leak a file handle.
     *
     * <p>Any {@link IOException} raised while opening or reading the
     * file propagates to the caller;
     * {@code com.tino1b2be.dtmf.io.AudioSources} catches such
     * exceptions during scoring, records the provider as having returned
     * {@code -1}, logs a warning, and continues.
     *
     * @param path file to score; must be non-null
     * @return {@code 100} on a magic-byte match, {@code -1} otherwise
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws IOException          on I/O failure while reading the file
     */
    @Override
    public int canOpen(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            byte[] header = new byte[OUTER_HEADER_BYTES];
            ByteBuffer target = ByteBuffer.wrap(header);
            int totalRead = 0;
            while (totalRead < OUTER_HEADER_BYTES) {
                int n = channel.read(target);
                if (n < 0) {
                    // File shorter than 12 bytes: cannot be a WAV.
                    return -1;
                }
                totalRead += n;
            }
            return matchesWaveMagic(header) ? SCORE_MATCH : -1;
        }
    }

    // ------------------------------------------------------------------
    // Detection: canOpen(InputStream, String)
    // ------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>When {@code stream} supports {@code mark}/{@code reset}, marks
     * up to {@link #OUTER_HEADER_BYTES} bytes, reads exactly twelve bytes
     * via {@link InputStream#readNBytes(int)}, scores against the RIFF
     * pattern, and resets the stream in a {@code finally} block so the
     * caller's position is restored on both the success and failure
     * paths (Requirement 4.6). Short reads (fewer than twelve bytes)
     * return {@code -1}.
     *
     * <p>Non-markable streams are declined with {@code -1} without
     * consuming any bytes (Requirement 4.7); the {@code AudioSources}
     * facade wraps such streams in a {@link BufferedInputStream} before
     * scoring, so in normal use this branch is defensive.
     *
     * @param stream the stream to score; must be non-null
     * @param hint   optional caller-supplied hint; may be {@code null}
     *               and is ignored by this provider (content-based
     *               detection)
     * @return {@code 100} on a magic-byte match, {@code -1} otherwise
     * @throws NullPointerException if {@code stream} is {@code null}
     * @throws IOException          on I/O failure while reading the
     *                              header prefix
     */
    @Override
    public int canOpen(InputStream stream, String hint) throws IOException {
        Objects.requireNonNull(stream, "stream");
        if (!stream.markSupported()) {
            // Req 4.7: decline without consuming bytes.
            return -1;
        }
        stream.mark(OUTER_HEADER_BYTES);
        try {
            byte[] header = stream.readNBytes(OUTER_HEADER_BYTES);
            if (header.length < OUTER_HEADER_BYTES) {
                return -1;
            }
            return matchesWaveMagic(header) ? SCORE_MATCH : -1;
        } finally {
            stream.reset();
        }
    }

    /**
     * Common magic-byte check shared by both {@code canOpen} overloads.
     * The first four bytes must be {@code "RIFF"} or {@code "RF64"}, the
     * middle four bytes (the outer size field) are ignored, and bytes
     * 8..11 must be {@code "WAVE"} (Requirements 9.5, 9.6).
     *
     * @param header twelve-byte header prefix; must be exactly
     *               {@link #OUTER_HEADER_BYTES} bytes long
     * @return {@code true} on a match, {@code false} otherwise
     */
    private static boolean matchesWaveMagic(byte[] header) {
        boolean riffOrRf64 = matches(header, 0, MAGIC_RIFF)
                          || matches(header, 0, MAGIC_RF64);
        boolean wave = matches(header, 8, MAGIC_WAVE);
        return riffOrRf64 && wave;
    }

    /** Byte-comparison helper. */
    private static boolean matches(byte[] buf, int offset, byte[] expected) {
        for (int i = 0; i < expected.length; i++) {
            if (buf[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Full parse: open(Path)
    // ------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Opens a read-only {@link FileChannel} and drives the RIFF
     * parser over it to build a {@link WaveFormat}, then hands the
     * channel (still open, positioned at the first byte of the
     * {@code data} payload) to
     * {@link WavAudioSource#fromChannel(WaveFormat, FileChannel)}. The
     * returned source owns the channel; its
     * {@link AudioSource#close()} closes it.
     *
     * <p>On any parse failure this method closes the channel before
     * re-throwing so partially-parsed files do not leak file handles.
     *
     * @param path file to open; must be non-null
     * @return an opened {@link WavAudioSource} in channel-backed mode
     * @throws NullPointerException            if {@code path} is {@code null}
     * @throws UnsupportedAudioFormatException if the file's magic
     *                                         matched but the {@code fmt }
     *                                         chunk declares an
     *                                         unsupported encoding
     *                                         (Requirement 9.10)
     * @throws IOException                     on any other failure,
     *                                         including structural
     *                                         defects (Requirement 9.11)
     *                                         and underlying I/O
     *                                         errors
     */
    @Override
    public AudioSource open(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
        try {
            WaveFormat format = parseHeader(new RiffReader(channel), /* streamMode */ false);
            AudioSource source = WavAudioSource.fromChannel(format, channel);
            // Ownership transferred to WavAudioSource; null out to skip
            // the finally-block close.
            channel = null;
            return source;
        } finally {
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException ignored) {
                    // The primary exception is already in flight; the
                    // close failure on an error path is not the caller's
                    // concern. Suppressing here avoids masking the real
                    // diagnostic with a secondary "close failed".
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Full parse: open(InputStream, String)
    // ------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Drives the RIFF parser directly over the caller's
     * {@link InputStream} and hands it to
     * {@link WavAudioSource#fromCallerStream(WaveFormat, InputStream)}
     * with the stream positioned at the first byte of the {@code data}
     * payload. The returned source is stream-backed, so
     * {@link AudioSource#canSeek()} is {@code false} (Requirement 3.9).
     *
     * <p>This method <strong>never</strong> closes the caller's stream
     * (Requirement 4.10), on either the success path or the failure
     * path: the stream is the caller's to manage. The returned
     * {@link WavAudioSource}'s {@code close()} likewise leaves the
     * stream untouched.
     *
     * @param stream the caller-supplied stream; must be non-null.
     *               Callers that come in through
     *               {@code AudioSources.open(InputStream, String)} will
     *               always receive a markable stream thanks to the
     *               facade's buffering (Requirement 5.12); callers
     *               invoking the provider directly must supply a stream
     *               whose bytes can be consumed forward-only starting
     *               from the current position.
     * @param hint   optional caller-supplied hint (file name, URL path
     *               segment, MIME type); may be {@code null} and is
     *               ignored by this provider (content-based detection
     *               wins regardless of the hint).
     * @return an opened {@link WavAudioSource} in stream-backed mode
     * @throws NullPointerException            if {@code stream} is {@code null}
     * @throws UnsupportedAudioFormatException on unsupported encodings
     *                                         (Requirement 9.10)
     * @throws IOException                     on structural defects
     *                                         (Requirement 9.11) or
     *                                         underlying stream errors
     */
    @Override
    public AudioSource open(InputStream stream, String hint) throws IOException {
        Objects.requireNonNull(stream, "stream");
        WaveFormat format = parseHeader(new RiffReader(stream), /* streamMode */ true);
        return WavAudioSource.fromCallerStream(format, stream);
    }

    // ------------------------------------------------------------------
    // RIFF parser core
    // ------------------------------------------------------------------

    /**
     * Parse a RIFF/WAVE (or RF64/WAVE) header starting at the current
     * position of {@code reader}, stopping immediately after reading
     * the 8-byte header of the {@code data} chunk (Requirement 9.11).
     * The reader is left positioned at the first byte of the
     * {@code data} payload; for a channel-backed reader this means the
     * underlying {@link FileChannel} is positioned there, which is the
     * invariant {@link WavAudioSource#fromChannel(WaveFormat, FileChannel)}
     * relies on.
     *
     * <p>The {@code streamMode} flag is used only for diagnostic
     * context inside exception messages; the actual parse is identical
     * in both modes.
     *
     * @param reader     byte-source reader; must be non-null
     * @param streamMode {@code true} for the {@link InputStream}
     *                   overload, {@code false} for the {@link Path}
     *                   overload
     * @return validated {@link WaveFormat} describing the stream
     * @throws UnsupportedAudioFormatException on unsupported encoding
     *                                         codes (Requirement 9.10)
     * @throws IOException                     on structural defects
     *                                         (Requirement 9.11) or
     *                                         I/O failures
     */
    private static WaveFormat parseHeader(RiffReader reader, boolean streamMode)
            throws IOException {
        // ------------------------------------------------------------------
        // Outer RIFF/RF64 header
        // ------------------------------------------------------------------
        String chunkId;
        try {
            chunkId = reader.readAscii(4);
        } catch (EOFException e) {
            throw new IOException("Malformed WAV: file is shorter than a RIFF header.", e);
        }
        boolean rf64;
        if ("RIFF".equals(chunkId)) {
            rf64 = false;
        } else if ("RF64".equals(chunkId)) {
            rf64 = true;
        } else {
            throw new IOException(
                    "Malformed WAV: expected 'RIFF' or 'RF64' at offset 0, got '"
                            + chunkId + "'.");
        }
        long outerSize32 = reader.readU32LE();
        String formType = reader.readAscii(4);
        if (!"WAVE".equals(formType)) {
            throw new IOException(
                    "Malformed WAV: expected 'WAVE' form type at offset 8, got '"
                            + formType + "'.");
        }

        // ------------------------------------------------------------------
        // Walk the child chunks until 'data' is reached.
        //
        // The payload bounds define how far we are willing to walk
        // before giving up. For RIFF we use the outer 32-bit size field
        // directly; for RF64 we initially trust the sentinel and refine
        // once ds64 is parsed.
        // ------------------------------------------------------------------
        long formPayloadBytes = rf64 ? Long.MAX_VALUE : outerSize32; // refined by ds64 below
        long bytesConsumedInForm = 4L; // the 'WAVE' form-type field we just read

        FmtChunk fmt = null;
        Ds64Info ds64 = null;
        long dataStartOffset = -1L;
        long dataSize = -1L;
        // True once we've seen the first non-ds64 child chunk; used to
        // enforce "ds64 must be the first child chunk in RF64" per the
        // RF64 spec (Requirement 9.11 edge case).
        boolean seenNonDs64Chunk = false;

        while (true) {
            // Defensive bound: never read a child header if we've
            // already consumed (or exceeded) the form payload.
            if (bytesConsumedInForm + 8L > formPayloadBytes) {
                break;
            }

            String id;
            long size;
            try {
                id = reader.readAscii(4);
                size = reader.readU32LE();
            } catch (EOFException e) {
                // Reached the real end of the source before finding
                // 'data': structural defect.
                break;
            }
            bytesConsumedInForm += 8L;

            // Validate the chunk payload fits within the declared form.
            // Payload may be odd-sized; a pad byte follows it in that
            // case. Requirement 9.11: "chunk size exceeding remaining
            // file size".
            long chunkFootprint = size + (size & 1L);
            if (!rf64 && bytesConsumedInForm + chunkFootprint > formPayloadBytes) {
                throw new IOException(
                        "Malformed WAV: chunk '" + id + "' declares size " + size
                                + " which exceeds the remaining form payload ("
                                + (formPayloadBytes - bytesConsumedInForm) + " bytes).");
            }

            if ("fmt ".equals(id)) {
                fmt = parseFmtChunk(reader, size);
                seenNonDs64Chunk = true;
                bytesConsumedInForm += size;
                if ((size & 1L) != 0L) {
                    reader.skip(1L);
                    bytesConsumedInForm += 1L;
                }
            } else if ("data".equals(id)) {
                // Anchor the payload's start offset from the reader's
                // current position: for channel mode this is the
                // absolute file offset; for stream mode it is the
                // reader's running counter.
                dataStartOffset = reader.position();
                if (rf64 && size == RF64_SIZE_OVERFLOW_MARKER) {
                    if (ds64 == null) {
                        throw new IOException(
                                "Malformed RF64: 'data' chunk uses 0xFFFFFFFF size marker"
                                        + " but no 'ds64' chunk was seen before it.");
                    }
                    dataSize = ds64.dataSize64;
                } else {
                    dataSize = size;
                }
                // Stop parsing: we stream the data payload below, we do
                // not copy it.
                break;
            } else if ("ds64".equals(id) && rf64) {
                if (seenNonDs64Chunk) {
                    // ds64 must be the FIRST child chunk per the RF64
                    // specification.
                    throw new IOException(
                            "Malformed RF64: 'ds64' chunk must appear before any"
                                    + " other child chunk (including 'fmt ').");
                }
                ds64 = parseDs64Chunk(reader, size);
                bytesConsumedInForm += size;
                if ((size & 1L) != 0L) {
                    reader.skip(1L);
                    bytesConsumedInForm += 1L;
                }
                // Refine the form-payload bound with the 64-bit size.
                formPayloadBytes = ds64.riffSize64;
            } else {
                // Unknown chunk (LIST, bext, junk, PEAK, fact, ...):
                // skip it and continue. This is the "anything else"
                // arm of the design's parser pseudocode.
                try {
                    reader.skip(size);
                } catch (EOFException e) {
                    throw new IOException(
                            "Malformed WAV: chunk '" + id + "' declares size " + size
                                    + " which runs past the end of the "
                                    + (streamMode ? "stream" : "file") + ".",
                            e);
                }
                bytesConsumedInForm += size;
                if ((size & 1L) != 0L) {
                    try {
                        reader.skip(1L);
                    } catch (EOFException e) {
                        throw new IOException(
                                "Malformed WAV: chunk '" + id + "' pad byte runs"
                                        + " past the end of the "
                                        + (streamMode ? "stream" : "file") + ".",
                                e);
                    }
                    bytesConsumedInForm += 1L;
                }
                seenNonDs64Chunk = true;
            }
        }

        if (fmt == null) {
            throw new IOException(
                    "Malformed WAV: required 'fmt ' chunk was not found in the form.");
        }
        if (dataStartOffset < 0L || dataSize < 0L) {
            throw new IOException(
                    "Malformed WAV: required 'data' chunk was not found in the form.");
        }

        // ------------------------------------------------------------------
        // Validate fmt against bit-depth / channel / encoding domain.
        // ------------------------------------------------------------------
        int bytesPerFrame = fmt.resolvedBytesPerFrame();
        if (dataSize % bytesPerFrame != 0L) {
            // Tolerate: some well-formed WAVs declare a data size that
            // includes trailing pad bytes beyond whole frames. We clip
            // to whole frames rather than refusing the file outright.
            dataSize = (dataSize / bytesPerFrame) * bytesPerFrame;
        }
        long totalFrames = dataSize / bytesPerFrame;

        return new WaveFormat(
                fmt.sampleRate,
                fmt.channelCount,
                fmt.resolvedBitDepth(),
                bytesPerFrame,
                fmt.encoding,
                dataStartOffset,
                dataSize,
                totalFrames);
    }

    // ------------------------------------------------------------------
    // fmt chunk parsing
    // ------------------------------------------------------------------

    /**
     * Parse the payload of a {@code fmt } chunk into a private
     * {@link FmtChunk} record. The chunk must be at least 16 bytes
     * (classic PCM/float shape); {@code WAVEFORMATEXTENSIBLE} payloads
     * are at least 40 bytes and carry a 16-byte {@code SubFormat} GUID
     * that dispatches onto {@link WaveFormat.Encoding}.
     *
     * @param reader        reader positioned at the first byte of the
     *                      {@code fmt } payload
     * @param declaredSize  the chunk's declared size (payload bytes
     *                      only, not including the 8-byte header)
     * @return parsed {@link FmtChunk}
     * @throws UnsupportedAudioFormatException on an unsupported
     *                                         {@code wFormatTag} or
     *                                         {@code SubFormat} GUID
     *                                         (Requirement 9.10)
     * @throws IOException                     on malformed fields or
     *                                         a short chunk
     */
    private static FmtChunk parseFmtChunk(RiffReader reader, long declaredSize)
            throws IOException {
        if (declaredSize < 16L) {
            throw new IOException(
                    "Malformed WAV: 'fmt ' chunk is " + declaredSize
                            + " bytes, must be at least 16.");
        }

        long bytesConsumed = 0L;
        int wFormatTag = reader.readU16LE();
        int nChannels = reader.readU16LE();
        long nSamplesPerSec = reader.readU32LE();
        reader.readU32LE(); // nAvgBytesPerSec (informative, ignored)
        int nBlockAlign = reader.readU16LE();
        int wBitsPerSample = reader.readU16LE();
        bytesConsumed += 16L;

        int effectiveFormatTag = wFormatTag;
        int effectiveBitDepth = wBitsPerSample;
        WaveFormat.Encoding encoding;

        if (wFormatTag == WAVE_FORMAT_EXTENSIBLE) {
            // EXTENSIBLE requires at least 40 bytes of fmt chunk:
            // 16 common + 2 cbSize + 2 wValidBits + 4 channelMask + 16 GUID.
            if (declaredSize < 40L) {
                throw new IOException(
                        "Malformed WAV: WAVEFORMATEXTENSIBLE 'fmt ' chunk must be"
                                + " at least 40 bytes, got " + declaredSize + ".");
            }
            int cbSize = reader.readU16LE();
            bytesConsumed += 2L;
            if (cbSize < 22) {
                throw new IOException(
                        "Malformed WAV: WAVEFORMATEXTENSIBLE 'fmt ' chunk declares"
                                + " cbSize=" + cbSize + "; must be >= 22.");
            }
            int wValidBitsPerSample = reader.readU16LE();
            reader.readU32LE(); // dwChannelMask (not used by this module)
            bytesConsumed += 6L;
            byte[] subFormatGuid = new byte[16];
            reader.readBytes(subFormatGuid, 0, 16);
            bytesConsumed += 16L;
            // Dispatch on the SubFormat GUID.
            if (byteArrayEquals(subFormatGuid, SUBTYPE_PCM_GUID)) {
                encoding = WaveFormat.Encoding.PCM_SIGNED;
                effectiveFormatTag = WAVE_FORMAT_PCM;
            } else if (byteArrayEquals(subFormatGuid, SUBTYPE_IEEE_FLOAT_GUID)) {
                encoding = WaveFormat.Encoding.IEEE_FLOAT;
                effectiveFormatTag = WAVE_FORMAT_IEEE_FLOAT;
            } else {
                throw new UnsupportedAudioFormatException(
                        "WAV uses WAVEFORMATEXTENSIBLE with unsupported SubFormat GUID "
                                + formatGuid(subFormatGuid) + ".");
            }
            // For EXTENSIBLE, the decoder uses the CONTAINER bit depth
            // (wBitsPerSample), which is the number of bytes per sample
            // actually written to disk; wValidBitsPerSample indicates
            // how many of those bits carry signal (the rest are zero).
            // The PCM integer decoder already produces normalised doubles
            // by dividing by 2^(containerBits - 1) &mdash; any unused
            // low bits simply read as zero, which is correct.
            //
            // For example, a file with wBitsPerSample=32 and
            // wValidBitsPerSample=24 is read as 32-bit PCM: the top 24
            // bits carry the signal, the bottom 8 are zero, and the
            // result after normalisation is identical to what a pure
            // 24-bit PCM file would produce (modulo a scale factor in
            // the least significant bits, which is below the decoder's
            // double precision).
            //
            // Accept wValidBitsPerSample purely for validation: the
            // parser rejects pathological combinations (validBits > containerBits).
            if (wValidBitsPerSample <= 0 || wValidBitsPerSample > wBitsPerSample) {
                throw new IOException(
                        "Malformed WAV: WAVEFORMATEXTENSIBLE wValidBitsPerSample="
                                + wValidBitsPerSample + " must be in (0, "
                                + wBitsPerSample + "].");
            }
            effectiveBitDepth = wBitsPerSample;
        } else if (wFormatTag == WAVE_FORMAT_PCM) {
            encoding = WaveFormat.Encoding.PCM_SIGNED;
        } else if (wFormatTag == WAVE_FORMAT_IEEE_FLOAT) {
            encoding = WaveFormat.Encoding.IEEE_FLOAT;
        } else {
            // Any other tag (0x0006 A-law, 0x0007 μ-law, 0x0011 IMA
            // ADPCM, etc.) is a compressed encoding we explicitly do
            // not support. Requirement 9.10 requires identifying the
            // code in the message.
            throw new UnsupportedAudioFormatException(
                    "WAV uses unsupported compression code 0x"
                            + String.format("%04X", wFormatTag)
                            + " (" + compressionName(wFormatTag) + ").");
        }

        // Bit-depth / encoding sanity checks. WaveFormat's compact
        // constructor would catch these too, but raising here gives a
        // cleaner, format-specific message.
        if (encoding == WaveFormat.Encoding.PCM_SIGNED
                && effectiveBitDepth != 16
                && effectiveBitDepth != 24
                && effectiveBitDepth != 32) {
            throw new IOException(
                    "Malformed WAV: PCM signed-integer bit depth must be one of"
                            + " {16, 24, 32}, got " + effectiveBitDepth + ".");
        }
        if (encoding == WaveFormat.Encoding.IEEE_FLOAT
                && effectiveBitDepth != 32
                && effectiveBitDepth != 64) {
            throw new IOException(
                    "Malformed WAV: IEEE float bit depth must be one of {32, 64},"
                            + " got " + effectiveBitDepth + ".");
        }
        if (nChannels <= 0) {
            throw new IOException(
                    "Malformed WAV: channel count must be positive, got "
                            + nChannels + ".");
        }
        if (nSamplesPerSec <= 0L || nSamplesPerSec > Integer.MAX_VALUE) {
            throw new IOException(
                    "Malformed WAV: sample rate out of range (got " + nSamplesPerSec + ").");
        }

        // Validate nBlockAlign against (bitDepth/8) * channels for
        // uncompressed formats. Some encoders set nBlockAlign to the
        // container size rather than the valid-bits size; trust the
        // effective bit depth for PCM / IEEE float.
        int expectedBlockAlign = ((effectiveBitDepth + 7) / 8) * nChannels;
        if (nBlockAlign != expectedBlockAlign) {
            throw new IOException(
                    "Malformed WAV: nBlockAlign=" + nBlockAlign
                            + " does not match (bitDepth/8 * channels) = "
                            + expectedBlockAlign + ".");
        }

        // Skip any trailing 'fmt ' bytes we did not consume (allowed by
        // the spec: cbSize can advertise a payload larger than 22).
        long remaining = declaredSize - bytesConsumed;
        if (remaining > 0L) {
            reader.skip(remaining);
        }

        return new FmtChunk(
                effectiveFormatTag,
                nChannels,
                (int) nSamplesPerSec,
                effectiveBitDepth,
                nBlockAlign,
                encoding);
    }

    // ------------------------------------------------------------------
    // ds64 chunk parsing (RF64)
    // ------------------------------------------------------------------

    /**
     * Parse the payload of a {@code ds64} chunk for RF64 files. Only the
     * first two 64-bit fields ({@code riffSize64} and {@code dataSize64})
     * are used by this module; {@code sampleCount64} and the optional
     * override table are read and discarded to advance past the chunk.
     */
    private static Ds64Info parseDs64Chunk(RiffReader reader, long declaredSize)
            throws IOException {
        if (declaredSize < 28L) {
            throw new IOException(
                    "Malformed RF64: 'ds64' chunk must be at least 28 bytes, got "
                            + declaredSize + ".");
        }
        long riffSize64 = reader.readU64LE();
        long dataSize64 = reader.readU64LE();
        reader.readU64LE(); // sampleCount64 (not used)
        reader.readU32LE(); // tableLength (we do not use the override table)
        // Skip any remaining bytes (override table + padding).
        long remaining = declaredSize - 28L;
        if (remaining > 0L) {
            reader.skip(remaining);
        }
        if (riffSize64 < 0L || dataSize64 < 0L) {
            // Guard against absurdly large RF64 sizes that would wrap
            // into negative longs when multiplied later.
            throw new IOException(
                    "Malformed RF64: 'ds64' sizes exceed the signed-long range"
                            + " (riffSize64=" + riffSize64 + ", dataSize64=" + dataSize64 + ").");
        }
        return new Ds64Info(riffSize64, dataSize64);
    }

    // ------------------------------------------------------------------
    // Low-level helpers
    // ------------------------------------------------------------------

    /** Byte-wise equality check, length-aware. */
    private static boolean byteArrayEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Format a 16-byte {@code SubFormat} GUID as the canonical dashed
     * hexadecimal string for diagnostic messages.
     */
    private static String formatGuid(byte[] guid) {
        // GUID layout on disk: Data1 (4 LE), Data2 (2 LE), Data3 (2 LE),
        // Data4 (8 BE).
        int d1 = (guid[0] & 0xFF)
              | ((guid[1] & 0xFF) << 8)
              | ((guid[2] & 0xFF) << 16)
              | ((guid[3] & 0xFF) << 24);
        int d2 = (guid[4] & 0xFF) | ((guid[5] & 0xFF) << 8);
        int d3 = (guid[6] & 0xFF) | ((guid[7] & 0xFF) << 8);
        StringBuilder sb = new StringBuilder(36);
        sb.append(String.format("%08X", d1));
        sb.append('-');
        sb.append(String.format("%04X", d2 & 0xFFFF));
        sb.append('-');
        sb.append(String.format("%04X", d3 & 0xFFFF));
        sb.append('-');
        sb.append(String.format("%02X%02X", guid[8] & 0xFF, guid[9] & 0xFF));
        sb.append('-');
        for (int i = 10; i < 16; i++) {
            sb.append(String.format("%02X", guid[i] & 0xFF));
        }
        return sb.toString();
    }

    /** Human-readable label for common compressed {@code wFormatTag} codes. */
    private static String compressionName(int formatTag) {
        switch (formatTag) {
            case 0x0002: return "MS ADPCM";
            case 0x0006: return "A-law";
            case 0x0007: return "mu-law";
            case 0x0011: return "IMA ADPCM";
            case 0x0031: return "GSM 6.10";
            case 0x0050: return "MPEG";
            case 0x0055: return "MP3";
            case 0x0092: return "Dolby AC-3";
            case 0x0161: return "WMA";
            case 0x0162: return "WMA Pro";
            default:     return "unknown compression";
        }
    }

    // ------------------------------------------------------------------
    // Private records used by the parser
    // ------------------------------------------------------------------

    /**
     * Intermediate carrier for parsed {@code fmt } chunk data, held
     * between {@link #parseFmtChunk(RiffReader, long)} and
     * {@link #parseHeader(RiffReader, boolean)}.
     */
    private static final class FmtChunk {
        final int formatTag;
        final int channelCount;
        final int sampleRate;
        final int bitDepth;
        final int blockAlign;
        final WaveFormat.Encoding encoding;

        FmtChunk(int formatTag,
                 int channelCount,
                 int sampleRate,
                 int bitDepth,
                 int blockAlign,
                 WaveFormat.Encoding encoding) {
            this.formatTag = formatTag;
            this.channelCount = channelCount;
            this.sampleRate = sampleRate;
            this.bitDepth = bitDepth;
            this.blockAlign = blockAlign;
            this.encoding = encoding;
        }

        int resolvedBitDepth() {
            // Validated to be one of {16, 24, 32, 64} by parseFmtChunk
            // before construction; the WaveFormat constructor will
            // re-validate, so we simply pass the stored value through.
            return bitDepth;
        }

        int resolvedBytesPerFrame() {
            return blockAlign;
        }
    }

    /**
     * Intermediate carrier for parsed {@code ds64} chunk data in RF64
     * files.
     */
    private static final class Ds64Info {
        final long riffSize64;
        final long dataSize64;

        Ds64Info(long riffSize64, long dataSize64) {
            this.riffSize64 = riffSize64;
            this.dataSize64 = dataSize64;
        }
    }
}
