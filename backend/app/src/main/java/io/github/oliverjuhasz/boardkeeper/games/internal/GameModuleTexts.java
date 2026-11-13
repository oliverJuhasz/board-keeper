package io.github.oliverjuhasz.boardkeeper.games.internal;

import io.github.oliverjuhasz.boardkeeper.gameapi.GameModule;
import io.github.oliverjuhasz.boardkeeper.gameapi.ScorePad;
import io.github.oliverjuhasz.boardkeeper.gameapi.Variant;
import io.github.oliverjuhasz.boardkeeper.games.GameModules;
import io.github.oliverjuhasz.boardkeeper.language.Language;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The texts of every installed Game Module, read once at startup from each module's
 * {@code i18n/<key>_<language>.properties} files.
 *
 * <p>Every text's name must start with the module's key and a dot, such as {@code calico.name},
 * so that two modules can never claim the same name. A text that is missing in Dutch or
 * Hungarian is shown in English, and a warning is logged.
 */
@Component
public class GameModuleTexts {

    private static final Logger log = LoggerFactory.getLogger(GameModuleTexts.class);

    private final Map<Language, Map<String, String>> texts = new EnumMap<>(Language.class);

    GameModuleTexts(GameModules gameModules) {
        for (Language language : Language.values()) {
            texts.put(language, new TreeMap<>());
        }
        for (GameModule module : gameModules.all()) {
            Map<String, String> fallback = read(module, Language.FALLBACK);
            requireTextsForPad(module, fallback.keySet());
            for (Language language : Language.values()) {
                Map<String, String> own = language == Language.FALLBACK ? fallback : read(module, language);
                warnAboutDifferences(module, language, fallback.keySet(), own.keySet());

                Map<String, String> all = texts.get(language);
                all.putAll(fallback);
                // Only names that exist in English count; anything else is a typing mistake.
                own.forEach((name, text) -> all.computeIfPresent(name, (n, english) -> text));
            }
        }
    }

    /** The name of a module's Game in every language, such as "7 Wonders Duel" and "7 Csoda Párbaj". */
    public Set<String> namesOf(GameModule module) {
        Set<String> names = new TreeSet<>();
        for (Language language : Language.values()) {
            names.add(texts.get(language).get(nameText(module)));
        }
        return names;
    }

    /** The name of a module's Game in English, the language every module has. */
    public String englishNameOf(GameModule module) {
        return texts.get(Language.FALLBACK).get(nameText(module));
    }

    private static String nameText(GameModule module) {
        return module.key() + ".name";
    }

    /** Every Game Module's texts in the given language, by name. */
    public Map<String, String> in(Language language) {
        return Collections.unmodifiableMap(texts.get(language));
    }

    private static Map<String, String> read(GameModule module, Language language) {
        String file = "i18n/" + module.key() + "_" + language.code() + ".properties";
        Properties properties = new Properties();
        try (InputStream in = module.getClass().getClassLoader().getResourceAsStream(file)) {
            if (in == null) {
                return Map.of();
            }
            // Read as UTF-8: Properties.load(InputStream) would assume the older ISO 8859-1.
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }

        Map<String, String> result = new TreeMap<>();
        for (String name : properties.stringPropertyNames()) {
            if (!name.startsWith(module.key() + ".")) {
                throw new IllegalStateException(
                        "The text '" + name + "' in " + file + " must have a name starting with '" + module.key() + ".'");
            }
            result.put(name, properties.getProperty(name));
        }
        return result;
    }

    /**
     * The frontend draws a module's form from its score pad and looks up what to call each
     * part of it in the module's texts. A text missing in English would leave a blank there, so
     * the application refuses to start without it.
     */
    private static void requireTextsForPad(GameModule module, Set<String> english) {
        String prefix = module.key() + ".";
        ScorePad pad = module.scorePad();
        Set<String> needed = new TreeSet<>();
        needed.add(prefix + "name");
        for (Variant variant : pad.variants()) {
            needed.add(prefix + "variant." + variant.key());
            if (variant instanceof Variant.Choice choice) {
                choice.options().forEach(option -> needed.add(prefix + "variant." + variant.key() + "." + option));
            }
        }
        pad.categories().forEach(category -> needed.add(prefix + "category." + category.key()));
        if (pad.recorderDecidesTies()) {
            needed.add(prefix + "tie");
        }
        needed.removeAll(english);
        if (!needed.isEmpty()) {
            throw new IllegalStateException(
                    "Game Module '" + module.key() + "' has no English text for " + needed + ", which its score pad names");
        }
    }

    private static void warnAboutDifferences(GameModule module, Language language, Set<String> english, Set<String> own) {
        Set<String> missing = new TreeSet<>(english);
        missing.removeAll(own);
        if (!missing.isEmpty()) {
            log.warn("Game Module '{}' has no {} text for {}; the English text is used", module.key(), language, missing);
        }
        Set<String> unknown = new TreeSet<>(own);
        unknown.removeAll(english);
        if (!unknown.isEmpty()) {
            log.warn("Game Module '{}' has {} texts that have no English original, so they are ignored: {}",
                    module.key(), language, unknown);
        }
    }
}
