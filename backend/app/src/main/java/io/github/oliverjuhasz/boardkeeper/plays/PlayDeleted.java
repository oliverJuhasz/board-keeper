package io.github.oliverjuhasz.boardkeeper.plays;

import java.util.UUID;

/** Published when a Play has been deleted. */
public record PlayDeleted(UUID playId) {}
