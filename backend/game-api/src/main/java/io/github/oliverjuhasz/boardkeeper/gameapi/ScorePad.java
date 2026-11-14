package io.github.oliverjuhasz.boardkeeper.gameapi;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What is entered for a Play of a Game, described so that the core can check it and the
 * frontend can draw a form for it: the Game's score pad, with what the pad itself leaves
 * unsaid, such as how many can play and how a tie is decided.
 *
 * <p>The description holds only keys. The module's translation files hold what people read:
 *
 * <ul>
 *   <li>{@code <module>.expansion.<key>} for an Expansion;
 *   <li>{@code <module>.variant.<key>} for a Variant, and {@code <module>.variant.<key>.<option>}
 *       for each option of a choice;
 *   <li>{@code <module>.category.<key>} for a Score Category;
 *   <li>{@code <module>.victoryType.<key>} for a Victory Type;
 *   <li>{@code <module>.tie}, when {@code recorderDecidesTies} is set: how the Game decides a
 *       tie, for the recorder who is asked to say who won it.
 * </ul>
 *
 * @param minPlayers the fewest Players a Play can have
 * @param maxPlayers the most Players a Play can have; null when there is no limit
 * @param variants the Variants a recorder chooses from, in the order to offer them
 * @param categories the Score Categories, in the order of the score pad
 * @param tieBreakers how Players with equal totals are put in order, first one first. Empty when
 *     nothing that is entered can decide a tie.
 * @param recorderDecidesTies whether a tie for first place that is left after the
 *     {@code tieBreakers} is decided by something that is not entered. If so, the recorder is
 *     asked who won it. If not, such a tie is a shared win.
 * @param solo how a Solo Play is recorded; null for a Game that is not played alone, for which
 *     the constructors without it are shorter
 * @param expansions the Expansions a Play can be played with, in the order to offer them
 * @param victoryTypes the ways a Play can be won, in the order to offer them, the usual one
 *     first; empty for a Game that is won in one way only. A Solo Play has no Victory Type.
 */
