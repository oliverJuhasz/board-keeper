package io.github.oliverjuhasz.boardkeeper.stats.internal;

import io.github.oliverjuhasz.boardkeeper.plays.PlayChanged;
import io.github.oliverjuhasz.boardkeeper.plays.PlayDeleted;
import io.github.oliverjuhasz.boardkeeper.plays.PlayRecorded;
import io.github.oliverjuhasz.boardkeeper.plays.Plays;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the module's copy of the Plays up to date from the plays module's events.
 *
 * <p>An {@link ApplicationModuleListener} runs after the transaction that published the event
 * has been committed, on a thread of its own and in a new transaction. Spring Modulith notes
 * each event in its event publication registry before the listener runs and crosses it off
 * once the listener has finished without an exception. An event that was not crossed off, say
 * because the application stopped in between, is handed to the listener again at the next
 * start. So a listener may see an event twice, and has to give the same result when it does.
 */
@Component
class StatsUpdater {

    private final Plays plays;
    private final StatsRepository repository;

    StatsUpdater(Plays plays, StatsRepository repository) {
        this.plays = plays;
        this.repository = repository;
    }

    @ApplicationModuleListener
    void whenRecorded(PlayRecorded event) {
        refresh(event.playId());
    }

    @ApplicationModuleListener
    void whenChanged(PlayChanged event) {
        refresh(event.playId());
    }

    @ApplicationModuleListener
    void whenDeleted(PlayDeleted event) {
        refresh(event.playId());
    }

    /**
     * Copies the Play as it is now, or removes the copy when the Play no longer exists. Whatever
     * the event, and however often it comes, the copy ends up like the Play.
     */
    @Transactional
    public void refresh(UUID playId) {
        // Listeners run side by side. Without the lock, the listeners of two quick changes to
        // one Play could read it in one order and write it in the other, leaving the older copy.
        repository.lock(playId);
        plays.find(playId).ifPresentOrElse(repository::save, () -> repository.delete(playId));
    }
}
