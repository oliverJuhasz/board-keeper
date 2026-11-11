-- How Plays of a Game are recorded unless the recorder chooses otherwise for a single Play.
-- These are the defaults of a Game that is scored by the Generic Module; a Game with a module
-- of its own gets its defaults from that module's code.
alter table game
    -- Everyone wins or loses together, against the game.
    add column cooperative       boolean not null default false,
    add column recording_mode    text    not null default 'POINTS' check (recording_mode in ('POINTS', 'WINNER_ONLY')),
    -- For a competitive Play recorded in points: the lowest score wins instead of the highest.
    add column lowest_score_wins boolean not null default false,
    add constraint game_name_tidy check (name = btrim(name) and char_length(name) <= 100);

-- Two Games cannot share a name, whatever their capitals.
create unique index game_name_key on game (lower(name));

-- A Member can add Games and edit them. Removing a Game needs a Permission of its own.
insert into role_permission (role_id, permission)
select role.id, granted.permission
from role
         cross join (values ('CREATE_GAMES'), ('EDIT_GAMES')) as granted (permission)
where role.built_in = 'MEMBER';
