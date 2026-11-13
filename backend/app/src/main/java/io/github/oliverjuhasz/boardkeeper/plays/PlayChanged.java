package io.github.oliverjuhasz.boardkeeper.plays;

import java.util.UUID;

/** Published when what was entered for a Play has been changed, and it was scored again. */
public record PlayChanged(UUID playId) {}
