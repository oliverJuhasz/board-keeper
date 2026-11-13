/**
 * Statistics: how often each Game is played, and how each Player does at it.
 *
 * <p>The module keeps a copy of every Play in tables of its own and works the statistics out
 * from those (ADR 0011). It keeps the copy up to date from the events the plays module
 * publishes, and never reads the plays module's tables.
 */
package io.github.oliverjuhasz.boardkeeper.stats;
