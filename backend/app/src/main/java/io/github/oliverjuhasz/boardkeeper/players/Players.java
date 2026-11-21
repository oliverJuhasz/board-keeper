package io.github.oliverjuhasz.boardkeeper.players;

import io.github.oliverjuhasz.boardkeeper.players.internal.PlayerRepository;
import io.github.oliverjuhasz.boardkeeper.problems.Problem;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What the other modules can do with Players. */
@Service
public class Players {

    public static final int MAX_NAME_LENGTH = 50;

    private static final String NAME_TAKEN = "players.nameTaken";
    private static final String NOT_FOUND = "players.notFound";

    private final PlayerRepository repository;

    Players(PlayerRepository repository) {
        this.repository = repository;
    }

    /** Every Player, ordered by name. */
    public List<Player> all() {
        return repository.findAllOrderedByName();
    }

    public Optional<Player> find(UUID id) {
        return repository.find(id);
    }

    /** The Players with these ids, by id. An id that no Player has is simply missing from the answer. */
    public Map<UUID, Player> find(Collection<UUID> ids) {
        return repository.findAll(ids).stream().collect(Collectors.toMap(Player::id, Function.identity()));
    }

    /** The Player with this name, whatever its capitals. */
    public Optional<Player> findByName(String name) {
        return repository.findIdByName(name.strip()).flatMap(repository::find);
    }

    /**
     * Creates a Player. Names are unique whatever their capitals; a name that is taken is
     * reported as the Problem {@code players.nameTaken}.
     */
    @Transactional
    public Player create(String name) {
        String trimmed = name.strip();
        if (repository.findIdByName(trimmed).isPresent()) {
            throw Problem.conflict(NAME_TAKEN);
        }
        try {
            return repository.insert(trimmed);
        } catch (DuplicateKeyException e) {
            // Someone created the same name between the check above and the insert.
            throw Problem.conflict(NAME_TAKEN);
        }
    }

    @Transactional
    public Player rename(UUID id, String name) {
        String trimmed = name.strip();
        // Changing only the capitals of a Player's own name is fine.
        if (repository.findIdByName(trimmed).filter(other -> !other.equals(id)).isPresent()) {
            throw Problem.conflict(NAME_TAKEN);
        }
        try {
            if (!repository.rename(id, trimmed)) {
                throw Problem.notFound(NOT_FOUND);
            }
        } catch (DuplicateKeyException e) {
            throw Problem.conflict(NAME_TAKEN);
        }
        return new Player(id, trimmed);
    }

    /**
     * Removes a Player that nothing refers to. A Player with an Account, or with Plays,
     * is protected by the database's foreign keys and reported as {@code players.inUse}.
     */
    @Transactional
    public void remove(UUID id) {
        try {
            if (!repository.delete(id)) {
                throw Problem.notFound(NOT_FOUND);
            }
        } catch (DataIntegrityViolationException e) {
            throw Problem.conflict("players.inUse");
        }
    }
}
