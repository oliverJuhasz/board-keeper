package io.github.oliverjuhasz.boardkeeper.language;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LanguageTests {

    @Test
    void isFoundByItsCode() {
        assertThat(Language.fromCode("hu")).contains(Language.HUNGARIAN);
        assertThat(Language.fromCode("nl")).contains(Language.DUTCH);
    }

    @Test
    void anUnknownCodeGivesNothing() {
        assertThat(Language.fromCode("de")).isEmpty();
        assertThat(Language.fromCode("HU")).isEmpty();
    }
}
