package io.github.oliverjuhasz.boardkeeper.exports.internal;

import io.github.oliverjuhasz.boardkeeper.about.RunningVersion;
import io.github.oliverjuhasz.boardkeeper.exports.internal.Export.ExportedGame;
import io.github.oliverjuhasz.boardkeeper.exports.internal.Export.ExportedGameModule;
import io.github.oliverjuhasz.boardkeeper.exports.internal.Export.ExportedGroupResult;
import io.github.oliverjuhasz.boardkeeper.exports.internal.Export.ExportedPlay;
import io.github.oliverjuhasz.boardkeeper.exports.internal.Export.ExportedPlayer;
import io.github.oliverjuhasz.boardkeeper.exports.internal.Export.ExportedRecordingDefaults;
import io.github.oliverjuhasz.boardkeeper.exports.internal.Export.ExportedResult;
import io.github.oliverjuhasz.boardkeeper.gameapi.GameModule;
import io.github.oliverjuhasz.boardkeeper.games.Game;
import io.github.oliverjuhasz.boardkeeper.games.GameModules;
import io.github.oliverjuhasz.boardkeeper.games.Games;
import io.github.oliverjuhasz.boardkeeper.players.Players;
import io.github.oliverjuhasz.boardkeeper.plays.Play;
import io.github.oliverjuhasz.boardkeeper.plays.Plays;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Makes Exports (ADR 0019). */
@Service
class Exports {

    private final Games games;
    private final GameModules gameModules;
    private final Players players;
    private final Plays plays;
    private final RunningVersion version;

    Exports(Games games, GameModules gameModules, Players players, Plays plays, RunningVersion version) {
        this.games = games;
        this.gameModules = gameModules;
        this.players = players;
        this.plays = plays;
        this.version = version;
    }

    /**
     * Everything an Export holds, as it is at one moment.
     *
     * <p>The Games, the Players and the Plays are read one after the other. In PostgreSQL's usual
     * isolation, each read sees what was committed when it began, so a Play recorded in between
     * could name a Player the file doesn't have. In {@code REPEATABLE_READ}, every read of the
     * transaction sees the database as it was at its first one.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Export export() {
        UUID generic = gameModules.generic().id();
        List<Game> catalogue = games.all();
        List<Play> everyPlay = plays.all();

        Set<UUID> moduleIds = Stream.concat(
                        catalogue.stream().map(Game::gameModuleId), everyPlay.stream().map(Play::gameModuleId))
                .collect(Collectors.toSet());
        List<ExportedGameModule> modules = moduleIds.stream()
                .map(gameModules::byId)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(GameModule::key))
                .map(module -> new ExportedGameModule(module.id(), module.key()))
                .toList();

        return new Export(
                Export.FORMAT,
                Instant.now(),
                new Export.WrittenBy(version.release(), version.commit()),
                modules,
                catalogue.stream().map(game -> exported(game, generic)).toList(),
                players.all().stream()
                        .map(player -> new ExportedPlayer(player.id(), player.name()))
                        .toList(),
                everyPlay.stream().map(Exports::exported).toList());
    }

    private static ExportedGame exported(Game game, UUID generic) {
        Game.RecordingDefaults defaults = game.defaults();
        return new ExportedGame(
                game.id(),
                game.name(),
                game.gameModuleId(),
                game.gameModuleId().equals(generic)
                        ? new ExportedRecordingDefaults(
                                defaults.cooperative(), defaults.recordingMode(), defaults.lowestScoreWins())
                        : null);
    }

    private static ExportedPlay exported(Play play) {
        return new ExportedPlay(
                play.id(),
                play.gameId(),
                play.gameModuleId(),
                play.playedOn(),
                play.location(),
                play.notes(),
                play.recordingMode(),
                play.setup(),
                play.victoryType(),
                play.cooperative(),
                play.group() == null
                        ? null
                        : new ExportedGroupResult(play.group().won(), play.group().scores(), play.group().total()),
                play.results().stream()
                        .map(result -> new ExportedResult(
                                result.playerId(),
                                result.scores(),
                                result.setup(),
                                result.markedWinner(),
                                result.total(),
                                result.placing(),
                                result.won()))
                        .toList(),
                play.recordedBy(),
                play.recordedAt());
    }
}
