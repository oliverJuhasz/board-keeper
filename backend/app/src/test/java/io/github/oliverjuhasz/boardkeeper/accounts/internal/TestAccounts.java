package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.ACCOUNT;

import io.github.oliverjuhasz.boardkeeper.language.Language;
import io.github.oliverjuhasz.boardkeeper.players.Players;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/** Creates Accounts for tests, without going through the setup link or an Invite. */
@Component
public class TestAccounts {

    /** Long enough for the password rule, and the same for every test Account. */
    public static final String PASSWORD = "correct horse battery staple";

    private final Players players;
    private final AccountRegistration registration;
    private final DSLContext dsl;

    TestAccounts(Players players, AccountRegistration registration, DSLContext dsl) {
        this.players = players;
        this.registration = registration;
        this.dsl = dsl;
    }

    public UUID admin(String username) {
        return create(username, BuiltInRole.ADMIN);
    }

    public UUID member(String username) {
        return create(username, BuiltInRole.MEMBER);
    }

    public void disable(UUID accountId) {
        dsl.update(ACCOUNT).set(ACCOUNT.DISABLED, true).where(ACCOUNT.ID.eq(accountId)).execute();
    }

    private UUID create(String username, BuiltInRole role) {
        UUID playerId = players.create("Player of " + username).id();
        return registration.register(username, PASSWORD, playerId, Language.ENGLISH, role);
    }
}
