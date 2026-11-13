-- Spring Modulith's event publication registry (ADR 0011). Every event the application
-- publishes is stored here before its listeners run, and removed once they have handled it.
-- An event left behind, because the application stopped before a listener finished, is handed
-- to its listeners again at the next start. This is the table Spring Modulith 2 expects
-- ("schema v2" in the spring-modulith-events-jdbc jar); Flyway creates it, not Spring Modulith.
create table event_publication (
    id                     uuid                     not null primary key,
    listener_id            text                     not null,
    event_type             text                     not null,
    serialized_event       text                     not null,
    publication_date       timestamp with time zone not null,
    completion_date        timestamp with time zone,
    status                 text,
    completion_attempts    integer,
    last_resubmission_date timestamp with time zone
);
create index event_publication_serialized_event_hash_idx on event_publication using hash (serialized_event);
create index event_publication_by_completion_date_idx on event_publication (completion_date);

-- The stats module's own copy of every Play: what statistics are worked out from (ADR 0011).
-- It is filled from the events of the plays module. There are no foreign keys to
-- the tables of other modules on purpose: this copy belongs to the stats module alone.
--
-- To rebuild it, for example after changing its shape, empty both tables. They are filled
-- again from all Plays when the application next starts.
create table stats_play (
    play_id        uuid    primary key,
    game_id        uuid    not null,
    -- The Game Module that scored the Play, which says what the keys in the scores mean.
    game_module_id uuid    not null,
    played_on      date    not null,
    cooperative    boolean not null,
    -- A Play of one that isn't cooperative.
    solo           boolean not null,
    -- For a Cooperative Play: whether the group won, and its score if there was one.
    group_won      boolean,
    group_total    integer
);

create index stats_play_game_id_idx on stats_play (game_id);

create table stats_result (
    play_id   uuid    not null references stats_play (play_id) on delete cascade,
    player_id uuid    not null,
    total     integer,
    -- PLACING is a reserved word in PostgreSQL, so it is quoted (see V9).
    "placing" integer,
    won       boolean,
    scores    jsonb   not null,
    primary key (play_id, player_id)
);

create index stats_result_player_id_idx on stats_result (player_id);
