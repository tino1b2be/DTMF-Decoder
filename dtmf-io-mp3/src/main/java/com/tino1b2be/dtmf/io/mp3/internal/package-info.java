/**
 * Package-private implementation details for {@code com.tino1b2be.dtmf.io.mp3}.
 *
 * <p>Nothing under this package is part of the published {@code dtmf-io-mp3}
 * API. Types declared here are package-private by convention (either
 * explicitly or by being non-{@code public}) and are free to change, move,
 * or disappear between any two releases without notice. External callers
 * MUST NOT depend on any class, method, constant, or file in this package.
 *
 * <p>Expected residents of this package include the MPEG frame-header
 * scanner that skips any leading ID3v2 tag, locates the 11-bit MPEG sync
 * pattern, and classifies the layer field so
 * {@code Mp3AudioSourceProvider.canOpen(...)} can score inputs by
 * Layer III content without consuming more than the first ten kilobytes
 * required by Requirement 10.5. All of those names are internal detail —
 * the public contract for MP3 decoding lives on
 * {@code Mp3AudioSourceProvider} and {@code Mp3AudioSource} in the parent
 * package.
 *
 * @since 2.0.0
 */
package com.tino1b2be.dtmf.io.mp3.internal;
