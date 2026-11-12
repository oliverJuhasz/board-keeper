package io.github.oliverjuhasz.boardkeeper.gameapi;

/**
 * The calculated part of one Player's Result in a competitive Play.
 *
 * @param total the Player's total; null when no scores were recorded
 * @param placing the Player's Placing, counted from 1, where tied Players share one; null when
 *     only the winner was recorded, because the others were then not put in order
 * @param won whether the Player won. Everyone in first Placing has won.
 */
public record PlayerResult(Integer total, Integer placing, boolean won) {}
