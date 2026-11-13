package io.github.oliverjuhasz.boardkeeper.gamemodules.generic;

import io.github.oliverjuhasz.boardkeeper.gameapi.GameModule;
import io.github.oliverjuhasz.boardkeeper.gameapi.InvalidPlayException;
import io.github.oliverjuhasz.boardkeeper.gameapi.Outcome;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayEntry;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayerEntry;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayerResult;
import io.github.oliverjuhasz.boardkeeper.gameapi.RecordingMode;
import io.github.oliverjuhasz.boardkeeper.gameapi.ScoreCategory;
import io.github.oliverjuhasz.boardkeeper.gameapi.ScorePad;
import io.github.oliverjuhasz.boardkeeper.gameapi.Variant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * The Generic Module: the built-in Game Module for any Game that has no module of its own. It
 * knows nothing about the Game, so it records only totals, or only the winner.
 *
 * <p>What a real module knows about its Game, the recorder tells this one for each Play, as
 * two on/off Variants: whether the Play was {@link #COOPERATIVE}, and whether the
 * {@link #LOWEST_SCORE_WINS}.
 *
 * <p>A competitive Play needs at least two Players. Solo Plays of competitive Games come with
 * the solo styles of milestone M3.
 */
public final class GenericGameModule implements GameModule {

    /** Stored with every Play recorded through this module. Never change it (ADR 0012). */
    public static final UUID ID = UUID.fromString("38b119d2-8f9f-4e20-8068-cba305c06cdd");

    public static final String KEY = "generic";

    /** The on/off Variant "everyone wins or loses together, against the game". */
    public static final String COOPERATIVE = "cooperative";

    /** The on/off Variant "the lowest score wins". It only matters for competitive Plays recorded in points. */
    public static final String LOWEST_SCORE_WINS = "lowestScoreWins";

    /** The only Score Category: the total itself, of a Player or of the group. */
    public static final String TOTAL = "total";

    private static final Set<String> VARIANTS = Set.of(COOPERATIVE, LOWEST_SCORE_WINS);
    private static final Set<String> ON_OR_OFF = Set.of("true", "false");

    @Override
    public UUID id() {
        return ID;
    }

    @Override
    public String key() {
        return KEY;
    }

    /**
     * The pad names this module's two Variants and its one Score Category. It cannot say
     * that the "cooperative" Variant turns a Play into a Cooperative one, with a result for the
     * group. No other module needs that, so the frontend knows it about this module.
     */
    @Override
    public ScorePad scorePad() {
        return new ScorePad(
                1,
                null,
                List.of(new Variant.OnOff(COOPERATIVE, false), new Variant.OnOff(LOWEST_SCORE_WINS, false)),
                List.of(ScoreCategory.of(TOTAL).mayBeNegative()),
                List.of(),
                true);
    }

    @Override
    public Outcome score(PlayEntry entry) {
        entry.setup().forEach((variant, value) -> {
            if (!VARIANTS.contains(variant) || !ON_OR_OFF.contains(value)) {
                throw new InvalidPlayException("notRecorded");
            }
        });
        if (entry.players().isEmpty()) {
            throw new InvalidPlayException("tooFewPlayers");
        }
        boolean points = entry.recordingMode() == RecordingMode.POINTS;
        if (isOn(entry, COOPERATIVE)) {
            return cooperative(entry, points);
        }
        if (entry.group() != null) {
            throw new InvalidPlayException("notRecorded");
        }
        if (entry.players().size() < 2) {
            throw new InvalidPlayException("tooFewPlayers");
        }
        return points ? byPoints(entry) : new Outcome.Competitive(byMarkedWinners(entry), List.of());
    }

    private static boolean isOn(PlayEntry entry, String variant) {
        return "true".equals(entry.setup().get(variant));
    }

    /** Cooperative: the group won or lost, with a score if the Play was recorded in points and one was entered. */
    private static Outcome cooperative(PlayEntry entry, boolean points) {
        if (entry.group() == null) {
            throw new InvalidPlayException("groupResultMissing");
        }
        // Nothing is recorded for the Players one by one.
        for (PlayerEntry player : entry.players()) {
            if (!player.scores().isEmpty() || player.winner()) {
                throw new InvalidPlayException("notRecorded");
            }
        }
        Map<String, Integer> scores = entry.group().scores();
        if (!scores.isEmpty() && (!points || !scores.keySet().equals(Set.of(TOTAL)))) {
            throw new InvalidPlayException("notRecorded");
        }
        return new Outcome.Cooperative(entry.group().won(), scores.get(TOTAL));
    }

    /** Winner only: whoever the recorder marked has won. The other Players are not put in order. */
    private static List<PlayerResult> byMarkedWinners(PlayEntry entry) {
        if (entry.players().stream().anyMatch(player -> !player.scores().isEmpty())) {
            throw new InvalidPlayException("notRecorded");
        }
        if (entry.players().stream().noneMatch(PlayerEntry::winner)) {
            throw new InvalidPlayException("winnerMissing");
        }
        return entry.players().stream()
                .map(player -> new PlayerResult(null, null, player.winner()))
                .toList();
    }

    /**
     * Points: the best total is first, and equal totals share a Placing. The Placing after a
     * shared one skips ahead, so two Players in first place are followed by the third.
     *
     * <p>Only a tie for first place can be decided by the recorder: of the Players with the
     * best total, the ones marked as winner come first, and the rest share the Placing after
     * them. Marking none of them, or all of them, leaves first place shared.
     */
    private static Outcome byPoints(PlayEntry entry) {
        List<PlayerEntry> players = entry.players();
        for (PlayerEntry player : players) {
            if (player.scores().isEmpty()) {
                throw new InvalidPlayException("scoreMissing");
            }
            if (!player.scores().keySet().equals(Set.of(TOTAL))) {
                throw new InvalidPlayException("notRecorded");
            }
        }
        // Sorts a better total before a worse one.
        Comparator<Integer> bestFirst =
                isOn(entry, LOWEST_SCORE_WINS) ? Comparator.naturalOrder() : Comparator.reverseOrder();
        int best = players.stream().map(GenericGameModule::total).min(bestFirst).orElseThrow();

        if (players.stream().anyMatch(player -> player.winner() && total(player) != best)) {
            throw new InvalidPlayException("winnerNotInFirstPlace");
        }
        int markedWinners = (int) players.stream().filter(PlayerEntry::winner).count();

        List<PlayerResult> results = players.stream()
                .map(player -> {
                    int total = total(player);
                    int placing;
                    if (total != best) {
                        // One more than the number of Players who did better.
                        placing = 1 + (int) players.stream()
                                .filter(other -> bestFirst.compare(total(other), total) < 0)
                                .count();
                    } else if (markedWinners == 0 || player.winner()) {
                        placing = 1;
                    } else {
                        // Tied on points, but the recorder gave first place to someone else.
                        placing = 1 + markedWinners;
                    }
                    return new PlayerResult(total, placing, placing == 1);
                })
                .toList();

        // With nothing but totals, any tie for first place is the recorder's to decide.
        List<Integer> tied = IntStream.range(0, players.size())
                .filter(player -> total(players.get(player)) == best)
                .boxed()
                .toList();
        return new Outcome.Competitive(results, tied.size() > 1 ? tied : List.of());
    }

    private static int total(PlayerEntry player) {
        return player.scores().get(TOTAL);
    }
}
