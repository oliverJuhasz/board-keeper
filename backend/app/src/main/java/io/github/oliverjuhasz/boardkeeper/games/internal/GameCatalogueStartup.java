package io.github.oliverjuhasz.boardkeeper.games.internal;

import io.github.oliverjuhasz.boardkeeper.games.Games;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Brings the catalogue in step with the installed Game Modules when the application starts.
 * Spring Boot calls every {@link ApplicationRunner} once, after the database has been migrated
 * and before the application takes requests.
 */
@Component
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
