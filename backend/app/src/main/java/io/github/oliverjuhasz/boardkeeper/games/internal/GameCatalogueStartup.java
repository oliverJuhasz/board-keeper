package io.github.oliverjuhasz.boardkeeper.games.internal;

import io.github.oliverjuhasz.boardkeeper.BoardKeeperApplication;
import io.github.oliverjuhasz.boardkeeper.games.Games;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Brings the catalogue in step with the installed Game Modules when the application starts.
 * Spring Boot calls every {@link ApplicationRunner} once, after the database has been migrated
 * and before the application takes requests.
 */
@Component
// Work for the application that serves Board Keeper, not for a command.
@Profile("!" + BoardKeeperApplication.COMMAND_PROFILE)
class GameCatalogueStartup implements ApplicationRunner {

    private final Games games;

    GameCatalogueStartup(Games games) {
        this.games = games;
    }

    @Override
    public void run(ApplicationArguments args) {
        games.addGamesOfModules();
    }
}
