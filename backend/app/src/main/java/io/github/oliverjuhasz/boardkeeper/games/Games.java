package io.github.oliverjuhasz.boardkeeper.games;

import io.github.oliverjuhasz.boardkeeper.gameapi.GameModule;
import io.github.oliverjuhasz.boardkeeper.gameapi.RecordingMode;
import io.github.oliverjuhasz.boardkeeper.games.Game.RecordingDefaults;
import io.github.oliverjuhasz.boardkeeper.games.internal.GameModuleTexts;
import io.github.oliverjuhasz.boardkeeper.games.internal.GameRepository;
import io.github.oliverjuhasz.boardkeeper.problems.Problem;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The catalogue of Games. A Game added here by hand is scored by the Generic Module. A Game
 * with a Game Module of its own appears by itself: see {@link #addGamesOfModules()}.
 */
@Service
public class Games {

    public static final int MAX_NAME_LENGTH = 100;

    private static final String NAME_TAKEN = "games.nameTaken";
    private static final String NOT_FOUND = "games.notFound";
    private static final String HAS_OWN_MODULE = "games.hasOwnModule";

    private static final Logger log = LoggerFactory.getLogger(Games.class);

    private final GameRepository repository;
    private final GameModules gameModules;
    private final GameModuleTexts texts;

    Games(GameRepository repository, GameModules gameModules, GameModuleTexts texts) {
        this.repository = repository;
        this.gameModules = gameModules;
        this.texts = texts;
    }

    /**
     * Puts a Game in the catalogue for every installed Game Module that has none yet. It runs
     * when the application starts, so a Game with a module of its own appears by itself.
     *
     * <p>If someone added a Game by hand under the name the module gives its Game, in any
     * language, that Game is handed to the module instead: Plays recorded from then on use the
     * module's score pad. The Plays it already has stay as the Generic Module recorded them,
     * because each Play remembers the module that scored it.
     */
    @Transactional
    public void addGamesOfModules() {
        UUID generic = gameModules.generic().id();
        for (GameModule module : gameModules.all()) {
            if (module.id().equals(generic) || repository.existsByModule(module.id())) {
                continue;
            }
            Optional<Game> addedByHand = texts.namesOf(module).stream()
                    .flatMap(name -> repository.findIdByName(name).stream())
                    .findFirst()
                    .flatMap(repository::find);
            if (addedByHand.isEmpty()) {
                String name = texts.englishNameOf(module);
                repository.insert(name, module.id(), new RecordingDefaults(false, RecordingMode.POINTS, false));
                log.info("Added the Game '{}' to the catalogue for the Game Module '{}'", name, module.key());
            } else if (addedByHand.get().gameModuleId().equals(generic)) {
                repository.changeModule(addedByHand.get().id(), module.id());
                log.info("The Game '{}' is now scored by the Game Module '{}'", addedByHand.get().name(), module.key());
            } else {
                throw new IllegalStateException("The Game Module '" + module.key() + "' names its Game '"
                        + addedByHand.get().name() + "', like another Game Module");
            }
        }
    }

    /**
     * The key of the Game Module that scores a Game, such as {@code calico}. Empty when that
     * module is not installed in this build.
     */
    public Optional<String> moduleKeyOf(Game game) {
        return gameModules.byId(game.gameModuleId()).map(GameModule::key);
    }

    /**
     * Whether the Game has a Game Module of its own, and is not scored by the Generic Module.
     * Such a Game is named by its module and stays for as long as the module does, so it can
     * neither be edited nor removed.
     */
    public boolean hasOwnModule(Game game) {
        return !game.gameModuleId().equals(gameModules.generic().id());
    }

    /**
     * The Game that goes by this name, whatever its capitals: one in the catalogue under that name,
     * or the Game of a Game Module whose name it is in any language, as when the module arrived the
     * Game would have been handed to it.
     */
    public Optional<Game> findByName(String name) {
        String wanted = name.strip();
        Optional<Game> inCatalogue = repository.findIdByName(wanted).flatMap(repository::find);
        if (inCatalogue.isPresent()) {
            return inCatalogue;
        }
        UUID generic = gameModules.generic().id();
        return gameModules.all().stream()
                .filter(module -> !module.id().equals(generic))
                .filter(module -> texts.namesOf(module).stream().anyMatch(wanted::equalsIgnoreCase))
                .findFirst()
                .flatMap(module -> findByModule(module.id()));
    }

    /** The Game of a Game Module of its own. Every installed module has one, from startup on. */
    public Optional<Game> findByModule(UUID gameModuleId) {
        return repository.findByModule(gameModuleId);
    }

    /** Every Game, ordered by name. */
    public List<Game> all() {
        return repository.findAllOrderedByName();
    }

    public Optional<Game> find(UUID id) {
        return repository.find(id);
    }

    /** The Games with these ids, by id. An id that no Game has is simply missing from the answer. */
    public Map<UUID, Game> find(Collection<UUID> ids) {
        return repository.findAll(ids).stream().collect(Collectors.toMap(Game::id, Function.identity()));
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
        if (hasOwnModule(game)) {
            throw Problem.conflict(HAS_OWN_MODULE);
        }
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
        if (repository.find(id).filter(this::hasOwnModule).isPresent()) {
            throw Problem.conflict(HAS_OWN_MODULE);
        }
        try {
            if (!repository.delete(id)) {
                throw Problem.notFound(NOT_FOUND);
            }
        } catch (DataIntegrityViolationException e) {
            throw Problem.conflict("games.inUse");
        }
    }
}
