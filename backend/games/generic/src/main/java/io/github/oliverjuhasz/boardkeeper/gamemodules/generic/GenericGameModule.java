package io.github.oliverjuhasz.boardkeeper.gamemodules.generic;

import io.github.oliverjuhasz.boardkeeper.gameapi.GameModule;
import java.util.UUID;

/**
 * The Generic Module: the built-in Game Module for any Game that has no module of its own. It
 * will record only totals, or only the winner (milestone M2).
 */
public final class GenericGameModule implements GameModule {

    /** Stored with every Play recorded through this module. Never change it (ADR 0012). */
    public static final UUID ID = UUID.fromString("38b119d2-8f9f-4e20-8068-cba305c06cdd");

    public static final String KEY = "generic";

    @Override
    public UUID id() {
        return ID;
    }

    @Override
    public String key() {
        return KEY;
    }
}
