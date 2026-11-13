package io.github.oliverjuhasz.boardkeeper.plays;

import java.util.UUID;

/**
 * Published when a Play has been recorded. Other modules, such as statistics, listen for it
 * to keep their own data up to date (ADR 0011).
 *
 * <p>It carries only the Play's id. A listener asks {@link Plays#find} for the Play as it is
 * when the listener runs, so it never works from an outdated copy.
 */
public record PlayRecorded(UUID playId) {}
