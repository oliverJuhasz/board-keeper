package io.github.oliverjuhasz.boardkeeper.plays;

import io.github.oliverjuhasz.boardkeeper.access.CurrentAccount;
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

/** Recording Plays and looking them up. */
@Service
public class Plays {

    public static final int MAX_LOCATION_LENGTH = 100;
    public static final int MAX_NOTES_LENGTH = 2000;

    /** Far more than any Game seats. It only stops absurd requests. */
    public static final int MAX_PLAYERS = 100;

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
     * Records a Play: has the Game's Game Module score what was entered, and stores the entry
     * together with the Results (ADR 0010).
     *
     * <p>What the module refuses is reported as the Problem {@code plays.invalidEntry}, with the
     * module's own reason.
     */
    @Transactional
    public Play record(NewPlay newPlay, CurrentAccount recorder) {
        Game game = games.find(newPlay.gameId()).orElseThrow(() -> Problem.notFound("games.notFound"));
        // A Game can outlive its module: an installation may be running a build without it.
        GameModule module =
                gameModules.byId(game.gameModuleId()).orElseThrow(() -> Problem.conflict("plays.gameModuleMissing"));

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

        UUID id = repository.insert(game, newPlay, group, results, recorder.playerId());
        return repository.find(id).orElseThrow();
    }
}
