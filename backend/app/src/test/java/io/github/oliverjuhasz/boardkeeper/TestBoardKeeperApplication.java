package io.github.oliverjuhasz.boardkeeper;

import io.github.oliverjuhasz.boardkeeper.accounts.internal.DevelopmentAccount;
import org.springframework.boot.SpringApplication;

/**
 * Runs the application for local development, with a throwaway PostgreSQL from
 * {@link TestcontainersConfiguration}. Start it with {@code ./mvnw spring-boot:test-run}.
 * The database is empty on every start, apart from the Admin that {@link DevelopmentAccount}
 * creates.
 */
public class TestBoardKeeperApplication {

    public static void main(String[] args) {
        SpringApplication.from(BoardKeeperApplication::main)
                .with(TestcontainersConfiguration.class, DevelopmentAccount.class)
                .run(args);
    }
}
