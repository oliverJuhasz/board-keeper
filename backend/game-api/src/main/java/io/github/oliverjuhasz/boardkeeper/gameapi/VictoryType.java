package io.github.oliverjuhasz.boardkeeper.gameapi;

/**
 * One way a Play of a Game can be won, for Games that can be won in more than one way, such as
 * on points or by military supremacy in 7 Wonders Duel. The recorder says how each Play was won.
 *
 * <p>A Victory Type is either on points, when the score pad decides who won, or not, when the
 * Play ended outright and nobody counted points. A Play won the second way is recorded like one
 * with Recording Mode "Winner only": the recorder marks its one winner.
 *
 * @param key the Victory Type's name within its module: lower camel case, such as
 *     {@code militarySupremacy}
 * @param onPoints whether the score pad decides who won
 * @param onlyWhen the Setup that makes this way of winning possible; null when it always is
 */
public record VictoryType(String key, boolean onPoints, OnlyWhen onlyWhen) {

    /** Won on points, by the score pad. */
    public static VictoryType onPoints(String key) {
        return new VictoryType(key, true, null);
    }

    /** Won outright, without counting points. */
    public static VictoryType outright(String key) {
        return new VictoryType(key, false, null);
    }

    /** The same Victory Type, possible only with this Setup, such as with an Expansion. */
    public VictoryType onlyWhen(String setupKey, String value) {
        return new VictoryType(key, onPoints, new OnlyWhen(setupKey, value));
    }
}
