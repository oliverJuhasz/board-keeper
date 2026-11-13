package io.github.oliverjuhasz.boardkeeper;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The application's entry point.
 *
 * <p>Every package directly below this one is a Spring Modulith application module, such as
 * {@code games}. A module's own package is its public interface; anything in its sub-packages
 * (by convention {@code internal}) is hidden from the other modules. {@code ModularityTests}
 * fails the build when a module breaks that rule.
 *
 * <p>Started with the option of a command, such as {@code --reset-link-for=<username>}, the
 * application does that command's work and stops, instead of serving Board Keeper. It then runs
 * with the {@value #COMMAND_PROFILE} profile, whose settings are in
 * {@code application-command.properties}.
 */
@SpringBootApplication
public class BoardKeeperApplication {

    public static final String COMMAND_PROFILE = "command";

    /** The options that start a command. The command itself is a module's ApplicationRunner. */
    public static final List<String> COMMAND_OPTIONS = List.of("reset-link-for");

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(BoardKeeperApplication.class);
        if (runsACommand(args)) {
            application.setAdditionalProfiles(COMMAND_PROFILE);
            // SpringApplication.exit closes the application and returns the exit code that the
            // command chose, so a script can tell whether it worked.
            System.exit(SpringApplication.exit(application.run(args)));
        }
        application.run(args);
    }

    static boolean runsACommand(String[] args) {
        return Arrays.stream(args)
                .anyMatch(arg -> COMMAND_OPTIONS.stream().anyMatch(option -> arg.startsWith("--" + option)));
    }
}
