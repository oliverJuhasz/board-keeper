package io.github.oliverjuhasz.boardkeeper.players;

import java.util.UUID;

/** A person who takes part in Plays. */
public record Player(UUID id, String name) {}
