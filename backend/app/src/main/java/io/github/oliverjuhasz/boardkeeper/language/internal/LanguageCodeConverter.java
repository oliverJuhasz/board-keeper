package io.github.oliverjuhasz.boardkeeper.language.internal;

import io.github.oliverjuhasz.boardkeeper.language.Language;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Lets a controller take a {@link Language} straight from an address or a query, as in
 * {@code /texts/hu}. Spring finds this converter because it is a bean; an unknown code is
 * answered with "400 Bad Request".
 */
@Component
class LanguageCodeConverter implements Converter<String, Language> {

    @Override
    public Language convert(String code) {
        return Language.fromCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Unknown language: " + code));
    }
}
