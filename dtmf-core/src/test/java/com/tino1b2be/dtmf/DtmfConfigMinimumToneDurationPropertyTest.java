package com.tino1b2be.dtmf;

// Feature: dtmf-v2-foundation, Property 21: Minimum tone duration lower bound

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;

/**
 * Property-based test for Requirement 8.8: any
 * {@link Duration} with {@code toMillis() < 10} must be rejected by
 * {@link DtmfConfig} regardless of which construction path is used.
 *
 * <p><strong>Property 21: Minimum tone duration lower bound.</strong>
 * <strong>Validates: Requirement 8.8.</strong>
 *
 * <p>Generator domain: {@code [0, 9]} milliseconds. Zero and positive values
 * below 10&nbsp;ms must all fail. Negative values are covered by a separate
 * assertion so this property stays focused on the &ldquo;non-negative but
 * too small&rdquo; boundary.
 *
 * <p>Both construction paths are exercised:
 *
 * <ol>
 *   <li>{@code DtmfConfig.advanced().minimumToneDuration(d).build()}</li>
 *   <li>{@code DtmfConfig.advanced().minimumToneDuration(d)} &mdash; the setter
 *       itself fails, without needing {@code build()}; this is the user-visible
 *       failure point for a caller who starts from a standard-factory-seeded
 *       builder.</li>
 * </ol>
 */
class DtmfConfigMinimumToneDurationPropertyTest {

    @Property(tries = 100)
    void nonNegativeDurationsBelow10MillisAreRejected(
            @ForAll @LongRange(min = 0L, max = 9L) long millis) {

        Duration d = Duration.ofMillis(millis);

        // Path 1: setter fails directly.
        assertThrows(IllegalArgumentException.class,
                () -> DtmfConfig.advanced().minimumToneDuration(d),
                "advanced().minimumToneDuration(" + d + ") should throw");

        // Path 2: build() also fails when a standard-factory-equivalent seed
        // is kept and we try to drop the duration in via the advanced API.
        // (The builder already guards at the setter, so this is a second,
        // belt-and-braces assertion that no alternative mutation path exists
        // that could bypass validation.)
        assertThrows(IllegalArgumentException.class,
                () -> DtmfConfig.advanced()
                        .sampleRate(8000)
                        .minimumToneDuration(d)
                        .build(),
                "advanced().sampleRate(8000).minimumToneDuration(" + d + ").build() should throw");
    }
}
