package io.github.oliverjuhasz.boardkeeper.games;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import io.github.oliverjuhasz.boardkeeper.gameapi.GameModule;
import io.github.oliverjuhasz.boardkeeper.gameapi.Outcome;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayEntry;
import io.github.oliverjuhasz.boardkeeper.gameapi.ScorePad;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GameModulesTests {

    // src/test/resources/i18n holds English translation files for the keys "alpha" and "beta".
    private static final UUID ALPHA_ID = UUID.fromString("00000000-0000-4000-8000-00000000000a");
    private static final UUID BETA_ID = UUID.fromString("00000000-0000-4000-8000-00000000000b");

    @Test
    void listsModulesByKey() {
        var modules = new GameModules(List.of(new FakeModule(BETA_ID, "beta"), new FakeModule(ALPHA_ID, "alpha")));

        assertThat(modules.all()).extracting(GameModule::key).containsExactly("alpha", "beta");
    }

    @Test
    void findsAModuleById() {
        var modules = new GameModules(List.of(new FakeModule(ALPHA_ID, "alpha")));

        assertThat(modules.byId(ALPHA_ID)).map(GameModule::key).contains("alpha");
        assertThat(modules.byId(BETA_ID)).isEmpty();
    }

    @Test
    void rejectsTwoModulesWithTheSameId() {
        assertThatIllegalStateException()
                .isThrownBy(() ->
                        new GameModules(List.of(new FakeModule(ALPHA_ID, "alpha"), new FakeModule(ALPHA_ID, "beta"))))
                .withMessageContaining(ALPHA_ID.toString());
    }

    @Test
    void rejectsTwoModulesWithTheSameKey() {
        assertThatIllegalStateException()
                .isThrownBy(() ->
                        new GameModules(List.of(new FakeModule(ALPHA_ID, "alpha"), new FakeModule(BETA_ID, "alpha"))))
                .withMessageContaining("'alpha'");
    }

    @Test
    void rejectsAMalformedKey() {
        for (String key : new String[] {"Alpha", "alpha_beta", "alpha--beta", "-alpha", "alpha beta", ""}) {
            assertThatIllegalStateException()
                    .as(key)
                    .isThrownBy(() -> new GameModules(List.of(new FakeModule(ALPHA_ID, key))))
                    .withMessageContaining("malformed key");
        }
    }

    @Test
    void rejectsAModuleWithoutEnglishTexts() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new GameModules(List.of(new FakeModule(ALPHA_ID, "untranslated"))))
                .withMessageContaining("i18n/untranslated_en.properties");
    }

    /** A Game Module that is only there to be found: nothing here scores a Play with it. */
    private record FakeModule(UUID id, String key) implements GameModule {

        @Override
        public ScorePad scorePad() {
            // An empty pad: nothing on it needs a text.
            return new ScorePad(1, null, List.of(), List.of(), List.of(), false);
        }

        @Override
        public Outcome score(PlayEntry entry) {
            throw new UnsupportedOperationException();
        }
    }
}
