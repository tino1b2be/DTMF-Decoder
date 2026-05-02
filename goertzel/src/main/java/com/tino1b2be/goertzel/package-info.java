/**
 * General-purpose Goertzel filter and filter-bank primitives.
 *
 * <p>The {@code goertzel} module is the v2 foundation's leaf library: it has
 * no runtime dependencies outside the JDK and is useful on its own for
 * frequency-domain analysis at arbitrary target frequencies. The DTMF
 * detection logic in {@code com.tino1b2be.dtmf} is built on top of the
 * types declared in this package.
 *
 * <p>Public API arrives in Stage 2 of the dtmf-v2-foundation spec:
 * {@code GoertzelFilter} (single-frequency streaming evaluator) and
 * {@code GoertzelBank} (multi-frequency streaming and batch evaluator).
 * This {@code package-info.java} exists so that the source tree is present
 * from Stage 1 onward, which lets the build-shape smoke tests in Task 1.9
 * resolve the package at runtime before any implementation is checked in.
 */
package com.tino1b2be.goertzel;
