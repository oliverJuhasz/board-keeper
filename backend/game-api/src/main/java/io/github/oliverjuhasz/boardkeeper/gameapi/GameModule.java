package io.github.oliverjuhasz.boardkeeper.gameapi;

import java.util.UUID;

/**
 * A Game Module: the package that adds support for one Game to Board Keeper.
 *
 * <p>The core finds Game Modules with Java's {@link java.util.ServiceLoader}. To be found, a
 * module's jar lists its implementation class in the file
 * {@code META-INF/services/io.github.oliverjuhasz.boardkeeper.gameapi.GameModule}, and the
 * class has a public no-argument constructor.
 *
 * <p>This is the skeleton of the contract: identity only. How a module describes and scores
 * its Game is added in milestone M3.
 */
public interface GameModule {

    /**
     * The module's permanent identity. Plays store it, so it must never change once the module
     * has been released (ADR 0012). Generate it once and keep it as a constant.
     */
    UUID id();

    /**
     * The module's readable name for files, folders and logs, such as {@code calico}: lower-case
     * letters and digits, in words separated by single hyphens. The module's translation files
     * are named after it: {@code i18n/<key>_en.properties}, and likewise for {@code nl} and
     * {@code hu}. English is required.
     */
    String key();
}
