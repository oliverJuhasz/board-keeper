package io.github.oliverjuhasz.boardkeeper.games;

import io.github.oliverjuhasz.boardkeeper.gameapi.GameModule;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The Game Modules installed in this application.
 *
 * <p>Creating it checks every module, so the application refuses to start when two modules
 * share an id or a key (ADR 0012), or when a module has a malformed key or no English
 * translation file.
 */
public final class GameModules {

    /** The key of the Generic Module, which scores every Game that has no module of its own. */
    public static final String GENERIC_KEY = "generic";

    private static final Pattern KEY_FORMAT = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    private final List<GameModule> modules;

    public GameModules(Collection<? extends GameModule> modules) {
        Set<UUID> ids = new HashSet<>();
        Set<String> keys = new HashSet<>();
        for (GameModule module : modules) {
            String name = module.getClass().getName();
            if (module.id() == null) {
                throw new IllegalStateException("Game Module " + name + " has no id");
            }
            if (module.key() == null || !KEY_FORMAT.matcher(module.key()).matches()) {
                throw new IllegalStateException("Game Module " + name + " has the malformed key '" + module.key()
                        + "': use lower-case letters and digits, in words separated by single hyphens");
            }
            if (!ids.add(module.id())) {
                throw new IllegalStateException("More than one Game Module has the id " + module.id());
            }
            if (!keys.add(module.key())) {
                throw new IllegalStateException("More than one Game Module has the key '" + module.key() + "'");
            }
            String englishTexts = "i18n/" + module.key() + "_en.properties";
            if (module.getClass().getClassLoader().getResource(englishTexts) == null) {
                throw new IllegalStateException("Game Module '" + module.key() + "' has no " + englishTexts);
            }
        }
        this.modules = modules.stream()
                .<GameModule>map(module -> module)
                .sorted(Comparator.comparing(GameModule::key))
                .toList();
    }

    /** Every installed Game Module, ordered by key. */
    public List<GameModule> all() {
        return modules;
    }

    public Optional<GameModule> byId(UUID id) {
        return modules.stream().filter(module -> module.id().equals(id)).findFirst();
    }

    /** The Generic Module. Board Keeper cannot run without it. */
    public GameModule generic() {
        return modules.stream()
                .filter(module -> module.key().equals(GENERIC_KEY))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The Generic Module ('" + GENERIC_KEY + "') is not installed"));
    }
}
