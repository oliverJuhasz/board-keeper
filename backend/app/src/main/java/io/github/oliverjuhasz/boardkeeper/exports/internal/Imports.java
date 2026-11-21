package io.github.oliverjuhasz.boardkeeper.exports.internal;

import io.github.oliverjuhasz.boardkeeper.exports.internal.Export.ExportedGame;
import io.github.oliverjuhasz.boardkeeper.exports.internal.Export.ExportedPlay;
import io.github.oliverjuhasz.boardkeeper.exports.internal.Export.ExportedPlayer;
import io.github.oliverjuhasz.boardkeeper.exports.internal.Export.ExportedRecordingDefaults;
import io.github.oliverjuhasz.boardkeeper.exports.internal.Export.ExportedResult;
import io.github.oliverjuhasz.boardkeeper.exports.internal.Import.GameMatch;
import io.github.oliverjuhasz.boardkeeper.exports.internal.Import.PlayerChoice;
import io.github.oliverjuhasz.boardkeeper.exports.internal.Import.RefusedPlay;
import io.github.oliverjuhasz.boardkeeper.exports.internal.Import.ReviewedGame;
import io.github.oliverjuhasz.boardkeeper.exports.internal.Import.ReviewedPlayer;
import io.github.oliverjuhasz.boardkeeper.gameapi.RecordingMode;
import io.github.oliverjuhasz.boardkeeper.games.Game;
import io.github.oliverjuhasz.boardkeeper.games.Game.RecordingDefaults;
import io.github.oliverjuhasz.boardkeeper.games.GameModules;
import io.github.oliverjuhasz.boardkeeper.games.Games;
import io.github.oliverjuhasz.boardkeeper.players.Player;
import io.github.oliverjuhasz.boardkeeper.players.Players;
import io.github.oliverjuhasz.boardkeeper.plays.Play;
import io.github.oliverjuhasz.boardkeeper.plays.Plays;
import io.github.oliverjuhasz.boardkeeper.problems.Problem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Brings the Plays of an Export into this Installation (ADR 0021), with the Players and Games they
 * need. A Play is copied as it was, and one that is here already is left as it is.
 *
 * <p>The review and the Import itself work the file out in the same way, so the Import does what the
 * review said, unless something changed here in between. Nothing is kept between the two: the file
 * comes with each request.
 */
@Service
class Imports {

    static final String GAME_MODULE_MISSING = "imports.gameModuleMissing";

    private static final RecordingDefaults FALLBACK_DEFAULTS =
            new RecordingDefaults(false, RecordingMode.POINTS, false);

    private final Games games;
    private final GameModules gameModules;
    private final Players players;
    private final Plays plays;

    Imports(Games games, GameModules gameModules, Players players, Plays plays) {
        this.games = games;
        this.gameModules = gameModules;
        this.players = players;
        this.plays = plays;
    }

    /** What importing the file would do. Nothing is written. */
    @Transactional(readOnly = true)
    public Import.Review review(Export file) {
        Plan plan = plan(file);
        Map<UUID, UUID> suggested = suggestPlayers(file);
        List<ReviewedPlayer> reviewed = file.players().stream()
                .map(player -> new ReviewedPlayer(player.id(), player.name(), suggested.get(player.id())))
                .toList();
        return new Import.Review(
                List.copyOf(plan.games().values()),
                reviewed,
                file.plays().size(),
                plan.alreadyHere(),
                plan.refused());
    }

