-- Each Player's Player Setup, such as their Wonder and its side in 7 Wonders: the option chosen
-- for each of the Game Module's Player Setup choices, by the choice's key. Entered by the
-- recorder; a choice nobody made is left out. The keys belong to the Game Module, so they are
-- kept as JSON, as the Setup and the scores are.
alter table play_result add column player_setup jsonb not null default '{}';
