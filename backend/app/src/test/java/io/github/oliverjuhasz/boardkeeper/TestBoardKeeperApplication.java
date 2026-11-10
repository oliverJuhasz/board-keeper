package io.github.oliverjuhasz.boardkeeper;

import org.springframework.boot.SpringApplication;

/**
 * Runs the application for local development, with a throwaway PostgreSQL from
 * {@link TestcontainersConfiguration}. Start it with {@code ./mvnw -pl app spring-boot:test-run}.
 * The database is empty on every start.
 */
public class TestBoardKeeperApplication {

    public static void main(String[] args) {
        SpringApplication.from(BoardKeeperApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