    /**
     * Imports the file: adds the new Players and Games, and copies every Play that isn't here yet and
     * that this Installation can take. All of it happens in one transaction, so if anything fails,
     * nothing has changed.
     *
     * @param choices who each of the file's Players is here: an existing Player, or a new one
     */
    @Transactional
    public Import.Result importFile(Export file, List<PlayerChoice> choices) {
        Plan plan = plan(file);
        Map<UUID, UUID> playerIds = new HashMap<>();
        int newPlayers = choosePlayers(file.players(), choices, playerIds);

        Map<UUID, ExportedGame> fileGames =
                file.games().stream().collect(Collectors.toMap(ExportedGame::id, Function.identity()));
        Map<UUID, UUID> gameIds = new HashMap<>();
        int newGames = 0;
        for (ReviewedGame game : plan.games().values()) {
            switch (game.match()) {
                case CATALOGUE -> gameIds.put(game.fileId(), game.gameId());
                case NEW -> {
                    Game added = games.create(game.name(), defaultsOf(fileGames.get(game.fileId())));
                    gameIds.put(game.fileId(), added.id());
                    newGames++;
                }
                case MODULE_MISSING -> {
                    // Its Plays are refused, and the Game stays out of the catalogue.
                }
            }
        }

        int added = 0;
        int alreadyHere = plan.alreadyHere();
        for (ExportedPlay play : plan.toCopy()) {
            if (plays.copy(playOf(play, gameIds::get, playerIds::get))) {
                added++;
            } else {
                // Recorded here between the review of this transaction and now.
                alreadyHere++;
            }
        }
        return new Import.Result(added, alreadyHere, plan.refused(), newPlayers, newGames);
    }

    /**
     * Who each of the file's Players most likely is here, by their id in the file. A Play that is
     * here already tells for sure: it has the same Players in the same order, as when the same file,
     * or a later one from the same Installation, is imported again. Otherwise it is the Player with
     * the same name. Each Player here is suggested once at most.
     */
    private Map<UUID, UUID> suggestPlayers(Export file) {
        Map<UUID, UUID> suggested = new HashMap<>();
        Set<UUID> taken = new HashSet<>();
        Set<UUID> existing = plays.existing(file.plays().stream().map(ExportedPlay::id).toList());
        for (ExportedPlay filePlay : file.plays()) {
            if (suggested.size() == file.players().size()) {
                break;
            }
            if (!existing.contains(filePlay.id())) {
                continue;
            }
            plays.find(filePlay.id())
                    .filter(here -> here.results().size() == filePlay.results().size())
                    .ifPresent(here -> {
                        for (int i = 0; i < here.results().size(); i++) {
                            UUID hereId = here.results().get(i).playerId();
                            UUID fileId = filePlay.results().get(i).playerId();
                            if (!suggested.containsKey(fileId) && taken.add(hereId)) {
                                suggested.put(fileId, hereId);
                            }
                        }
                    });
        }
        for (ExportedPlayer player : file.players()) {
            if (!suggested.containsKey(player.id())) {
                players.findByName(player.name())
                        .map(Player::id)
                        .filter(taken::add)
                        .ifPresent(hereId -> suggested.put(player.id(), hereId));
            }
        }
        return suggested;
    }

    /**
     * What the file's Games and Plays come to here.
     *
     * @param games where the Plays of each of the file's Games go, by the Game's id in the file
     * @param toCopy the Plays to copy
     * @param alreadyHere how many Plays are here already
     * @param refused the Plays this Installation can't take
     */
    private record Plan(
            Map<UUID, ReviewedGame> games, List<ExportedPlay> toCopy, int alreadyHere, List<RefusedPlay> refused) {}

    private Plan plan(Export file) {
        checkReadable(file);
        Map<UUID, String> moduleKeys = new HashMap<>();
        if (file.gameModules() != null) {
            file.gameModules().stream()
                    .filter(Objects::nonNull)
                    .filter(module -> module.id() != null && module.key() != null)
                    .forEach(module -> moduleKeys.put(module.id(), module.key()));
        }

        Map<UUID, ReviewedGame> reviewed = new LinkedHashMap<>();
        for (ExportedGame game : file.games()) {
            reviewed.put(game.id(), match(game, moduleKeys));
        }

        Set<UUID> existing = plays.existing(file.plays().stream().map(ExportedPlay::id).toList());
        List<ExportedPlay> toCopy = new ArrayList<>();
        List<RefusedPlay> refused = new ArrayList<>();
        int alreadyHere = 0;
        for (ExportedPlay play : file.plays()) {
            ReviewedGame game = reviewed.get(play.gameId());
            if (existing.contains(play.id())) {
                alreadyHere++;
            } else if (game.match() == GameMatch.MODULE_MISSING) {
                refused.add(refusal(play, game, GAME_MODULE_MISSING, game.gameModuleKey()));
            } else if (gameModules.byId(play.gameModuleId()).isEmpty()) {
                refused.add(refusal(play, game, GAME_MODULE_MISSING, keyOf(play.gameModuleId(), moduleKeys)));
            } else {
                try {
                    // The file's own ids will do: the check only looks at the Play itself.
                    plays.checkCopy(playOf(play, Function.identity(), Function.identity()));
                    toCopy.add(play);
                } catch (Problem refusal) {
                    String reason = refusal.reason() != null ? refusal.reason() : refusal.code();
                    refused.add(refusal(play, game, reason, null));
                }
            }
        }
        return new Plan(reviewed, toCopy, alreadyHere, refused);
    }

