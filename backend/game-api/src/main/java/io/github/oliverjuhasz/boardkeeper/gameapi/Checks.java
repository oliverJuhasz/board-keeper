package io.github.oliverjuhasz.boardkeeper.gameapi;

import io.github.oliverjuhasz.boardkeeper.gameapi.InvalidPlayException.Standard;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** The checks that StandardGameModule and CooperativeGameModule make alike. */
final class Checks {

    private Checks() {}

    /**
     * Every Variant and Expansion in the Setup has to be on the pad, with a value it allows. A
     * Variant for Solo Plays only belongs in the Setup of a Solo Play only, and a Variant that
     * depends on the Setup only where it holds.
     */
    static void setup(ScorePad pad, Map<String, String> setup, boolean soloPlay) {
        List<Variant> chosen = pad.variantsFor(setup, soloPlay);
        setup.forEach((key, value) -> {
            boolean variant = chosen.stream()
                    .anyMatch(candidate -> candidate.key().equals(key) && candidate.allows(value));
            boolean expansion = pad.expansions().stream().anyMatch(candidate -> candidate.key().equals(key))
                    && Expansion.allows(value);
            if (!variant && !expansion) {
                throw new InvalidPlayException(Standard.NOT_RECORDED);
            }
        });
    }

    /**
     * Every choice in a Player Setup has to be one of the pad's, with one of its options. A
     * choice made once per Play cannot give two Players the same option.
     */
    static void playerSetup(ScorePad pad, List<PlayerEntry> players) {
        for (PlayerEntry player : players) {
            player.setup().forEach((key, option) -> {
                boolean known = pad.playerSetup().stream()
                        .anyMatch(choice -> choice.key().equals(key) && choice.options().contains(option));
                if (!known) {
                    throw new InvalidPlayException(Standard.NOT_RECORDED);
                }
            });
        }
        for (PlayerSetupChoice choice : pad.playerSetup()) {
            if (choice.oncePerPlay()) {
                List<String> chosen = players.stream()
                        .map(player -> player.setup().get(choice.key()))
                        .filter(Objects::nonNull)
                        .toList();
                if (Set.copyOf(chosen).size() != chosen.size()) {
                    throw new InvalidPlayException(Standard.CHOSEN_TWICE);
                }
            }
        }
    }

    /**
     * Checks that every row on the pad has a value it allows, and that nothing is in a row that
     * isn't on it.
     */
    static void rows(List<ScoreCategory> categories, Map<String, Integer> scores) {
        Set<String> onThePad = categories.stream().map(ScoreCategory::key).collect(Collectors.toSet());
        if (!onThePad.containsAll(scores.keySet())) {
            throw new InvalidPlayException(Standard.NOT_RECORDED);
        }
        for (ScoreCategory category : categories) {
            Integer value = scores.get(category.key());
            if (value == null) {
                throw new InvalidPlayException(Standard.SCORE_MISSING);
            }
            if (!category.allows(value)) {
                throw new InvalidPlayException(Standard.SCORE_NOT_ALLOWED);
            }
        }
    }
}
