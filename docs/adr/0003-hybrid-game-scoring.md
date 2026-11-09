# Game scoring: data-defined score sheets, with game-specific code as the exception

Most games are scored with a score sheet defined as data: a list of categories that the app adds up. A new game can be set up this way without a code change or a release. Games whose rules need real computation get scoring written in code, on both backend and frontend. For example, 7 Wonders scores science from sets of symbols plus the square of each symbol count.

We rejected recording only a final score per player, because category breakdowns are wanted for statistics. We also rejected code-only game modules, because every new game would then need a release.
