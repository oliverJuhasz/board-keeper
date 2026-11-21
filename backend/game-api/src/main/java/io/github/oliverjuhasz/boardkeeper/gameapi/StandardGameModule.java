package io.github.oliverjuhasz.boardkeeper.gameapi;

import io.github.oliverjuhasz.boardkeeper.gameapi.InvalidPlayException.Standard;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A Game Module for a competitive Game that is scored the usual way: every Player adds up the
 * rows of a score pad, and the highest total wins. Such a module only describes its pad, in
 * {@link #scorePad()}; the scoring here follows from the description.
 *
 * <p>Equal totals are put in order by the pad's Tie-breakers. Players who are still level
 * share a Placing. For first place only, a pad can say that the Game decides what is left by
 * something that is not entered: then the recorder is asked, and the Players they mark as
 * winner come first.
 *
 * <p>When only the winner is recorded, nothing is entered on the pad: the Players the recorder
 * marked have won, and nobody gets a Placing.
 *
 * <p>A Play with one Player is a Solo Play, which the pad's {@link Solo} describes. Played for
 * score only, it has a total but no winner. Played against an automated opponent, the Player
 * wins by beating the opponent's score. A Solo Play has no Placing.
 *
 * <p>A Game that can be won in more than one way has Victory Types, and every Play that isn't a
 * Solo Play says which one it was won by. On points, it is scored as above. Won outright, nobody
 * counted points: it is recorded like a Play with only the winner, and has one winner.
 */
public abstract class StandardGameModule implements GameModule {

    @Override
    public final Outcome score(PlayEntry entry) {
        ScorePad pad = scorePad();
        List<PlayerEntry> players = entry.players();
        boolean soloPlay = players.size() == 1;
        if (soloPlay && pad.solo() == null) {
            throw new InvalidPlayException(Standard.TOO_FEW_PLAYERS);
        }
        Checks.playerCount(pad, entry.setup(), players.size());
        Checks.setup(pad, entry.setup(), soloPlay);
        Checks.playerSetup(pad, entry.setup(), players);
        if (entry.group() != null) {
            throw new InvalidPlayException(Standard.NOT_RECORDED);
        }
        VictoryType victoryType = victoryTypeOf(pad, entry, soloPlay);
        boolean points = entry.recordingMode() == RecordingMode.POINTS;
        if (soloPlay) {
            return solo(pad, entry.setup(), players.getFirst(), points);
        }
        if (victoryType != null && !victoryType.onPoints()) {
            return outright(points, players);
        }
        return points ? byPoints(pad, entry.setup(), players) : byMarkedWinners(players);
    }

    /**
     * How the Play was won: one of the ways the Game can be won with its Setup. Null for a Game
     * that is won in one way only, and for a Solo Play.
     */
    private static VictoryType victoryTypeOf(ScorePad pad, PlayEntry entry, boolean soloPlay) {
        String key = entry.victoryType();
        if (pad.victoryTypes().isEmpty() || soloPlay) {
            if (key != null) {
                throw new InvalidPlayException(Standard.NOT_RECORDED);
            }
            return null;
        }
        if (key == null) {
            throw new InvalidPlayException(Standard.VICTORY_TYPE_MISSING);
        }
        return pad.victoryTypesFor(entry.setup()).stream()
                .filter(victoryType -> victoryType.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new InvalidPlayException(Standard.NOT_RECORDED));
    }

    /**
     * Checks that every Player has a value the row allows in every row on the pad, and nothing
     * in a row that isn't on it.
     */
    private static void checkPad(List<ScoreCategory> categories, List<PlayerEntry> players) {
        for (PlayerEntry player : players) {
            Checks.rows(categories, player.scores());
        }
    }

    /** The sum of the rows that count toward the total. */
    private static int totalOf(List<ScoreCategory> categories, PlayerEntry player) {
        return categories.stream()
                .filter(ScoreCategory::countsTowardTotal)
                .mapToInt(category -> player.scores().get(category.key()))
                .sum();
    }

    private static Outcome solo(ScorePad pad, Map<String, String> setup, PlayerEntry player, boolean points) {
        // A switch over a sealed interface: the compiler checks that both kinds are handled.
        PlayerResult result = switch (pad.solo()) {
            case Solo.ForScoreOnly forScoreOnly -> {
                // There is nothing to record but the score, and nobody to win against.
                if (!points) {
                    throw new InvalidPlayException(Standard.SCORES_NEEDED);
                }
                if (player.winner()) {
                    throw new InvalidPlayException(Standard.NOT_RECORDED);
                }
                List<ScoreCategory> categories = pad.categoriesFor(setup, true);
                checkPad(categories, List.of(player));
                yield new PlayerResult(totalOf(categories, player), null, null);
            }
            case Solo.AgainstOpponent against -> {
                if (!points) {
                    // Only whether the Player beat the opponent: marked as winner, or not.
                    if (!player.scores().isEmpty()) {
                        throw new InvalidPlayException(Standard.NOT_RECORDED);
                    }
                    yield new PlayerResult(null, null, player.winner());
                }
                // With the scores, they say who won, so there is nothing for the recorder to mark.
                if (player.winner()) {
                    throw new InvalidPlayException(Standard.NOT_RECORDED);
                }
                List<ScoreCategory> categories = pad.categoriesFor(setup, true);
                checkPad(categories, List.of(player));
                int total = totalOf(categories, player);
                int opponent = player.scores().get(against.opponentCategory());
                yield new PlayerResult(total, null, total > opponent || (total == opponent && against.tieWins()));
            }
        };
        return new Outcome.Competitive(List.of(result), List.of());
    }

    /** A Play won outright: nobody counted points, and the recorder marks its one winner. */
    private static Outcome outright(boolean points, List<PlayerEntry> players) {
        if (points) {
            throw new InvalidPlayException(Standard.NOT_RECORDED);
        }
        Outcome outcome = byMarkedWinners(players);
        if (players.stream().filter(PlayerEntry::winner).count() > 1) {
            throw new InvalidPlayException(Standard.ONE_WINNER_ONLY);
        }
        return outcome;
    }

    private static Outcome byMarkedWinners(List<PlayerEntry> players) {
        if (players.stream().anyMatch(player -> !player.scores().isEmpty())) {
            throw new InvalidPlayException(Standard.NOT_RECORDED);
        }
        if (players.stream().noneMatch(PlayerEntry::winner)) {
            throw new InvalidPlayException(Standard.WINNER_MISSING);
        }
        List<PlayerResult> results = players.stream()
                .map(player -> new PlayerResult(null, null, player.winner()))
                .toList();
        return new Outcome.Competitive(results, List.of());
    }

    private static Outcome byPoints(ScorePad pad, Map<String, String> setup, List<PlayerEntry> players) {
        List<ScoreCategory> categories = pad.categoriesFor(setup, false);
        checkPad(categories, players);
        Set<String> onThePad = categories.stream().map(ScoreCategory::key).collect(Collectors.toSet());
        List<Integer> totals = players.stream().map(player -> totalOf(categories, player)).toList();

        // Sorts the Player who did better first: by total, then by each Tie-breaker in turn.
        // Players it calls equal are level on everything that was entered.
        Comparator<Integer> bestFirst = Comparator.comparing((Integer player) -> totals.get(player), Comparator.reverseOrder());
        for (TieBreaker tieBreaker : pad.tieBreakers()) {
            // A Tie-breaker whose row is not on the pad for this Setup decides nothing.
            if (onThePad.contains(tieBreaker.category())) {
                Comparator<Integer> byValue =
                        Comparator.comparing(player -> players.get(player).scores().get(tieBreaker.category()));
                bestFirst = bestFirst.thenComparing(tieBreaker.fewestWins() ? byValue : byValue.reversed());
            }
        }
        Comparator<Integer> order = bestFirst;
        List<Integer> everyone = IntStream.range(0, players.size()).boxed().toList();
        Integer best = everyone.stream().min(order).orElseThrow();
        List<Integer> first = everyone.stream().filter(player -> order.compare(player, best) == 0).toList();

        // Marking a winner only means something for a tie the recorder may decide.
        List<Integer> marked = everyone.stream().filter(player -> players.get(player).winner()).toList();
        boolean recorderMayDecide = pad.recorderDecidesTies() && first.size() > 1;
        if (!marked.isEmpty() && (!first.containsAll(marked) || (!recorderMayDecide && first.size() > 1))) {
            throw new InvalidPlayException(Standard.WINNER_NOT_TIED);
        }
        boolean decided = recorderMayDecide && !marked.isEmpty() && marked.size() < first.size();

        List<PlayerResult> results = new ArrayList<>();
        for (int player : everyone) {
            int placing;
            if (!first.contains(player)) {
                // One more than the number of Players who did better.
                placing = 1 + (int) everyone.stream().filter(other -> order.compare(other, player) < 0).count();
            } else if (!decided || marked.contains(player)) {
                placing = 1;
            } else {
                // Level with the winner on what was entered, but the recorder gave first place away.
                placing = 1 + marked.size();
            }
            results.add(new PlayerResult(totals.get(player), placing, placing == 1));
        }
        return new Outcome.Competitive(results, recorderMayDecide ? first : List.of());
    }
}
