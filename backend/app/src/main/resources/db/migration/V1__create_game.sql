-- The catalogue of Games. Each Game is scored by one Game Module, identified by that
-- module's permanent UUID (ADR 0012). Game Modules live in code, not in the database,
-- so game_module_id has no foreign key.
create table game (
    id             uuid        primary key default uuidv7(),
    name           text        not null check (btrim(name) <> ''),
    game_module_id uuid        not null,
    created_at     timestamptz not null default now()
);