    /** Where the Plays of one of the file's Games go here. */
    private ReviewedGame match(ExportedGame game, Map<UUID, String> moduleKeys) {
        if (game.gameModuleId().equals(gameModules.generic().id())) {
            // A Game added by hand goes by its name, also to a module's Game of that name.
            return games.findByName(game.name())
                    .map(found -> new ReviewedGame(game.id(), game.name(), GameMatch.CATALOGUE, found.id(), null))
                    .orElseGet(() -> new ReviewedGame(game.id(), game.name(), GameMatch.NEW, null, null));
        }
        if (gameModules.byId(game.gameModuleId()).isEmpty()) {
            return new ReviewedGame(
                    game.id(), game.name(), GameMatch.MODULE_MISSING, null, keyOf(game.gameModuleId(), moduleKeys));
        }
        Game own = games.findByModule(game.gameModuleId())
                .orElseThrow(() -> new IllegalStateException(
                        "The Game Module " + game.gameModuleId() + " has no Game in the catalogue"));
        return new ReviewedGame(game.id(), game.name(), GameMatch.CATALOGUE, own.id(), null);
    }

    private static String keyOf(UUID gameModuleId, Map<UUID, String> moduleKeys) {
        return moduleKeys.getOrDefault(gameModuleId, gameModuleId.toString());
    }

    private static RefusedPlay refusal(ExportedPlay play, ReviewedGame game, String reason, String gameModuleKey) {
        return new RefusedPlay(play.id(), play.playedOn(), game.name(), reason, gameModuleKey);
    }

    /**
     * Checks that the file is an Export this Board Keeper can read, and that it is whole: that
     * everything has what it can't be without, and that every Play names a Game and Players the
     * file has. Board Keeper always writes such a file; one that isn't was changed by hand.
     */
    private static void checkReadable(Export file) {
        if (file.format() == null || file.format() < 1 || file.games() == null || file.players() == null || file.plays() == null) {
            throw Problem.unprocessable("imports.notAnExport");
        }
        if (file.format() > Export.FORMAT) {
            throw Problem.unprocessable("imports.formatTooNew");
        }

        Set<UUID> gameIds = new HashSet<>();
        Set<String> gameNames = new HashSet<>();
        for (ExportedGame game : file.games()) {
            whole(game != null
                    && game.gameModuleId() != null
                    && game.id() != null
                    && gameIds.add(game.id())
                    && fits(game.name(), Games.MAX_NAME_LENGTH)
                    && gameNames.add(game.name().strip().toLowerCase(Locale.ROOT)));
        }
        Set<UUID> playerIds = new HashSet<>();
        Set<String> playerNames = new HashSet<>();
        for (ExportedPlayer player : file.players()) {
            whole(player != null
                    && player.id() != null
                    && playerIds.add(player.id())
                    && fits(player.name(), Players.MAX_NAME_LENGTH)
                    && playerNames.add(player.name().strip().toLowerCase(Locale.ROOT)));
        }
        Set<UUID> playIds = new HashSet<>();
        for (ExportedPlay play : file.plays()) {
            whole(play != null
                    && play.id() != null
                    && playIds.add(play.id())
                    && gameIds.contains(play.gameId())
                    && play.gameModuleId() != null
                    && play.playedOn() != null
                    && play.recordingMode() != null
                    && play.results() != null
                    && play.results().stream()
                            .allMatch(result -> result != null && playerIds.contains(result.playerId()))
                    && playerIds.contains(play.recordedBy())
                    && play.recordedAt() != null);
        }
    }

