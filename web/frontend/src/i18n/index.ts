import dayjs from "dayjs"
import "dayjs/locale/en"
import "dayjs/locale/ru"
import "dayjs/locale/zh-cn"
import localizedFormat from "dayjs/plugin/localizedFormat"
import relativeTime from "dayjs/plugin/relativeTime"
import i18n from "i18next"
import LanguageDetector from "i18next-browser-languagedetector"
import { initReactI18next } from "react-i18next"

import {
  LANGUAGE_STORAGE_KEY,
  SUPPORTED_LANGUAGES,
  getInitialLanguage,
  normalizeLanguage,
} from "./languages"
import en from "./locales/en.json"
import ru from "./locales/ru.json"
import zh from "./locales/zh.json"

dayjs.extend(relativeTime)
dayjs.extend(localizedFormat)

const syncRuntimeLanguage = (language: string) => {
  const normalizedLanguage = normalizeLanguage(language)

  dayjs.locale(normalizedLanguage === "zh" ? "zh-cn" : normalizedLanguage)

  if (typeof document !== "undefined") {
    document.documentElement.lang = normalizedLanguage
  }
}

i18n
  // detect user language
  // learn more: https://github.com/i18next/i18next-browser-languageDetector
  .use(LanguageDetector)
  // pass the i18n instance to react-i18next.
  .use(initReactI18next)
  // init i18next
  // for all options read: https://www.i18next.com/overview/configuration-options
  .init({
    resources: {
      ru: {
        translation: ru,
      },
      en: {
        translation: en,
      },
      zh: {
        translation: zh,
      },
    },
    supportedLngs: [...SUPPORTED_LANGUAGES],
    load: "languageOnly",
    nonExplicitSupportedLngs: true,
    fallbackLng: "ru",
    lng: getInitialLanguage(),
    detection: {
      order: ["localStorage"],
      lookupLocalStorage: LANGUAGE_STORAGE_KEY,
      caches: ["localStorage"],
    },
    debug: false,

    interpolation: {
      escapeValue: false, // not needed for react as it escapes by default
    },
  })

syncRuntimeLanguage(i18n.resolvedLanguage ?? i18n.language)
i18n.on("languageChanged", syncRuntimeLanguage)

export default i18n
