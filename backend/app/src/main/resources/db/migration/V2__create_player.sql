-- A Player is a person who takes part in Plays. Names are unique whatever their capitals,
-- so "bence" and "Bence" cannot both exist.
create table player (
    id         uuid        primary key default uuidv7(),
    name       text        not null check (name = btrim(name) and char_length(name) between 1 and 50),
    created_at timestamptz not null default now()
);

create unique index player_name_key on player (lower(name));
