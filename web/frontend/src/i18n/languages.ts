export const LANGUAGE_STORAGE_KEY = "i18nextLng"

export const SUPPORTED_LANGUAGES = ["ru", "en", "zh"] as const

export type SupportedLanguage = (typeof SUPPORTED_LANGUAGES)[number]

export const LANGUAGE_OPTIONS: ReadonlyArray<{
  code: SupportedLanguage
  label: string
}> = [
  { code: "ru", label: "Русский" },
  { code: "en", label: "English" },
  { code: "zh", label: "简体中文" },
]

export function normalizeLanguage(language?: string | null): SupportedLanguage {
  const base = language?.trim().toLowerCase().split("-")[0]
  if (!base) return "ru"

  return SUPPORTED_LANGUAGES.includes(base as SupportedLanguage)
    ? (base as SupportedLanguage)
    : "ru"
}

export function getInitialLanguage(): SupportedLanguage {
  try {
    return normalizeLanguage(
      globalThis.localStorage?.getItem(LANGUAGE_STORAGE_KEY),
    )
  } catch {
    return "ru"
  }
}
