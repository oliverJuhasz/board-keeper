package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.ACCOUNT;
import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.ACCOUNT_ROLE;
import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.ROLE;
import static io.github.oliverjuhasz.boardkeeper.jooq.Tables.ROLE_PERMISSION;

import io.github.oliverjuhasz.boardkeeper.TestBrowser;
import io.github.oliverjuhasz.boardkeeper.access.Permission;
import io.github.oliverjuhasz.boardkeeper.language.Language;
import io.github.oliverjuhasz.boardkeeper.players.Players;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

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

    /** A Member that also holds a new Role with exactly the given Permissions. */
    public UUID memberWith(String username, Permission... permissions) {
        UUID accountId = member(username);
        UUID roleId = dsl.insertInto(ROLE, ROLE.NAME)
                .values("Role of " + username)
                .returningResult(ROLE.ID)
                .fetchSingle()
                .value1();
        for (Permission permission : permissions) {
            dsl.insertInto(ROLE_PERMISSION, ROLE_PERMISSION.ROLE_ID, ROLE_PERMISSION.PERMISSION)
                    .values(roleId, permission.name())
                    .execute();
        }
        dsl.insertInto(ACCOUNT_ROLE, ACCOUNT_ROLE.ACCOUNT_ID, ACCOUNT_ROLE.ROLE_ID)
                .values(accountId, roleId)
                .execute();
        return accountId;
    }

    /** A browser in which the Account has signed in with a password other than the usual one. */
    public TestBrowser signedIn(MockMvcTester mvc, String username, String password) {
        TestBrowser browser = TestBrowser.open(mvc);
        var result = browser.post("/api/session", signInRequest(username, password));
        if (result.getResponse().getStatus() != 200) {
            throw new IllegalStateException("Could not sign in as " + username);
        }
        return browser;
    }

    public static String signInRequest(String username, String password) {
        return "{\"username\": \"%s\", \"password\": \"%s\", \"staySignedIn\": false}".formatted(username, password);
    }

    /** A browser in which the Account with this username has signed in. */
    public TestBrowser signedIn(MockMvcTester mvc, String username) {
        return signedIn(mvc, username, PASSWORD);
    }

    public UUID playerOf(UUID accountId) {
        return dsl.select(ACCOUNT.PLAYER_ID).from(ACCOUNT).where(ACCOUNT.ID.eq(accountId)).fetchSingle().value1();
    }

    public void disable(UUID accountId) {
        dsl.update(ACCOUNT).set(ACCOUNT.DISABLED, true).where(ACCOUNT.ID.eq(accountId)).execute();
    }

    private UUID create(String username, BuiltInRole role) {
        UUID playerId = players.create("Player of " + username).id();
        return registration.register(username, PASSWORD, playerId, Language.ENGLISH, role);
    }
}
