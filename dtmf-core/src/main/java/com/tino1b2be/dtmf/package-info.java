/**
 * DTMF detection, generation, and streaming API for DTMF-Decoder v2.
 *
 * <p>This package hosts the public surface of the {@code dtmf-core} module:
 * {@code DtmfDecoder} (batch), {@code DtmfDetector} (push), {@code DtmfStream}
 * (pull), {@code DtmfGenerator}, together with the immutable value types
 * {@code DtmfTone} and {@code DtmfConfig} and the {@code ChannelMode} /
 * {@code WindowFunction} enums. Implementation details live under
 * {@code com.tino1b2be.dtmf.internal} and are not part of the published API.
 *
 * <p>The module depends on {@code com.tino1b2be.goertzel} and only on
 * {@code com.tino1b2be.goertzel} at runtime (Requirement 1.5). The concrete
 * types are introduced starting at Stage 3 of the dtmf-v2-foundation spec;
 * this {@code package-info.java} is present from Stage 1 so the source tree
 * exists for the build-shape smoke tests in Task 1.9.
 */
package com.tino1b2be.dtmf;
