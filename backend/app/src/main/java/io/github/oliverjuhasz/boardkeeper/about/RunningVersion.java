package io.github.oliverjuhasz.boardkeeper.about;

/**
 * Which version of Board Keeper is running, for a module that notes it, as an Export notes which
 * Board Keeper wrote it. The About page tells more (ADR 0018).
 *
 * @param release the release that is running, such as {@code 1.2.3}; {@code null} for a Snapshot
 * @param commit the commit it was built from; {@code null} when it was built without git
 */
public record RunningVersion(String release, String commit) {}
