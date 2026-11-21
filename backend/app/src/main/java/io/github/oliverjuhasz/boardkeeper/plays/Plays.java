package io.github.oliverjuhasz.boardkeeper.plays;

import io.github.oliverjuhasz.boardkeeper.access.CurrentAccount;
import io.github.oliverjuhasz.boardkeeper.access.Permission;
import io.github.oliverjuhasz.boardkeeper.gameapi.GameModule;
import io.github.oliverjuhasz.boardkeeper.gameapi.GroupEntry;
import io.github.oliverjuhasz.boardkeeper.gameapi.InvalidPlayException;
import io.github.oliverjuhasz.boardkeeper.gameapi.Outcome;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayEntry;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayerEntry;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayerResult;
import io.github.oliverjuhasz.boardkeeper.games.Game;
import io.github.oliverjuhasz.boardkeeper.games.GameModules;
import io.github.oliverjuhasz.boardkeeper.games.Games;
import io.github.oliverjuhasz.boardkeeper.players.Players;
import io.github.oliverjuhasz.boardkeeper.plays.internal.PlayRepository;
import io.github.oliverjuhasz.boardkeeper.problems.Problem;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recording Plays, looking them up, and changing them. */
@Service
public class Plays {

    public static final int MAX_LOCATION_LENGTH = 100;
    public static final int MAX_NOTES_LENGTH = 2000;

    /** Far more than any Game seats. It only stops absurd requests. */
    public static final int MAX_PLAYERS = 100;

    /** How many Plays a page of the history has unless asked otherwise, and at most. */
    public static final int DEFAULT_PAGE_SIZE = 50;

    public static final int MAX_PAGE_SIZE = 100;

    private static final String NOT_FOUND = "plays.notFound";

    private final PlayRepository repository;
    private final Games games;
    private final GameModules gameModules;
    private final Players players;
    private final ApplicationEventPublisher events;

    Plays(
            PlayRepository repository,
            Games games,
            GameModules gameModules,
            Players players,
            ApplicationEventPublisher events) {
        this.repository = repository;
        this.games = games;
        this.gameModules = gameModules;
        this.players = players;
        this.events = events;
    }

    /** The ids of every Play, for a module that builds data of its own from all of them. */
    public List<UUID> allIds() {
        return repository.allIds();
    }

    public Optional<Play> find(UUID id) {
        return repository.find(id);
    }

    /** Every Play, the oldest first, as for an Export. */
    public List<Play> all() {
        return repository.findAll();
    }

    /** The day each Game was last played on, by the Game's id. A Game without Plays is missing. */
    public Map<UUID, LocalDate> lastPlayedOn() {
        return repository.lastPlayedOnByGame();
    }

    /**
     * A page of the play history, newest first.
     *
     * @param after where the page before this one ended; null for the first page
     */
    public PlayHistory history(PlayHistory.Filter filter, PlayHistory.Position after, int pageSize) {
        int size = Math.clamp(pageSize, 1, MAX_PAGE_SIZE);
        // One more than asked for, to learn whether there is a next page without counting.
        List<Play> plays = repository.findPage(filter, after, size + 1);
        if (plays.size() <= size) {
            return new PlayHistory(plays, null);
        }
        Play last = plays.get(size - 1);
        return new PlayHistory(plays.subList(0, size), new PlayHistory.Position(last.playedOn(), last.id()));
    }

    /**
     * Records a Play: has the Game's Game Module score what was entered, and stores the entry
     * together with the Results (ADR 0010).
     *
     * <p>What the module refuses is reported as the Problem {@code plays.invalidEntry}, with the
     * module's own reason.
     */
    @Transactional
    public Play record(NewPlay newPlay, CurrentAccount recorder) {
        return record(null, newPlay, recorder);
    }

