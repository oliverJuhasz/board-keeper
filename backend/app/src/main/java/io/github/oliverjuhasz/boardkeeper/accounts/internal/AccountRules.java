package io.github.oliverjuhasz.boardkeeper.accounts.internal;

/** The rules for usernames and passwords. The frontend's forms repeat them. */
final class AccountRules {

    /** 3 to 30 letters, digits, dots, underscores and hyphens. Letters may be from any alphabet. */
    static final String USERNAME_PATTERN = "[\\p{L}\\p{N}._-]{3,30}";

    static final int MIN_PASSWORD_LENGTH = 16;

    /** A generous limit, only there so nobody can keep the server busy hashing a huge text. */
    static final int MAX_PASSWORD_LENGTH = 200;

    private AccountRules() {}
}
