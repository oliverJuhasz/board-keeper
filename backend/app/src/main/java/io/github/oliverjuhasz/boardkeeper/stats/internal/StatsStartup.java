package io.github.oliverjuhasz.boardkeeper.stats.internal;

import io.github.oliverjuhasz.boardkeeper.plays.Plays;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Fills the module's copy of the Plays when it is empty while Plays exist: the first time the
 * application starts with this module, and after the copy has been emptied to rebuild it.
 */
@Component
class StatsStartup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StatsStartup.class);

    private final Plays plays;
    private final StatsUpdater updater;
    private final StatsRepository repository;

    StatsStartup(Plays plays, StatsUpdater updater, StatsRepository repository) {
        this.plays = plays;
        this.updater = updater;
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!repository.isEmpty()) {
            return;
        }
        List<UUID> ids = plays.allIds();
        if (!ids.isEmpty()) {
            log.info("Filling the statistics from {} Plays", ids.size());
            // One transaction per Play, so that a Play recorded meanwhile is not held up for long.
            ids.forEach(updater::refresh);
        }
    }
}
