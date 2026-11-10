-- An Invite offers the person behind a Player the chance to register an Account. A row exists
-- only while the Invite is open: accepting or cancelling it deletes the row.
create table invite (
    id         uuid        primary key default uuidv7(),
    -- Only a hash of the link's token is kept, so reading the database does not reveal a usable link.
    token_hash text        not null unique,
    -- At most one open Invite per Player. Removing the Player removes the Invite.
    player_id  uuid        not null unique references player (id) on delete cascade,
    -- The Role the new Account starts with.
    role_id    uuid        not null references role (id),
    created_by uuid        references account (id) on delete set null,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null
);
