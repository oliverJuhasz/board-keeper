package io.github.oliverjuhasz.boardkeeper.access;

/**
 * One thing an Account may do. Accounts get Permissions through their Roles.
 *
 * <p>The list is fixed in code, because code checks it. The names are stored in the database
 * and used in the API, so renaming one needs a database migration.
 */
public enum Permission {
    CREATE_PLAYERS,
    /** Rename a Player. */
    EDIT_PLAYERS,
    /** Remove a Player that has no Account and no Plays. */
    REMOVE_PLAYERS,
    CREATE_PLAYS,
    /** Create and cancel Invites and Reset Links. */
    MANAGE_INVITES,
    /** See the Accounts, give and take their Roles, and disable them. */
    MANAGE_ACCOUNTS,
    /** Create, change and delete Roles. */
    MANAGE_ROLES
}
