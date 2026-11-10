-- A Reset Link lets the owner of an Account choose a new password. Like an Invite, a row
-- exists only while the link is open, and only a hash of the link's token is kept.
create table password_reset (
    id         uuid        primary key default uuidv7(),
    token_hash text        not null unique,
    -- At most one open Reset Link per Account.
    account_id uuid        not null unique references account (id) on delete cascade,
    created_by uuid        references account (id) on delete set null,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null
);
