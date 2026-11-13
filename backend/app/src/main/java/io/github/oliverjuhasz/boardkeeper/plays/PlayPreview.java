package io.github.oliverjuhasz.boardkeeper.plays;

import java.util.List;
import java.util.UUID;

/**
 * How a Play would end if it were recorded as entered.
 *
 * @param group how it would end for the group, for a Cooperative Play; otherwise null
 * @param results one per Player, in the order they were entered
 * @param tieForRecorder the Players among whom the recorder may say who won, because they are
 *     level on everything that was entered and the Game decides such a tie by something that
 *     was not. Empty when what was entered decides who won.
 */
public record PlayPreview(Play.GroupResult group, List<Play.Result> results, List<UUID> tieForRecorder) {}
