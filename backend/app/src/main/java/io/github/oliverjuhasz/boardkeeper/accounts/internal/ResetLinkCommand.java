package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import io.github.oliverjuhasz.boardkeeper.BoardKeeperApplication;
import io.github.oliverjuhasz.boardkeeper.accounts.internal.Passwords.NewResetLink;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Writes a Reset Link for an Account to the log. It is for an installation whose only Admin
 * has forgotten their password, so that nobody is left who could create a Reset Link in the
 * app. It needs only the right to run commands on Board Keeper's container:
 *
 * <pre>docker compose exec app java -jar board-keeper.jar --reset-link-for=USERNAME</pre>
 *
 * <p>Like the first-time setup link, the link is written as an address without its host,
 * because the application does not know the address it is reached at.
 */
@Component
@Profile(BoardKeeperApplication.COMMAND_PROFILE)
class ResetLinkCommand implements ApplicationRunner, ExitCodeGenerator {

    static final String OPTION = "reset-link-for";

    private static final Logger log = LoggerFactory.getLogger(ResetLinkCommand.class);

    private final AccountRepository accounts;
    private final Passwords passwords;
    private int exitCode;

    ResetLinkCommand(AccountRepository accounts, Passwords passwords) {
        this.accounts = accounts;
        this.passwords = passwords;
    }

    @Override
    public void run(ApplicationArguments args) {
        exitCode = 0;
        List<String> usernames = args.getOptionValues(OPTION);
        if (usernames == null) {
            return;
        }
        if (usernames.size() != 1 || usernames.getFirst().isBlank()) {
            fail("Give one username: --{}=USERNAME", OPTION);
            return;
        }
        String username = usernames.getFirst();
        accounts.findCredentials(username)
                .ifPresentOrElse(
                        account -> {
                            if (account.disabled()) {
                                fail("The Account \"{}\" is disabled. Enable it before creating a Reset Link.", username);
                            } else {
                                offer(username, passwords.createResetLinkFromCommandLine(account.accountId()));
                            }
                        },
                        () -> fail("There is no Account with the username \"{}\".", username));
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    private void offer(String username, NewResetLink link) {
        log.info("""

                ------------------------------------------------------------------
                A Reset Link for the Account "{}".
                To choose a new password, open this page on Board Keeper's address:

                    /reset-password/{}

                It works once, until {}. Whoever opens it can
                take over the Account, so keep it to yourself.
                ------------------------------------------------------------------
                """, username, link.token(), link.expiresAt());
    }

    private void fail(String message, Object argument) {
        log.error(message, argument);
        exitCode = 1;
    }
}
