package io.github.oliverjuhasz.boardkeeper.games.internal;

import io.github.oliverjuhasz.boardkeeper.gameapi.GameModule;
import io.github.oliverjuhasz.boardkeeper.games.GameModules;
import java.util.ServiceLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class GameModulesConfiguration {

    /**
     * Finds every Game Module on the classpath. {@link ServiceLoader} is Java's built-in plug-in
     * mechanism: it reads the {@code META-INF/services} file in each module's jar.
     */
    @Bean
    GameModules gameModules() {
        GameModules modules = new GameModules(ServiceLoader.load(GameModule.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList());
        // Fail at startup, not at the first added Game, if the Generic Module is missing.
        modules.generic();
        return modules;
    }
}
