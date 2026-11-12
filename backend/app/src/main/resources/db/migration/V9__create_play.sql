-- A Play: one occasion on which a group of Players played a Game to the end.
--
-- A Play keeps two things side by side (ADR 0010): what the recorder entered, and what the
-- Game Module calculated from it at the time. The calculated columns are only replaced when
-- someone asks for a Recalculation.
create table play (
    id             uuid        primary key default uuidv7(),
    -- No "on delete": a Game that has Plays cannot be removed.
    game_id        uuid        not null references game (id),
    -- The Game Module that scored this Play (ADR 0012). It stays the same even if the Game is
    -- given to another module later.
    game_module_id uuid        not null,
    played_on      date        not null,
    location       text        check (location = btrim(location) and char_length(location) between 1 and 100),
    notes          text        check (notes = btrim(notes) and char_length(notes) between 1 and 2000),

    -- Entered.
    recording_mode text        not null check (recording_mode in ('POINTS', 'WINNER_ONLY')),
    -- The Setup: the value chosen for each Variant, by the Variant's key. The keys belong to
    -- the Game Module, so they are kept as JSON and not as columns.
    setup          jsonb       not null default '{}',
    -- For a Cooperative Play: the value entered for each of the group's Score Categories.
    group_scores   jsonb,

    -- Calculated.
    cooperative    boolean     not null,
    -- For a Cooperative Play: whether the group won, and its score if there is one.
    group_won      boolean,
    group_total    integer,

    -- The Player of the Account that recorded the Play.
    recorded_by    uuid        not null references player (id),
    recorded_at    timestamptz not null default now(),

    constraint play_group_only_when_cooperative check (
        case
            when cooperative then group_won is not null and group_scores is not null
            else group_won is null and group_scores is null and group_total is null
        end)
);

-- For the play history, newest first, of everything and of one Game.
create index play_played_on_idx on play (played_on desc, id desc);
create index play_game_id_idx on play (game_id, played_on desc);

-- The Result of one Player in one Play.
create table play_result (
    play_id       uuid    not null references play (id) on delete cascade,
    -- No "on delete": a Player who has taken part in a Play cannot be removed.
    player_id     uuid    not null references player (id),
    -- The order in which the recorder entered the Players, counted from 0.
    entry_order   integer not null check (entry_order >= 0),

    -- Entered: the value for each Score Category by its key, and whether the recorder marked
    -- the Player as a winner.
    scores        jsonb   not null default '{}',
    marked_winner boolean not null default false,

    -- Calculated. A Play recorded with only the winner has no totals and no Placings, and a
    -- Cooperative Play has none per Player.
    total         integer,
    -- PLACING is a reserved word in PostgreSQL (it belongs to the OVERLAY function), so
    -- hand-written SQL has to quote this column. jOOQ quotes every name anyway.
    "placing"     integer check ("placing" >= 1),
    won           boolean not null,

    primary key (play_id, player_id),
    unique (play_id, entry_order)
);

-- For "the Plays this Player took part in".
create index play_result_player_id_idx on play_result (player_id);
