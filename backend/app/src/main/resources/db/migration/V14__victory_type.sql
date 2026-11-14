-- How a Play was won, for Games that can be won in more than one way, such as on points or by
-- military supremacy in 7 Wonders Duel. Entered by the recorder: the key of one of the Game
-- Module's Victory Types. Null for a Game that is won in one way only.
alter table play add column victory_type text check (victory_type <> '');

-- The statistics count the Plays of a Game by how they were won.
alter table stats_play add column victory_type text;
