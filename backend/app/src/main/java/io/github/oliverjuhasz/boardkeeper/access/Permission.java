package io.github.oliverjuhasz.boardkeeper.access;

/**
 * One thing an Account may do. Accounts get Permissions through their Roles.
 *
 * <p>The list is fixed in code, because code checks it. The names are stored in the database
 * and used in the API, so renaming one needs a database migration.
 *
 * <p>Some Permissions are for Admins only: they belong to the Admin Role and cannot be put
 * into any other Role (ADR 0015).
 */
public enum Permission {
    CREATE_PLAYERS(false),
    /** Rename a Player. */
    EDIT_PLAYERS(false),
    /** Remove a Player that has no Account and no Plays. */
    REMOVE_PLAYERS(false),
    CREATE_GAMES(false),
    /** Rename a Game and change how its Plays are recorded. */
    EDIT_GAMES(false),
    /** Remove a Game that has no Plays. */
    REMOVE_GAMES(false),
    CREATE_PLAYS(false),
    /** Edit or delete a Play that the Account recorded itself. */
    CHANGE_PLAYS_RECORDED(false),
    /** Edit or delete a Play that the Account's own Player took part in. */
    CHANGE_PLAYS_TAKEN_PART_IN(false),
    /** Edit or delete any Play, whoever recorded it or played in it. */
    CHANGE_ANY_PLAY(false),
    /**
     * Create API Keys for the Account's own use, and use them (ADR 0017). Without it, the
     * Account's API Keys stop working, but it can still see and delete them.
     */
    USE_API_KEYS(false),
    /** Create and cancel Invites and Reset Links. */
    MANAGE_INVITES(true),
    /** See the Accounts, give and take their Roles, and disable them. */
    MANAGE_ACCOUNTS(true),
    /** Create, change and delete Roles. */
    MANAGE_ROLES(true),
    /**
     * Recalculate every Play of a Game at once (ADR 0010). One Play can be recalculated by
     * whoever may change it.
     */
    RECALCULATE_PLAYS(true);

    private final boolean adminOnly;

    Permission(boolean adminOnly) {
        this.adminOnly = adminOnly;
    }

    /** Whether only the Admin Role can hold this Permission. */
    public boolean isAdminOnly() {
        return adminOnly;
    }
}
