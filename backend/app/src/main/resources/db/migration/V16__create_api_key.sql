-- An API Key lets a program use the API as one Account (ADR 0017). Like a Reset Link, only a
-- hash of its secret is kept: the secret is shown once, when the API Key is created.
--
-- Using API Keys needs the Permission USE_API_KEYS. The Admin Role holds every Permission by
-- definition; no other Role gets it here, so an Admin decides who may use API Keys.
create table api_key (
    id           uuid        primary key default uuidv7(),
    account_id   uuid        not null references account (id) on delete cascade,
    name         text        not null check (name = btrim(name) and char_length(name) between 1 and 50),
    secret_hash  text        not null unique,
    created_at   timestamptz not null default now(),
    -- Null for an API Key that never expires.
    expires_at   timestamptz,
    -- Null until it is first used. Recorded at most once an hour, so that a busy script does
    -- not write to the database on every request.
    last_used_at timestamptz
);

-- Names are unique among one Account's API Keys, whatever their capitals.
create unique index api_key_name_key on api_key (account_id, lower(name));
