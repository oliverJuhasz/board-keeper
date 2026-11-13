-- Passkeys, kept by Spring Security's WebAuthn support (JdbcPublicKeyCredentialUserEntityRepository
-- and JdbcUserCredentialRepository), which expects these tables under these names. They are the
-- PostgreSQL version of the scripts in the spring-security-web jar
-- (org/springframework/security/user-entities-schema.sql and user-credentials-schema-postgres.sql).
-- They belong to the accounts module, which uses them only through Spring Security.

-- The WebAuthn "user" of an Account: a random id that the passkey keeps instead of anything
-- about the Account, and the name a phone shows when it offers the passkey, which is the
-- Account's username.
create table user_entities (
    id           varchar(1000) not null primary key,
    name         varchar(100)  not null unique,
    display_name varchar(200)
);

-- One row per passkey: its public key, with which the server checks what the passkey signs.
-- The private key never leaves the phone or security key.
create table user_credentials (
    credential_id                varchar(1000) not null primary key,
    user_entity_user_id          varchar(1000) not null references user_entities (id) on delete cascade,
    public_key                   bytea         not null,
    signature_count              bigint,
    uv_initialized               boolean,
    backup_eligible              boolean       not null,
    authenticator_transports     varchar(1000),
    public_key_credential_type   varchar(100),
    backup_state                 boolean       not null,
    attestation_object           bytea,
    attestation_client_data_json bytea,
    created                      timestamp,
    last_used                    timestamp,
    -- The name the Account's owner gave it, such as "My phone".
    label                        varchar(1000) not null
);

create index user_credentials_user_entity_user_id_idx on user_credentials (user_entity_user_id);