    /**
     * Records a Play under an id the recorder's device chose. A phone that recorded a Play
     * without a connection sends it later, and may send it again when it never heard the
     * answer. So a Play is recorded once per id: sending it again gives back the Play already
     * recorded, unchanged.
     *
     * @param id the new Play's id; null to have one made. It should be a UUID of version 7,
     *     which starts with the time, as the ids the database makes do: the play history puts
     *     the Plays of a day in the order of their ids.
     * @throws Problem {@code plays.idTaken} when another Account recorded a Play with this id
     */
    @Transactional
    public Play record(UUID id, NewPlay newPlay, CurrentAccount recorder) {
        if (id != null) {
            Optional<Play> earlier = repository.find(id);
            if (earlier.isPresent()) {
                return sentAgain(earlier.get(), recorder);
            }
        }
        Game game = games.find(newPlay.gameId()).orElseThrow(() -> Problem.notFound("games.notFound"));
        checkPlayersAndDate(newPlay);
        Scored scored = score(moduleOf(game.gameModuleId()), newPlay.playerIds(), newPlay.entry());

        Optional<UUID> recorded = repository.insert(id, game, newPlay, scored.group(), scored.results(), recorder.playerId());
        if (recorded.isEmpty()) {
            // The same id arrived twice at the same moment, and the other request won.
            return sentAgain(repository.find(id).orElseThrow(), recorder);
        }
        // Published inside the transaction. Listeners marked @ApplicationModuleListener run
        // only once it has been committed, so they never see a Play that was rolled back.
        events.publishEvent(new PlayRecorded(recorded.get()));
        return repository.find(recorded.get()).orElseThrow();
    }

    private static Play sentAgain(Play earlier, CurrentAccount recorder) {
        if (!earlier.recordedBy().equals(recorder.playerId())) {
            throw Problem.conflict("plays.idTaken");
        }
        return earlier;
    }

    /**
     * Works out how a Play would end without recording it, for a form that wants to show the
     * totals and Placings while it is being filled in, and to know whether there is a tie the
     * recorder has to decide.
     */
    public PlayPreview preview(UUID gameModuleId, List<UUID> playerIds, PlayEntry entry) {
        if (playerIds.size() != entry.players().size()) {
            throw new IllegalArgumentException("A Play needs exactly one entry for each of its Players");
        }
        Scored scored = score(moduleOf(gameModuleId), playerIds, entry);
        return new PlayPreview(scored.group(), scored.results(), scored.tieForRecorder());
    }

    /**
     * Whether an Account may change a Play, which means edit or delete it. There are three
     * Permissions for it: for Plays the Account recorded, for Plays its Player took part in,
     * and for any Play.
     */
    public boolean mayChange(Play play, CurrentAccount account) {
        if (account.has(Permission.CHANGE_ANY_PLAY)) {
            return true;
        }
        if (account.has(Permission.CHANGE_PLAYS_RECORDED) && play.recordedBy().equals(account.playerId())) {
            return true;
        }
        return account.has(Permission.CHANGE_PLAYS_TAKEN_PART_IN)
                && play.results().stream().anyMatch(result -> result.playerId().equals(account.playerId()));
    }

    /**
     * Replaces everything that was entered for a Play and scores it again. Its Game cannot be
     * changed, and who recorded it and when stay as they were.
     *
     * <p>The Play is scored by the Game Module that scored it before, because what was entered
     * for it is in that module's terms.
     */
    @Transactional
    public Play change(UUID id, NewPlay changed, CurrentAccount account) {
        Play play = changeable(id, account);
        if (!play.gameId().equals(changed.gameId())) {
            throw Problem.unprocessable("plays.gameCannotChange");
        }
        checkPlayersAndDate(changed);
        Scored scored = score(moduleOf(play.gameModuleId()), changed.playerIds(), changed.entry());

        repository.update(id, changed, scored.group(), scored.results());
        events.publishEvent(new PlayChanged(id));
        return repository.find(id).orElseThrow();
    }

    /** What a Recalculation of one Play did: the Play as it is now, and whether its Results changed. */
    public record Recalculated(Play play, boolean changed) {}

    /**
     * What a Recalculation of every Play of a Game did.
     *
     * @param plays how many Plays the Game has
     * @param changed how many of them got different Results
     * @param refused the Plays the Game Module refuses now, which kept their Results
     */
    public record GameRecalculation(int plays, int changed, List<Refused> refused) {}

    /**
     * A Play that could not be recalculated.
     *
     * @param reason the name of the text that says why, such as "plays.invalid.scoreMissing"
     */
    public record Refused(UUID playId, LocalDate playedOn, String reason) {}

    /**
     * Runs the Game Module's scoring again over what was entered for a Play, and stores the new
     * Results if they differ (ADR 0010). It uses the module that scored the Play before, and the
     * Play's own Setup, Expansions included. Whoever may change the Play may do this.
     *
     * @throws Problem when the module refuses the Play now; it then keeps its Results
     */
    @Transactional
    public Recalculated recalculate(UUID id, CurrentAccount account) {
        return recalculate(changeable(id, account));
    }

