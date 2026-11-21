/**
 * Exports and Imports: an Installation's Games, Players and Plays as one file (ADR 0020), and
 * bringing the Plays of such a file into another Installation (ADR 0021).
 *
 * <p>The module has no tables of its own. It reads and writes through the services of the games,
 * players and plays modules.
 */
package io.github.oliverjuhasz.boardkeeper.exports;
