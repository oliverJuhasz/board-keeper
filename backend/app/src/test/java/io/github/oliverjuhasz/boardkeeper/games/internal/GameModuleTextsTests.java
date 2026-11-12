package io.github.oliverjuhasz.boardkeeper.games.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import io.github.oliverjuhasz.boardkeeper.gameapi.GameModule;
import io.github.oliverjuhasz.boardkeeper.gameapi.Outcome;
import io.github.oliverjuhasz.boardkeeper.gameapi.PlayEntry;
import io.github.oliverjuhasz.boardkeeper.games.GameModules;
import io.github.oliverjuhasz.boardkeeper.language.Language;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Uses the translation files for the made-up modules in src/test/resources/i18n. */
class GameModuleTextsTests {

    private static final FakeModule ALPHA = new FakeModule(UUID.fromString("00000000-0000-4000-8000-00000000000a"), "alpha");
    private static final FakeModule BETA = new FakeModule(UUID.fromString("00000000-0000-4000-8000-00000000000b"), "beta");
    private static final FakeModule MISNAMED =
            new FakeModule(UUID.fromString("00000000-0000-4000-8000-00000000000c"), "misnamed");

    @Test
    void givesEveryModulesTextsInTheLanguageAskedFor() {
        var texts = new GameModuleTexts(new GameModules(List.of(ALPHA, BETA)));

        assertThat(texts.in(Language.ENGLISH))
                .containsEntry("alpha.name", "Alpha")
                .containsEntry("alpha.category.points", "Points")
                .containsEntry("beta.name", "Beta");
        assertThat(texts.in(Language.DUTCH)).containsEntry("alpha.name", "Alfa");
    }

    @Test
    void readsTheFilesAsUtf8() {
        var texts = new GameModuleTexts(new GameModules(List.of(ALPHA)));

        assertThat(texts.in(Language.HUNGARIAN)).containsEntry("alpha.name", "Alfa játék").containsEntry("alpha.category.points", "Pontszám");
    }

    @Test
    void fallsBackToEnglishForAMissingText() {
        var texts = new GameModuleTexts(new GameModules(List.of(ALPHA, BETA)));

        // alpha_nl.properties has no "alpha.category.points"; beta has no Dutch file at all.
        assertThat(texts.in(Language.DUTCH))
                .containsEntry("alpha.category.points", "Points")
                .containsEntry("beta.name", "Beta");
    }

    @Test
    void ignoresATranslationThatHasNoEnglishOriginal() {
        var texts = new GameModuleTexts(new GameModules(List.of(ALPHA)));

        // alpha_nl.properties also has "alpha.leftover", which alpha_en.properties does not.
        assertThat(texts.in(Language.DUTCH)).doesNotContainKey("alpha.leftover");
    }

    @Test
    void rejectsATextWhoseNameDoesNotStartWithTheModulesKey() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new GameModuleTexts(new GameModules(List.of(MISNAMED))))
                .withMessageContaining("'other.name'")
                .withMessageContaining("'misnamed.'");
    }

    /** A Game Module that is only there to be found: nothing here scores a Play with it. */
    private record FakeModule(UUID id, String key) implements GameModule {

        @Override
        public Outcome score(PlayEntry entry) {
            throw new UnsupportedOperationException();
        }
    }
}
