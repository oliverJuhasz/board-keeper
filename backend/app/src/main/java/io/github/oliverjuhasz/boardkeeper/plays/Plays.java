package io.github.oliverjuhasz.boardkeeper.plays;

import io.github.oliverjuhasz.boardkeeper.access.CurrentAccount;
import io.github.oliverjuhasz.boardkeeper.access.Permission;
import io.github.oliverjuhasz.boardkeeper.gameapi.GameModule;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

    Plays(PlayRepository repository, Games games, GameModules gameModules, Players players) {
        this.repository = repository;
        this.games = games;
        this.gameModules = gameModules;
        this.players = players;
    }

    public Optional<Play> find(UUID id) {
        return repository.find(id);
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
        Game game = games.find(newPlay.gameId()).orElseThrow(() -> Problem.notFound("games.notFound"));
        checkPlayersAndDate(newPlay);
        Scored scored = score(moduleOf(game.gameModuleId()), newPlay);

        UUID id = repository.insert(game, newPlay, scored.group(), scored.results(), recorder.playerId());
        return repository.find(id).orElseThrow();
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
        Scored scored = score(moduleOf(play.gameModuleId()), changed);

        repository.update(id, changed, scored.group(), scored.results());
        return repository.find(id).orElseThrow();
    }

    /** Deletes a Play for good. */
    @Transactional
    public void delete(UUID id, CurrentAccount account) {
        changeable(id, account);
        repository.delete(id);
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
    private static Scored score(GameModule module, NewPlay newPlay) {
        List<UUID> playerIds = newPlay.playerIds();
        PlayEntry entry = newPlay.entry();
        Outcome outcome;
        try {
            outcome = module.score(entry);
        } catch (InvalidPlayException refusal) {
            throw Problem.unprocessable("plays.invalidEntry", module.key() + ".invalid." + refusal.reason());
        }

        Play.GroupResult group = null;
        List<Play.Result> results = new ArrayList<>();
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
                            entered.winner(),
                            calculated.total(),
                            calculated.placing(),
                            calculated.won()));
                }
            }
            case Outcome.Cooperative cooperative -> {
                Map<String, Integer> groupScores = entry.group() == null ? Map.of() : entry.group().scores();
                group = new Play.GroupResult(cooperative.won(), groupScores, cooperative.total());
                // Everyone shares the group's fate, so each Player's history can show won or lost.
                for (int i = 0; i < playerIds.size(); i++) {
                    PlayerEntry entered = entry.players().get(i);
                    results.add(new Play.Result(
                            playerIds.get(i), entered.scores(), entered.winner(), null, null, cooperative.won()));
                }
            }
        }
        return new Scored(group, results);
    }

    /** A Game Module's answer in the form it is stored in. {@code group} is null for a competitive Play. */
    private record Scored(Play.GroupResult group, List<Play.Result> results) {}
}