    /**
     * Recalculates every Play of a Game, each as {@link #recalculate(UUID, CurrentAccount)} does.
     * A Play the module refuses keeps its Results and is listed in the answer. Only for Admins.
     */
    @Transactional
    public GameRecalculation recalculateGame(UUID gameId) {
        games.find(gameId).orElseThrow(() -> Problem.notFound("games.notFound"));
        List<UUID> ids = repository.idsOfGame(gameId);
        int changed = 0;
        List<Refused> refused = new ArrayList<>();
        for (UUID id : ids) {
            Play play = repository.find(id).orElseThrow();
            try {
                if (recalculate(play).changed()) {
                    changed++;
                }
            } catch (Problem refusal) {
                refused.add(new Refused(id, play.playedOn(), refusal.reason() != null ? refusal.reason() : refusal.code()));
            }
        }
        return new GameRecalculation(ids.size(), changed, refused);
    }

    private Recalculated recalculate(Play play) {
        PlayEntry entry = entryOf(play);
        List<UUID> playerIds = play.results().stream().map(Play.Result::playerId).toList();
        Scored scored = score(moduleOf(play.gameModuleId()), playerIds, entry);
        if (scored.results().equals(play.results()) && Objects.equals(scored.group(), play.group())) {
            return new Recalculated(play, false);
        }
        repository.replaceResults(play.id(), scored.group(), scored.results());
        events.publishEvent(new PlayChanged(play.id()));
        return new Recalculated(repository.find(play.id()).orElseThrow(), true);
    }

    /** Which of these Plays exist, by id. */
    public Set<UUID> existing(Collection<UUID> ids) {
        return repository.existingIds(ids);
    }

    /**
     * Checks that this Installation can hold a Play copied from an Export as it was (ADR 0020):
     * that the Game Module which scored it is installed and accepts what was entered, and that it
     * keeps the rules the core itself knows. Its Results are not compared with the module's: a
     * copy keeps those it was given. Its Game and Players are not looked up, because an Import may
     * still have to create them.
     *
     * @throws Problem saying why not, as recording the Play would
     */
    public void checkCopy(Play play) {
        List<UUID> playerIds = play.results().stream().map(Play.Result::playerId).toList();
        if (new HashSet<>(playerIds).size() != playerIds.size()) {
            throw Problem.unprocessable("plays.playerTwice");
        }
        // What Board Keeper never writes into an Export, but someone may have changed in the file.
        if (lengthOf(play.location()) > MAX_LOCATION_LENGTH
                || lengthOf(play.notes()) > MAX_NOTES_LENGTH
                || play.cooperative() != (play.group() != null)
                || play.results().stream().anyMatch(result -> result.placing() != null && result.placing() < 1)) {
            throw Problem.unprocessable("request.invalid");
        }
        score(moduleOf(play.gameModuleId()), playerIds, entryOf(play));
    }

    /**
     * Stores a Play from an Export as it was where it was recorded (ADR 0020): with its id, what
     * was entered, the Results its Game Module calculated there, and who recorded it and when. Its
     * Game and its Players must exist here.
     *
     * @return false when a Play with its id is here already. That one is left as it is.
     * @throws Problem when this Installation can't hold the Play; see {@link #checkCopy(Play)}
     */
    @Transactional
    public boolean copy(Play play) {
        checkCopy(play);
        games.find(play.gameId()).orElseThrow(() -> Problem.notFound("games.notFound"));
        Set<UUID> playerIds = new HashSet<>();
        playerIds.add(play.recordedBy());
        play.results().forEach(result -> playerIds.add(result.playerId()));
        if (players.find(playerIds).size() != playerIds.size()) {
            throw Problem.notFound("players.notFound");
        }
        Play tidied = new Play(
                play.id(),
                play.gameId(),
                play.gameModuleId(),
                play.playedOn(),
                tidy(play.location()),
                tidy(play.notes()),
                play.recordingMode(),
                play.setup(),
                play.victoryType(),
                play.cooperative(),
                play.group(),
                play.results(),
                play.recordedBy(),
                play.recordedAt());
        if (!repository.insertCopy(tidied)) {
            return false;
        }
        events.publishEvent(new PlayRecorded(play.id()));
        return true;
    }

