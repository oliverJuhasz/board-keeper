package io.github.oliverjuhasz.boardkeeper.games;

import io.github.oliverjuhasz.boardkeeper.games.Game.RecordingDefaults;
import io.github.oliverjuhasz.boardkeeper.games.internal.GameRepository;
import io.github.oliverjuhasz.boardkeeper.problems.Problem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The catalogue of Games. A Game added here by hand is scored by the Generic Module. Games
 * with a module of their own will appear by themselves once such modules exist (milestone M3).
 */
@Service
public class Games {

    public static final int MAX_NAME_LENGTH = 100;

    private static final String NAME_TAKEN = "games.nameTaken";
    private static final String NOT_FOUND = "games.notFound";

    private final GameRepository repository;
    private final GameModules gameModules;

    Games(GameRepository repository, GameModules gameModules) {
        this.repository = repository;
        this.gameModules = gameModules;
    }

    /** Every Game, ordered by name. */
    public List<Game> all() {
        return repository.findAllOrderedByName();
    }

    public Optional<Game> find(UUID id) {
        return repository.find(id);
    }

    @Transactional
    public Game create(String name, RecordingDefaults defaults) {
        String trimmed = name.strip();
        if (repository.findIdByName(trimmed).isPresent()) {
            throw Problem.conflict(NAME_TAKEN);
        }
        try {
            return repository.insert(trimmed, gameModules.generic().id(), defaults);
        } catch (DuplicateKeyException e) {
            throw Problem.conflict(NAME_TAKEN);
        }
    }

    @Transactional
    public Game update(UUID id, String name, RecordingDefaults defaults) {
        Game game = repository.find(id).orElseThrow(() -> Problem.notFound(NOT_FOUND));
        String trimmed = name.strip();
        if (repository.findIdByName(trimmed).filter(other -> !other.equals(id)).isPresent()) {
            throw Problem.conflict(NAME_TAKEN);
        }
        try {
            repository.update(id, trimmed, defaults);
        } catch (DuplicateKeyException e) {
            throw Problem.conflict(NAME_TAKEN);
        }
        return new Game(id, trimmed, game.gameModuleId(), defaults);
    }

    /** Removes a Game that has no Plays. One with Plays is protected by the database's foreign keys. */
    @Transactional
    public void remove(UUID id) {
        try {
            if (!repository.delete(id)) {
                throw Problem.notFound(NOT_FOUND);
            }
        } catch (DataIntegrityViolationException e) {
            throw Problem.conflict("games.inUse");
        }
    }
}
