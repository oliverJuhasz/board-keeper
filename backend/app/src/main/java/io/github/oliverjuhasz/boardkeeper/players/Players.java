package io.github.oliverjuhasz.boardkeeper.players;

import io.github.oliverjuhasz.boardkeeper.players.internal.PlayerRepository;
import io.github.oliverjuhasz.boardkeeper.problems.Problem;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What the other modules can do with Players. */
@Service
public class Players {

    public static final int MAX_NAME_LENGTH = 50;

    private static final String NAME_TAKEN = "players.nameTaken";

    private final PlayerRepository repository;

    Players(PlayerRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates a Player. Names are unique whatever their capitals; a name that is taken is
     * reported as the Problem {@code players.nameTaken}.
     */
    @Transactional
    public Player create(String name) {
        String trimmed = name.strip();
        if (repository.existsByName(trimmed)) {
            throw Problem.conflict(NAME_TAKEN);
        }
        try {
            return repository.insert(trimmed);
        } catch (DuplicateKeyException e) {
            // Someone created the same name between the check above and the insert.
            throw Problem.conflict(NAME_TAKEN);
        }
    }

    public Optional<Player> find(UUID id) {
        return repository.find(id);
    }
}
