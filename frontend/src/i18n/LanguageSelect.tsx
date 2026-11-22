import { useTranslation } from "react-i18next"
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select"
import { useChangeLanguage } from "@/features/session/useChangeLanguage"
import { currentLanguage, languages, type LanguageCode } from "."

export function LanguageSelect() {
  // useTranslation gives `t`, which looks a text up in the current language, and makes this
  // component redraw when the language changes.
  const { t } = useTranslation()
  const changeLanguage = useChangeLanguage()

  return (
    <NativeSelect
      size="sm"
      aria-label={t("language.label")}
      data-testid="language"
      value={currentLanguage()}
      onChange={(event) => changeLanguage(event.target.value as LanguageCode)}
    >
      {languages.map((language) => (
        <NativeSelectOption key={language.code} value={language.code} lang={language.code}>
          {language.name}
        </NativeSelectOption>
      ))}
    </NativeSelect>
  )
}
