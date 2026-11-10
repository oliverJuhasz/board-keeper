package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * For local development only: the development database is empty on every start, so this
 * creates an Admin to sign in with, instead of going through the setup link each time.
 * It lives with the tests and is never part of the application that gets installed.
 *
 * <p>To try the setup link itself, start with {@code -Dboardkeeper.development.account=false}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class DevelopmentAccount {

    public static final String USERNAME = "admin";

    private static final Logger log = LoggerFactory.getLogger(DevelopmentAccount.class);

    @Bean
    @ConditionalOnProperty(name = "boardkeeper.development.account", havingValue = "true", matchIfMissing = true)
    ApplicationRunner createDevelopmentAccount(AccountRepository accounts, TestAccounts testAccounts) {
        return arguments -> {
            if (!accounts.anyExist()) {
                testAccounts.admin(USERNAME);
                log.info("Development Admin created. Sign in as '{}' with the password '{}'", USERNAME, TestAccounts.PASSWORD);
            }
        };
    }
}