public record ScorePad(
        int minPlayers,
        Integer maxPlayers,
        List<Variant> variants,
        List<ScoreCategory> categories,
        List<TieBreaker> tieBreakers,
        boolean recorderDecidesTies,
        Solo solo,
        List<Expansion> expansions,
        List<VictoryType> victoryTypes) {

    public ScorePad {
        variants = List.copyOf(variants);
        categories = List.copyOf(categories);
        tieBreakers = List.copyOf(tieBreakers);
        expansions = List.copyOf(expansions);
        victoryTypes = List.copyOf(victoryTypes);
        if (minPlayers < 1 || (maxPlayers != null && maxPlayers < minPlayers)) {
            throw new IllegalArgumentException("A score pad is for at least one Player, and for no more than its maximum");
        }
        if (solo != null && minPlayers != 1) {
            throw new IllegalArgumentException("A score pad with a way to record Solo Plays is for one Player or more");
        }
        boolean soloOnlyParts = variants.stream().anyMatch(Variant::soloOnly)
                || categories.stream().anyMatch(ScoreCategory::soloOnly);
        if (soloOnlyParts && solo == null) {
            throw new IllegalArgumentException("Only a score pad with a way to record Solo Plays has parts for Solo Plays only");
        }
        if (solo instanceof Solo.AgainstOpponent against && categories.stream().noneMatch(category ->
                category.key().equals(against.opponentCategory()) && category.soloOnly() && !category.countsTowardTotal())) {
            throw new IllegalArgumentException("The opponent's score '" + against.opponentCategory()
                    + "' has to be a Score Category for Solo Plays only that does not count toward the total");
        }

        // Variants and Expansions share the Setup, so their keys have to differ from each other.
        Set<String> setupKeys = new HashSet<>();
        for (Expansion expansion : expansions) {
            if (!setupKeys.add(expansion.key())) {
                throw new IllegalArgumentException("Two Expansions have the key '" + expansion.key() + "'");
            }
        }
        for (Variant variant : variants) {
            if (!setupKeys.add(variant.key())) {
                throw new IllegalArgumentException("Two Variants or Expansions have the key '" + variant.key() + "'");
            }
        }
        Set<String> categoryKeys = new HashSet<>();
        for (ScoreCategory category : categories) {
            if (!categoryKeys.add(category.key())) {
                throw new IllegalArgumentException("Two Score Categories have the key '" + category.key() + "'");
            }
            if (!possible(category.onlyWhen(), variants, expansions)) {
                throw new IllegalArgumentException("The Score Category '" + category.key()
                        + "' depends on a Variant, an Expansion or a value that is not on the pad");
            }
        }
        for (TieBreaker tieBreaker : tieBreakers) {
            if (!categoryKeys.contains(tieBreaker.category())) {
                throw new IllegalArgumentException(
                        "The Tie-breaker '" + tieBreaker.category() + "' is not a Score Category of the pad");
            }
        }
        Set<String> victoryTypeKeys = new HashSet<>();
        for (VictoryType victoryType : victoryTypes) {
            if (!victoryTypeKeys.add(victoryType.key())) {
                throw new IllegalArgumentException("Two Victory Types have the key '" + victoryType.key() + "'");
            }
            if (!possible(victoryType.onlyWhen(), variants, expansions)) {
                throw new IllegalArgumentException("The Victory Type '" + victoryType.key()
                        + "' depends on a Variant, an Expansion or a value that is not on the pad");
            }
        }
        if (victoryTypes.size() == 1) {
            throw new IllegalArgumentException("A Game won in one way only has no Victory Types");
        }
        if (!victoryTypes.isEmpty() && victoryTypes.getFirst().onlyWhen() != null) {
            throw new IllegalArgumentException("The first Victory Type is the usual one, so it has to be always possible");
        }
    }

    /** A score pad for a Game that is not played alone. */
    public ScorePad(
            int minPlayers,
            Integer maxPlayers,
            List<Variant> variants,
            List<ScoreCategory> categories,
            List<TieBreaker> tieBreakers,
            boolean recorderDecidesTies) {
        this(minPlayers, maxPlayers, variants, categories, tieBreakers, recorderDecidesTies, null);
    }

    /** A score pad without Expansions, for a Game that is won in one way only. */
    public ScorePad(
            int minPlayers,
            Integer maxPlayers,
            List<Variant> variants,
            List<ScoreCategory> categories,
            List<TieBreaker> tieBreakers,
            boolean recorderDecidesTies,
            Solo solo) {
        this(minPlayers, maxPlayers, variants, categories, tieBreakers, recorderDecidesTies, solo, List.of(), List.of());
    }

    /** Whether a condition names a Variant or an Expansion of the pad, with a value it can have. */
    private static boolean possible(OnlyWhen onlyWhen, List<Variant> variants, List<Expansion> expansions) {
        if (onlyWhen == null) {
            return true;
        }
        return variants.stream().anyMatch(variant ->
                        variant.key().equals(onlyWhen.key()) && variant.allows(onlyWhen.value()))
                || expansions.stream().anyMatch(expansion ->
                        expansion.key().equals(onlyWhen.key()) && Expansion.allows(onlyWhen.value()));
    }

    /** The Score Categories that are on the pad for a Play with this Setup. */
    public List<ScoreCategory> categoriesFor(Map<String, String> setup, boolean soloPlay) {
        return categories.stream()
                .filter(category -> soloPlay || !category.soloOnly())
                .filter(category -> holds(category.onlyWhen(), setup))
                .toList();
    }

    /** The ways a Play with this Setup can be won. */
    public List<VictoryType> victoryTypesFor(Map<String, String> setup) {
        return victoryTypes.stream()
                .filter(victoryType -> holds(victoryType.onlyWhen(), setup))
                .toList();
    }

    /** Whether a Setup meets a condition. No condition is always met. */
    public boolean holds(OnlyWhen onlyWhen, Map<String, String> setup) {
        return onlyWhen == null || onlyWhen.value().equals(valueOf(onlyWhen.key(), setup));
    }

    /**
     * What a Setup holds for a Variant or an Expansion: what was chosen, or else the Variant's
     * default. An Expansion that was not chosen was not played with.
     */
    public String valueOf(String setupKey, Map<String, String> setup) {
        if (expansions.stream().anyMatch(expansion -> expansion.key().equals(setupKey))) {
            return setup.getOrDefault(setupKey, Expansion.WITHOUT);
        }
        Variant variant = variants.stream()
                .filter(candidate -> candidate.key().equals(setupKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No Variant or Expansion '" + setupKey + "' on the pad"));
        return setup.getOrDefault(setupKey, variant.defaultValue());
    }
}
