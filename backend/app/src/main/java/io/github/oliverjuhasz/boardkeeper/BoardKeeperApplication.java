package io.github.oliverjuhasz.boardkeeper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The application's entry point.
 *
 * <p>Every package directly below this one is a Spring Modulith application module, such as
 * {@code games}. A module's own package is its public interface; anything in its sub-packages
 * (by convention {@code internal}) is hidden from the other modules. {@code ModularityTests}
 * fails the build when a module breaks that rule.
 */
@SpringBootApplication
public class BoardKeeperApplication {

    public static void main(String[] args) {
        SpringApplication.run(BoardKeeperApplication.class, args);
    }
}