    /** Without spaces around it; null when nothing is left. */
    private static String tidy(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static int lengthOf(String text) {
        return text == null ? 0 : text.strip().length();
    }

    /** What was entered for a stored Play, as it was given to its Game Module. */
    private static PlayEntry entryOf(Play play) {
        return new PlayEntry(
                play.recordingMode(),
                play.setup(),
                play.results().stream()
                        .map(result -> new PlayerEntry(result.scores(), result.markedWinner(), result.setup()))
                        .toList(),
                play.group() == null ? null : new GroupEntry(play.group().won(), play.group().scores()),
                play.victoryType());
    }

    /** Deletes a Play for good. */
    @Transactional
    public void delete(UUID id, CurrentAccount account) {
        changeable(id, account);
        repository.delete(id);
        events.publishEvent(new PlayDeleted(id));
    }

    /** The Play, if it exists and the Account may change it. */
    private Play changeable(UUID id, CurrentAccount account) {
        Play play = repository.find(id).orElseThrow(() -> Problem.notFound(NOT_FOUND));
        if (!mayChange(play, account)) {
            throw Problem.forbidden("access.denied");
        }
        return play;
    }

    /** A Game can outlive its module: an installation may be running a build without it. */
    private GameModule moduleOf(UUID gameModuleId) {
        return gameModules.byId(gameModuleId).orElseThrow(() -> Problem.conflict("plays.gameModuleMissing"));
    }

    /** What the core itself knows the rules for. Everything about the Game is the module's to check. */
    private void checkPlayersAndDate(NewPlay newPlay) {
        List<UUID> playerIds = newPlay.playerIds();
        if (new HashSet<>(playerIds).size() != playerIds.size()) {
            throw Problem.unprocessable("plays.playerTwice");
        }
        if (players.find(playerIds).size() != playerIds.size()) {
            throw Problem.notFound("players.notFound");
        }
        // The server's clock says which day it is, but the recorder may be somewhere that is
        // already a day ahead of it, so tomorrow is let through.
        if (newPlay.playedOn().isAfter(LocalDate.now(ZoneOffset.UTC).plusDays(1))) {
            throw Problem.unprocessable("plays.dateInFuture");
        }
    }

    /** Has the Game Module score the entry, and puts its answer next to what was entered. */
    private static Scored score(GameModule module, List<UUID> playerIds, PlayEntry entry) {
        Outcome outcome;
        try {
            outcome = module.score(entry);
        } catch (InvalidPlayException refusal) {
            // The frontend has the texts for the reasons that are the same for every Game, and
            // the module has the texts for its own.
            String reason = refusal.isStandard()
                    ? "plays.invalid." + refusal.reason()
                    : module.key() + ".invalid." + refusal.reason();
            throw Problem.unprocessable("plays.invalidEntry", reason);
        }

        Play.GroupResult group = null;
        List<Play.Result> results = new ArrayList<>();
        List<UUID> tieForRecorder = List.of();
        // Outcome is sealed, so the compiler checks that both kinds are handled here.
        switch (outcome) {
            case Outcome.Competitive competitive -> {
                if (competitive.results().size() != playerIds.size() || entry.group() != null) {
                    throw new IllegalStateException(
                            "Game Module '" + module.key() + "' did not give one Result for each Player and nothing else");
                }
                for (int i = 0; i < playerIds.size(); i++) {
                    PlayerEntry entered = entry.players().get(i);
                    PlayerResult calculated = competitive.results().get(i);
                    results.add(new Play.Result(
                            playerIds.get(i),
                            entered.scores(),
                            entered.setup(),
                            entered.winner(),
                            calculated.total(),
                            calculated.placing(),
                            calculated.won()));
                }
                tieForRecorder = competitive.tieForRecorder().stream().map(playerIds::get).toList();
            }
            case Outcome.Cooperative cooperative -> {
                Map<String, Integer> groupScores = entry.group() == null ? Map.of() : entry.group().scores();
                group = new Play.GroupResult(cooperative.won(), groupScores, cooperative.total());
                // Everyone shares the group's fate, so each Player's history can show won or lost.
                for (int i = 0; i < playerIds.size(); i++) {
                    PlayerEntry entered = entry.players().get(i);
                    results.add(new Play.Result(
                            playerIds.get(i),
                            entered.scores(),
                            entered.setup(),
                            entered.winner(),
                            null,
                            null,
                            cooperative.won()));
                }
            }
        }
        return new Scored(group, results, tieForRecorder);
    }

    /** A Game Module's answer in the form it is stored in. {@code group} is null for a competitive Play. */
    private record Scored(Play.GroupResult group, List<Play.Result> results, List<UUID> tieForRecorder) {}
}
