package io.github.oliverjuhasz.boardkeeper.gamemodules.generic;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.oliverjuhasz.boardkeeper.gameapi.GameModule;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class GenericGameModuleTests {

    @Test
    void isFoundThroughTheServiceLoader() {
        assertThat(ServiceLoader.load(GameModule.class))
                .extracting(GameModule::id)
                .containsExactly(GenericGameModule.ID);
    }

    @Test
    void shipsATranslationFileForEachLanguage() {
        for (String language : new String[] {"en", "nl", "hu"}) {
            String file = "/i18n/" + GenericGameModule.KEY + "_" + language + ".properties";
            assertThat(getClass().getResource(file)).as(file).isNotNull();
        }
    }
}