    private static void whole(boolean whole) {
        if (!whole) {
            throw Problem.unprocessable("imports.fileBroken");
        }
    }

    private static boolean fits(String name, int maxLength) {
        return name != null && !name.isBlank() && name.strip().length() <= maxLength;
    }

    /**
     * Works out, from the Admin's choices, which Player here each of the file's Players is, and adds
     * the new ones. Everything is checked before the first is added.
     *
     * @param playerIds filled with the Player here of each of the file's Players, by its id in the file
     * @return how many Players were added
     */
    private int choosePlayers(List<ExportedPlayer> filePlayers, List<PlayerChoice> choices, Map<UUID, UUID> playerIds) {
        Map<UUID, PlayerChoice> byFileId = new HashMap<>();
        for (PlayerChoice choice : choices) {
            if (byFileId.put(choice.fileId(), choice) != null) {
                throw Problem.unprocessable("imports.playersNotChosen");
            }
        }
        Set<UUID> fileIds = filePlayers.stream().map(ExportedPlayer::id).collect(Collectors.toSet());
        if (!byFileId.keySet().equals(fileIds)) {
            throw Problem.unprocessable("imports.playersNotChosen");
        }

        Set<UUID> chosen = new HashSet<>();
        Map<UUID, String> newNames = new LinkedHashMap<>();
        Set<String> takenNames = new HashSet<>();
        for (ExportedPlayer filePlayer : filePlayers) {
            PlayerChoice choice = byFileId.get(filePlayer.id());
            boolean named = choice.newName() != null && !choice.newName().isBlank();
            if ((choice.playerId() != null) == named) {
                throw Problem.unprocessable("request.invalid");
            }
            if (choice.playerId() != null) {
                if (!chosen.add(choice.playerId())) {
                    throw Problem.unprocessable("imports.playerChosenTwice");
                }
                playerIds.put(filePlayer.id(), choice.playerId());
            } else {
                String name = choice.newName().strip();
                if (!takenNames.add(name.toLowerCase(Locale.ROOT)) || players.findByName(name).isPresent()) {
                    throw Problem.conflict("players.nameTaken");
                }
                newNames.put(filePlayer.id(), name);
            }
        }
        if (players.find(chosen).size() != chosen.size()) {
            throw Problem.notFound("players.notFound");
        }
        newNames.forEach((fileId, name) -> playerIds.put(fileId, players.create(name).id()));
        return newNames.size();
    }

    private static RecordingDefaults defaultsOf(ExportedGame game) {
        ExportedRecordingDefaults defaults = game.recordingDefaults();
        if (defaults == null || defaults.recordingMode() == null) {
            return FALLBACK_DEFAULTS;
        }
        return new RecordingDefaults(defaults.cooperative(), defaults.recordingMode(), defaults.lowestScoreWins());
    }

    /**
     * One of the file's Plays as a Play here, with the ids of its Game and Players turned into those
     * of this Installation.
     */
    private static Play playOf(ExportedPlay play, Function<UUID, UUID> gameId, Function<UUID, UUID> playerId) {
        return new Play(
                play.id(),
                gameId.apply(play.gameId()),
                play.gameModuleId(),
                play.playedOn(),
                play.location(),
                play.notes(),
                play.recordingMode(),
                orEmpty(play.setup()),
                play.victoryType(),
                play.cooperative(),
                play.group() == null
                        ? null
                        : new Play.GroupResult(play.group().won(), orEmpty(play.group().scores()), play.group().total()),
                play.results().stream().map(result -> resultOf(result, playerId)).toList(),
                playerId.apply(play.recordedBy()),
                play.recordedAt());
    }

    private static Play.Result resultOf(ExportedResult result, Function<UUID, UUID> playerId) {
        return new Play.Result(
                playerId.apply(result.playerId()),
                orEmpty(result.scores()),
                orEmpty(result.setup()),
                result.markedWinner(),
                result.total(),
                result.placing(),
                result.won());
    }

    private static <T> Map<String, T> orEmpty(Map<String, T> map) {
        return map == null ? Map.of() : map;
    }
}
