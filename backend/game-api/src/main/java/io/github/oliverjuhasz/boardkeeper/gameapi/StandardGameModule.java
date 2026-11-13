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
 */
public abstract class StandardGameModule implements GameModule {

    @Override
    public final Outcome score(PlayEntry entry) {
        ScorePad pad = scorePad();
        checkSetup(pad, entry.setup());

        List<PlayerEntry> players = entry.players();
        if (players.size() < pad.minPlayers()) {
            throw new InvalidPlayException(Standard.TOO_FEW_PLAYERS);
        }
        if (pad.maxPlayers() != null && players.size() > pad.maxPlayers()) {
            throw new InvalidPlayException(Standard.TOO_MANY_PLAYERS);
        }
        if (entry.group() != null) {
            throw new InvalidPlayException(Standard.NOT_RECORDED);
        }
        return entry.recordingMode() == RecordingMode.WINNER_ONLY
                ? byMarkedWinners(players)
                : byPoints(pad, entry.setup(), players);
    }

    /** Every Variant in the Setup has to be on the pad, with a value the Variant allows. */
    private static void checkSetup(ScorePad pad, Map<String, String> setup) {
        setup.forEach((key, value) -> {
            boolean known = pad.variants().stream()
                    .anyMatch(variant -> variant.key().equals(key) && variant.allows(value));
            if (!known) {
                throw new InvalidPlayException(Standard.NOT_RECORDED);
            }
        });
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
        List<ScoreCategory> categories = pad.categoriesFor(setup);
        Set<String> onThePad = categories.stream().map(ScoreCategory::key).collect(Collectors.toSet());
        for (PlayerEntry player : players) {
            if (!onThePad.containsAll(player.scores().keySet())) {
                throw new InvalidPlayException(Standard.NOT_RECORDED);
            }
            for (ScoreCategory category : categories) {
                Integer value = player.scores().get(category.key());
                if (value == null) {
                    throw new InvalidPlayException(Standard.SCORE_MISSING);
                }
                if (!category.allows(value)) {
                    throw new InvalidPlayException(Standard.SCORE_NOT_ALLOWED);
                }
            }
        }

        List<Integer> totals = players.stream()
                .map(player -> categories.stream()
                        .filter(ScoreCategory::countsTowardTotal)
                        .mapToInt(category -> player.scores().get(category.key()))
                        .sum())
                .toList();

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
