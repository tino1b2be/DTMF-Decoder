package com.tino1b2be.dtmf.io;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration test verifying that {@link java.util.ServiceLoader} discovers
 * both the WAV and MP3 providers when they are on the runtime classpath
 * (Requirements 16.1, 16.2).
 *
 * <p>This is the only test in the suite that exercises the raw discovery
 * path end-to-end; every other integration test builds on the same cache
 * but does not re-verify provider enumeration. The assertion is
 * order-independent on purpose: {@code ServiceLoader} discovery order is
 * classpath-dependent, and Gradle's multi-module build offers no
 * first-class way to fix the order between {@code dtmf-io-wav} and
 * {@code dtmf-io-mp3} — what matters for callers is that both names are
 * present.
 *
 * @since 2.0.0
 */
final class RegisteredFormatsIT {

    @Test
    @DisplayName("registeredFormats() contains both WAV and MP3")
    void registeredFormatsContainsWavAndMp3() {
        List<String> formats = AudioSources.registeredFormats();

        assertAll(
                () -> assertTrue(
                        formats.contains("WAV"),
                        () -> "Expected registeredFormats() to contain \"WAV\", "
                                + "got " + formats),
                () -> assertTrue(
                        formats.contains("MP3"),
                        () -> "Expected registeredFormats() to contain \"MP3\", "
                                + "got " + formats));
    }
}
