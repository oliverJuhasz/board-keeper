-- A Role is a named set of Permissions. The Permissions themselves are a fixed list in code
-- (the Permission enum); role_permission stores their names.
create table role (
    id         uuid        primary key default uuidv7(),
    name       text        not null check (name = btrim(name) and char_length(name) between 1 and 50),
    -- 'ADMIN' and 'MEMBER' mark the two built-in Roles. Every other Role has null here.
    built_in   text        unique check (built_in in ('ADMIN', 'MEMBER')),
    created_at timestamptz not null default now()
);

create unique index role_name_key on role (lower(name));

create table role_permission (
    role_id    uuid not null references role (id) on delete cascade,
    permission text not null,
    primary key (role_id, permission)
);

-- The Admin Role holds every Permission by definition, so it needs no rows in role_permission.
insert into role (name, built_in)
values ('Admin', 'ADMIN'),
       ('Member', 'MEMBER');

insert into role_permission (role_id, permission)
select role.id, granted.permission
from role
         cross join (values ('CREATE_PLAYERS'), ('CREATE_PLAYS')) as granted (permission)
where role.built_in = 'MEMBER';

create table account (
    id            uuid        primary key default uuidv7(),
    username      text        not null check (char_length(username) between 3 and 30),
    password_hash text        not null,
    -- The Claim: every Account claims exactly one Player, and a Player has at most one Account.
    player_id     uuid        not null unique references player (id),
    -- A language code such as 'hu'. The list of languages is the Language enum in code.
    language      text        not null,
    disabled      boolean     not null default false,
    created_at    timestamptz not null default now()
);

-- Usernames are unique whatever their capitals, and signing in ignores capitals too.
create unique index account_username_key on account (lower(username));

create table account_role (
    account_id uuid not null references account (id) on delete cascade,
    role_id    uuid not null references role (id) on delete cascade,
    primary key (account_id, role_id)
);
