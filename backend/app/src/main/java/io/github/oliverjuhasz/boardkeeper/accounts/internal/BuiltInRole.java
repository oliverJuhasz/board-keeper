package io.github.oliverjuhasz.boardkeeper.accounts.internal;

/** The two Roles that always exist. Their names are stored in the {@code role.built_in} column. */
enum BuiltInRole {
    /** Holds every Permission. At least one Account always has it. */
    ADMIN,
    /** What an Invite gives unless the Admin picks another Role. */
    MEMBER
}
