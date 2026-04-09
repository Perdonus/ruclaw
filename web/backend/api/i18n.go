package api

import (
	"fmt"
	"strings"
	"sync/atomic"
)

// Language represents the supported launcher languages.
type Language string

const (
	LanguageRussian Language = "ru"
	LanguageEnglish Language = "en"
	LanguageChinese Language = "zh"
)

// TranslationKey represents a launcher/backend translation key.
type TranslationKey string

const (
	AppTooltip                  TranslationKey = "AppTooltip"
	MenuOpen                    TranslationKey = "MenuOpen"
	MenuOpenTooltip             TranslationKey = "MenuOpenTooltip"
	MenuAbout                   TranslationKey = "MenuAbout"
	MenuAboutTooltip            TranslationKey = "MenuAboutTooltip"
	MenuVersion                 TranslationKey = "MenuVersion"
	MenuVersionTooltip          TranslationKey = "MenuVersionTooltip"
	MenuGitHub                  TranslationKey = "MenuGitHub"
	MenuDocs                    TranslationKey = "MenuDocs"
	MenuRestart                 TranslationKey = "MenuRestart"
	MenuRestartTooltip          TranslationKey = "MenuRestartTooltip"
	MenuQuit                    TranslationKey = "MenuQuit"
	MenuQuitTooltip             TranslationKey = "MenuQuitTooltip"
	Exiting                     TranslationKey = "Exiting"
	DocUrl                      TranslationKey = "DocUrl"
	FlagPort                    TranslationKey = "FlagPort"
	FlagPublic                  TranslationKey = "FlagPublic"
	FlagNoBrowser               TranslationKey = "FlagNoBrowser"
	FlagLang                    TranslationKey = "FlagLang"
	FlagConsole                 TranslationKey = "FlagConsole"
	FlagDebug                   TranslationKey = "FlagDebug"
	UsageTitle                  TranslationKey = "UsageTitle"
	UsageLine                   TranslationKey = "UsageLine"
	UsageArguments              TranslationKey = "UsageArguments"
	UsageConfigArgument         TranslationKey = "UsageConfigArgument"
	UsageOptions                TranslationKey = "UsageOptions"
	UsageExamples               TranslationKey = "UsageExamples"
	ExampleDefaultMode          TranslationKey = "ExampleDefaultMode"
	ExampleConfigFile           TranslationKey = "ExampleConfigFile"
	ExamplePublicAccess         TranslationKey = "ExamplePublicAccess"
	ExampleConsoleDebug         TranslationKey = "ExampleConsoleDebug"
	OpenBrowserPrompt           TranslationKey = "OpenBrowserPrompt"
	DashboardPasswordRuntime    TranslationKey = "DashboardPasswordRuntime"
	DashboardPasswordEnv        TranslationKey = "DashboardPasswordEnv"
	DashboardPasswordConfig     TranslationKey = "DashboardPasswordConfig"
	DebugModeEnabled            TranslationKey = "DebugModeEnabled"
	LauncherStarting            TranslationKey = "LauncherStarting"
	LauncherHome                TranslationKey = "LauncherHome"
	LauncherFlags               TranslationKey = "LauncherFlags"
	ResolveConfigPathFailed     TranslationKey = "ResolveConfigPathFailed"
	AutoInitConfigFailed        TranslationKey = "AutoInitConfigFailed"
	EnsurePicoChannelFailed     TranslationKey = "EnsurePicoChannelFailed"
	LoadLauncherConfigFailed    TranslationKey = "LoadLauncherConfigFailed"
	InvalidPort                 TranslationKey = "InvalidPort"
	DashboardAuthSetupFailed    TranslationKey = "DashboardAuthSetupFailed"
	OpenAuthStoreFailed         TranslationKey = "OpenAuthStoreFailed"
	InvalidAllowedCIDR          TranslationKey = "InvalidAllowedCIDR"
	LocalServerAddress          TranslationKey = "LocalServerAddress"
	PublicServerAddress         TranslationKey = "PublicServerAddress"
	ServerListening             TranslationKey = "ServerListening"
	ServerStartFailed           TranslationKey = "ServerStartFailed"
	AutoOpenBrowserFailed       TranslationKey = "AutoOpenBrowserFailed"
	ShuttingDown                TranslationKey = "ShuttingDown"
	OpenBrowserFailed           TranslationKey = "OpenBrowserFailed"
	OpenGitHubFailed            TranslationKey = "OpenGitHubFailed"
	OpenDocsFailed              TranslationKey = "OpenDocsFailed"
	RestartRequestReceived      TranslationKey = "RestartRequestReceived"
	RestartGatewayFailed        TranslationKey = "RestartGatewayFailed"
	GatewayRestarted            TranslationKey = "GatewayRestarted"
	SystemTrayUnavailableNoCGO  TranslationKey = "SystemTrayUnavailableNoCGO"
	AuthPasswordStoreUnavailable TranslationKey = "AuthPasswordStoreUnavailable"
	AuthInvalidJSON             TranslationKey = "AuthInvalidJSON"
	AuthTooManyLoginAttempts    TranslationKey = "AuthTooManyLoginAttempts"
	AuthPasswordVerifyFailed    TranslationKey = "AuthPasswordVerifyFailed"
	AuthInvalidPassword         TranslationKey = "AuthInvalidPassword"
	AuthMethodNotAllowed        TranslationKey = "AuthMethodNotAllowed"
	AuthContentTypeJSONRequired TranslationKey = "AuthContentTypeJSONRequired"
	AuthInvalidJSONBody         TranslationKey = "AuthInvalidJSONBody"
	AuthMarshalResponseFailed   TranslationKey = "AuthMarshalResponseFailed"
	AuthPasswordStoreNotConfigured TranslationKey = "AuthPasswordStoreNotConfigured"
	AuthMustAuthenticateToChange   TranslationKey = "AuthMustAuthenticateToChange"
	AuthPasswordEmpty           TranslationKey = "AuthPasswordEmpty"
	AuthPasswordsDoNotMatch     TranslationKey = "AuthPasswordsDoNotMatch"
	AuthPasswordTooShort        TranslationKey = "AuthPasswordTooShort"
	AuthSavePasswordFailed      TranslationKey = "AuthSavePasswordFailed"
	StartupReadFailed           TranslationKey = "StartupReadFailed"
	StartupInvalidJSON          TranslationKey = "StartupInvalidJSON"
	StartupUpdateFailed         TranslationKey = "StartupUpdateFailed"
	StartupVerifyFailed         TranslationKey = "StartupVerifyFailed"
	StartupApplyNextLogin       TranslationKey = "StartupApplyNextLogin"
	StartupUnsupportedPlatform  TranslationKey = "StartupUnsupportedPlatform"
	StartupAutoStartUnsupported TranslationKey = "StartupAutoStartUnsupported"
	StartupDesktopComment       TranslationKey = "StartupDesktopComment"
	LauncherConfigReadFailed    TranslationKey = "LauncherConfigReadFailed"
	LauncherConfigInvalidJSON   TranslationKey = "LauncherConfigInvalidJSON"
	LauncherConfigSaveFailed    TranslationKey = "LauncherConfigSaveFailed"
	PicoProxyFailed             TranslationKey = "PicoProxyFailed"
	PicoGatewayUnavailable      TranslationKey = "PicoGatewayUnavailable"
	PicoGatewayNotAvailable     TranslationKey = "PicoGatewayNotAvailable"
	PicoInvalidToken            TranslationKey = "PicoInvalidToken"
	PicoConfigLoadFailed        TranslationKey = "PicoConfigLoadFailed"
	PicoConfigSaveFailed        TranslationKey = "PicoConfigSaveFailed"
	PicoEnsureConfigLoadFailed  TranslationKey = "PicoEnsureConfigLoadFailed"
	PicoEnsureConfigSaveFailed  TranslationKey = "PicoEnsureConfigSaveFailed"
	OAuthLoadCredentialsFailed  TranslationKey = "OAuthLoadCredentialsFailed"
	OAuthReadRequestFailed      TranslationKey = "OAuthReadRequestFailed"
	OAuthInvalidJSON            TranslationKey = "OAuthInvalidJSON"
	OAuthUnsupportedProvider    TranslationKey = "OAuthUnsupportedProvider"
	OAuthUnsupportedMethodForProvider TranslationKey = "OAuthUnsupportedMethodForProvider"
	OAuthTokenRequired          TranslationKey = "OAuthTokenRequired"
	OAuthTokenLoginFailed       TranslationKey = "OAuthTokenLoginFailed"
	OAuthDeviceCodeRequestFailed TranslationKey = "OAuthDeviceCodeRequestFailed"
	OAuthGeneratePKCEFailed     TranslationKey = "OAuthGeneratePKCEFailed"
	OAuthGenerateStateFailed    TranslationKey = "OAuthGenerateStateFailed"
	OAuthUnsupportedMethod      TranslationKey = "OAuthUnsupportedMethod"
	OAuthMissingFlowID          TranslationKey = "OAuthMissingFlowID"
	OAuthFlowNotFound           TranslationKey = "OAuthFlowNotFound"
	OAuthFlowNoPolling          TranslationKey = "OAuthFlowNoPolling"
	OAuthDeviceCodePollFailed   TranslationKey = "OAuthDeviceCodePollFailed"
	OAuthSaveCredentialFailed   TranslationKey = "OAuthSaveCredentialFailed"
	OAuthDeleteCredentialFailed TranslationKey = "OAuthDeleteCredentialFailed"
	OAuthUpdateConfigFailed     TranslationKey = "OAuthUpdateConfigFailed"
	OAuthCallbackMissingStateTitle TranslationKey = "OAuthCallbackMissingStateTitle"
	OAuthCallbackFlowNotFoundTitle TranslationKey = "OAuthCallbackFlowNotFoundTitle"
	OAuthCallbackAlreadyCompletedTitle TranslationKey = "OAuthCallbackAlreadyCompletedTitle"
	OAuthCallbackAuthorizationFailedTitle TranslationKey = "OAuthCallbackAuthorizationFailedTitle"
	OAuthCallbackMissingCodeTitle TranslationKey = "OAuthCallbackMissingCodeTitle"
	OAuthCallbackUnsupportedProviderTitle TranslationKey = "OAuthCallbackUnsupportedProviderTitle"
	OAuthCallbackTokenExchangeFailedTitle TranslationKey = "OAuthCallbackTokenExchangeFailedTitle"
	OAuthCallbackSaveCredentialFailedTitle TranslationKey = "OAuthCallbackSaveCredentialFailedTitle"
	OAuthCallbackSuccessTitle    TranslationKey = "OAuthCallbackSuccessTitle"
	OAuthCallbackCloseWindow     TranslationKey = "OAuthCallbackCloseWindow"
	OAuthCallbackWindowTitle     TranslationKey = "OAuthCallbackWindowTitle"
	OAuthProviderNoBrowser       TranslationKey = "OAuthProviderNoBrowser"
	OAuthFlowExpired            TranslationKey = "OAuthFlowExpired"
	OAuthEmptyCredential        TranslationKey = "OAuthEmptyCredential"
	OAuthPersistCredentialFailed TranslationKey = "OAuthPersistCredentialFailed"
	OAuthSyncProviderConfigFailed TranslationKey = "OAuthSyncProviderConfigFailed"
	FlowMissingID               TranslationKey = "FlowMissingID"
	FlowNotFound               TranslationKey = "FlowNotFound"
	QRCodeFetchFailed          TranslationKey = "QRCodeFetchFailed"
	QRImageGenerateFailed      TranslationKey = "QRImageGenerateFailed"
	WeixinCreateClientFailed   TranslationKey = "WeixinCreateClientFailed"
	WeixinClientFailed         TranslationKey = "WeixinClientFailed"
	WeixinMissingBotToken      TranslationKey = "WeixinMissingBotToken"
	WeixinSaveTokenFailed      TranslationKey = "WeixinSaveTokenFailed"
	WecomMissingBotCredentials TranslationKey = "WecomMissingBotCredentials"
	WecomSaveCredentialsFailed TranslationKey = "WecomSaveCredentialsFailed"
	WecomInvalidGenerateURL    TranslationKey = "WecomInvalidGenerateURL"
	WecomInvalidQueryURL       TranslationKey = "WecomInvalidQueryURL"
	WecomMissingResponseFields TranslationKey = "WecomMissingResponseFields"
)

var currentLauncherLanguage atomic.Value

func init() {
	currentLauncherLanguage.Store(LanguageRussian)
}

var launcherTranslations = map[Language]map[TranslationKey]string{
	LanguageRussian: {
		AppTooltip:                  "%s - веб-консоль",
		MenuOpen:                    "Открыть консоль",
		MenuOpenTooltip:             "Открыть консоль RuClaw в браузере",
		MenuAbout:                   "О программе",
		MenuAboutTooltip:            "О RuClaw",
		MenuVersion:                 "Версия: %s",
		MenuVersionTooltip:          "Текущая версия",
		MenuGitHub:                  "GitHub",
		MenuDocs:                    "Документация",
		MenuRestart:                 "Перезапустить сервис",
		MenuRestartTooltip:          "Перезапустить сервис шлюза",
		MenuQuit:                    "Выход",
		MenuQuitTooltip:             "Закрыть RuClaw",
		Exiting:                     "Выход из RuClaw...",
		DocUrl:                      "https://github.com/Perdonus/ruclaw/tree/main/docs",
		FlagPort:                    "Порт для прослушивания",
		FlagPublic:                  "Слушать на всех интерфейсах (0.0.0.0), а не только на localhost",
		FlagNoBrowser:               "Не открывать браузер автоматически при запуске",
		FlagLang:                    "Язык: ru (русский), en (английский) или zh (китайский). По умолчанию: ru",
		FlagConsole:                 "Консольный режим, без GUI",
		FlagDebug:                   "Включить подробные логи",
		UsageTitle:                  "%s Launcher - веб-консоль и менеджер шлюза",
		UsageLine:                   "Использование: %s [опции] [config.json]",
		UsageArguments:              "Аргументы:",
		UsageConfigArgument:         "  config.json    Путь к файлу конфигурации (по умолчанию: ~/.ruclaw/config.json)",
		UsageOptions:                "Опции:",
		UsageExamples:               "Примеры:",
		ExampleDefaultMode:          "      Использовать путь конфигурации по умолчанию в GUI-режиме",
		ExampleConfigFile:           "      Указать файл конфигурации",
		ExamplePublicAccess:         "      Разрешить доступ с других устройств в локальной сети",
		ExampleConsoleDebug:         "      Запустить в терминале с включёнными debug-логами",
		OpenBrowserPrompt:           "Откройте в браузере один из следующих адресов:",
		DashboardPasswordRuntime:    "Пароль панели управления (текущий запуск): %s",
		DashboardPasswordEnv:        "Пароль панели управления: из переменной окружения PICOCLAW_LAUNCHER_TOKEN",
		DashboardPasswordConfig:     "Пароль панели управления: задан в %s",
		DebugModeEnabled:            "Включён режим отладки",
		LauncherStarting:            "%s launcher запускается (версия %s)...",
		LauncherHome:                "%s Home: %s",
		LauncherFlags:               "Флаги launcher: console=%t public=%t no_browser=%t config=%s",
		ResolveConfigPathFailed:     "Не удалось определить путь к конфигу: %v",
		AutoInitConfigFailed:        "Предупреждение: не удалось автоматически инициализировать конфиг %s: %v",
		EnsurePicoChannelFailed:     "Предупреждение: не удалось подготовить pico channel при запуске: %v",
		LoadLauncherConfigFailed:    "Предупреждение: не удалось загрузить %s: %v",
		InvalidPort:                 "Некорректный порт %q: %v",
		DashboardAuthSetupFailed:    "Не удалось настроить авторизацию панели: %v",
		OpenAuthStoreFailed:         "Предупреждение: не удалось открыть хранилище паролей: %v",
		InvalidAllowedCIDR:          "Некорректная конфигурация allowed CIDR: %v",
		LocalServerAddress:          "Сервер будет доступен на http://localhost:%s",
		PublicServerAddress:         "Публичный доступ включён: http://%s:%s",
		ServerListening:             "Сервер слушает %s",
		ServerStartFailed:           "Не удалось запустить сервер: %v",
		AutoOpenBrowserFailed:       "Предупреждение: не удалось автоматически открыть браузер: %v",
		ShuttingDown:                "Завершение работы...",
		OpenBrowserFailed:           "Не удалось открыть браузер: %v",
		OpenGitHubFailed:            "Не удалось открыть GitHub: %v",
		OpenDocsFailed:              "Не удалось открыть документацию: %v",
		RestartRequestReceived:      "Получен запрос на перезапуск...",
		RestartGatewayFailed:        "Не удалось перезапустить шлюз: %v",
		GatewayRestarted:            "Шлюз перезапущен (PID: %d)",
		SystemTrayUnavailableNoCGO:  "Системный трей недоступен в сборке %s без cgo; запуск без трея",
		AuthPasswordStoreUnavailable: "Хранилище паролей недоступно (%v); для восстановления остановите приложение, удалите файл базы данных и запустите его снова",
		AuthInvalidJSON:             "Некорректный JSON",
		AuthTooManyLoginAttempts:    "Слишком много попыток входа",
		AuthPasswordVerifyFailed:    "Не удалось проверить пароль: %v",
		AuthInvalidPassword:         "Неверный пароль",
		AuthMethodNotAllowed:        "Метод не поддерживается",
		AuthContentTypeJSONRequired: "Content-Type должен быть application/json",
		AuthInvalidJSONBody:         "Некорректное JSON-тело запроса",
		AuthMarshalResponseFailed:   "Не удалось сформировать ответ: %v",
		AuthPasswordStoreNotConfigured: "Хранилище паролей не настроено",
		AuthMustAuthenticateToChange:   "Для смены пароля нужно войти в систему",
		AuthPasswordEmpty:           "Пароль не должен быть пустым",
		AuthPasswordsDoNotMatch:     "Пароли не совпадают",
		AuthPasswordTooShort:        "Пароль должен содержать минимум 8 символов",
		AuthSavePasswordFailed:      "Не удалось сохранить пароль: %v",
		StartupReadFailed:           "Не удалось прочитать настройку автозапуска: %v",
		StartupInvalidJSON:          "Некорректный JSON: %v",
		StartupUpdateFailed:         "Не удалось обновить настройку автозапуска: %v",
		StartupVerifyFailed:         "Не удалось проверить настройку автозапуска: %v",
		StartupApplyNextLogin:       "Изменения вступят в силу при следующем входе в систему.",
		StartupUnsupportedPlatform:  "Текущая платформа не поддерживает запуск при входе в систему.",
		StartupAutoStartUnsupported: "Автозапуск на этой платформе не поддерживается",
		StartupDesktopComment:       "Запускать RuClaw Web при входе в систему",
		LauncherConfigReadFailed:    "Не удалось загрузить конфиг launcher: %v",
		LauncherConfigInvalidJSON:   "Некорректный JSON: %v",
		LauncherConfigSaveFailed:    "Не удалось сохранить конфиг launcher: %v",
		PicoProxyFailed:             "Шлюз недоступен: %v",
		PicoGatewayUnavailable:      "Шлюз недоступен для проксирования WebSocket",
		PicoGatewayNotAvailable:     "Шлюз недоступен",
		PicoInvalidToken:            "Некорректный Pico token",
		PicoConfigLoadFailed:        "Не удалось загрузить конфиг: %v",
		PicoConfigSaveFailed:        "Не удалось сохранить конфиг: %v",
		PicoEnsureConfigLoadFailed:  "не удалось загрузить конфиг: %w",
		PicoEnsureConfigSaveFailed:  "не удалось сохранить конфиг: %w",
		OAuthLoadCredentialsFailed:  "Не удалось загрузить учётные данные: %v",
		OAuthReadRequestFailed:      "Не удалось прочитать тело запроса",
		OAuthInvalidJSON:            "Некорректный JSON: %v",
		OAuthUnsupportedProvider:    "Провайдер %q не поддерживается",
		OAuthUnsupportedMethodForProvider: "Метод входа %q не поддерживается для провайдера %q",
		OAuthTokenRequired:          "Нужно указать token",
		OAuthTokenLoginFailed:       "Не удалось войти по token: %v",
		OAuthDeviceCodeRequestFailed: "Не удалось запросить device code: %v",
		OAuthGeneratePKCEFailed:     "Не удалось сгенерировать PKCE: %v",
		OAuthGenerateStateFailed:    "Не удалось сгенерировать state: %v",
		OAuthUnsupportedMethod:      "Метод входа не поддерживается",
		OAuthMissingFlowID:          "Не указан идентификатор flow",
		OAuthFlowNotFound:           "Flow не найден",
		OAuthFlowNoPolling:          "Для этого flow опрос не поддерживается",
		OAuthDeviceCodePollFailed:   "Не удалось опросить device code: %v",
		OAuthSaveCredentialFailed:   "Не удалось сохранить учётные данные: %v",
		OAuthDeleteCredentialFailed: "Не удалось удалить учётные данные: %v",
		OAuthUpdateConfigFailed:     "Не удалось обновить конфиг: %v",
		OAuthCallbackMissingStateTitle: "Отсутствует state",
		OAuthCallbackFlowNotFoundTitle: "OAuth flow не найден",
		OAuthCallbackAlreadyCompletedTitle: "Flow уже завершён",
		OAuthCallbackAuthorizationFailedTitle: "Авторизация не удалась",
		OAuthCallbackMissingCodeTitle: "Отсутствует код авторизации",
		OAuthCallbackUnsupportedProviderTitle: "Неподдерживаемый провайдер",
		OAuthCallbackTokenExchangeFailedTitle: "Не удалось обменять код на token",
		OAuthCallbackSaveCredentialFailedTitle: "Не удалось сохранить учётные данные",
		OAuthCallbackSuccessTitle:    "Аутентификация успешно завершена",
		OAuthCallbackCloseWindow:     "Это окно можно закрыть.",
		OAuthCallbackWindowTitle:     "RuClaw OAuth",
		OAuthProviderNoBrowser:       "Провайдер %q не поддерживает browser oauth",
		OAuthFlowExpired:             "Срок действия flow истёк",
		OAuthEmptyCredential:         "Пустые учётные данные",
		OAuthPersistCredentialFailed: "сохранение учётных данных: %w",
		OAuthSyncProviderConfigFailed: "синхронизация auth-конфига провайдера: %w",
		FlowMissingID:                "Не указан идентификатор flow",
		FlowNotFound:                 "Flow не найден",
		QRCodeFetchFailed:            "Не удалось получить QR-код: %v",
		QRImageGenerateFailed:        "Не удалось сгенерировать изображение QR-кода: %v",
		WeixinCreateClientFailed:     "Не удалось создать клиент Weixin: %v",
		WeixinClientFailed:           "Ошибка клиента: %v",
		WeixinMissingBotToken:        "Вход подтверждён, но bot_token отсутствует",
		WeixinSaveTokenFailed:        "Не удалось сохранить token: %v",
		WecomMissingBotCredentials:   "Вход подтверждён, но отсутствуют данные бота",
		WecomSaveCredentialsFailed:   "Не удалось сохранить данные: %v",
		WecomInvalidGenerateURL:      "Некорректный URL генерации WeCom QR: %w",
		WecomInvalidQueryURL:         "Некорректный URL запроса WeCom QR: %w",
		WecomMissingResponseFields:   "В ответе отсутствуют scode или auth_url",
	},
	LanguageEnglish: {
		AppTooltip:                  "%s - Web Console",
		MenuOpen:                    "Open Console",
		MenuOpenTooltip:             "Open RuClaw console in browser",
		MenuAbout:                   "About",
		MenuAboutTooltip:            "About RuClaw",
		MenuVersion:                 "Version: %s",
		MenuVersionTooltip:          "Current version number",
		MenuGitHub:                  "GitHub",
		MenuDocs:                    "Documentation",
		MenuRestart:                 "Restart Service",
		MenuRestartTooltip:          "Restart Gateway service",
		MenuQuit:                    "Quit",
		MenuQuitTooltip:             "Exit RuClaw",
		Exiting:                     "Exiting RuClaw...",
		DocUrl:                      "https://github.com/Perdonus/ruclaw/tree/main/docs",
		FlagPort:                    "Port to listen on",
		FlagPublic:                  "Listen on all interfaces (0.0.0.0) instead of localhost only",
		FlagNoBrowser:               "Do not auto-open browser on startup",
		FlagLang:                    "Language: ru (Russian), en (English) or zh (Chinese). Default: ru",
		FlagConsole:                 "Console mode, no GUI",
		FlagDebug:                   "Enable debug logging",
		UsageTitle:                  "%s Launcher - Web console and gateway manager",
		UsageLine:                   "Usage: %s [options] [config.json]",
		UsageArguments:              "Arguments:",
		UsageConfigArgument:         "  config.json    Path to the configuration file (default: ~/.ruclaw/config.json)",
		UsageOptions:                "Options:",
		UsageExamples:               "Examples:",
		ExampleDefaultMode:          "      Use default config path in GUI mode",
		ExampleConfigFile:           "      Specify a config file",
		ExamplePublicAccess:         "      Allow access from other devices on the local network",
		ExampleConsoleDebug:         "      Run in the terminal with debug logs enabled",
		OpenBrowserPrompt:           "Open one of the following URLs in your browser:",
		DashboardPasswordRuntime:    "Dashboard password (this run): %s",
		DashboardPasswordEnv:        "Dashboard password: from environment variable PICOCLAW_LAUNCHER_TOKEN",
		DashboardPasswordConfig:     "Dashboard password: configured in %s",
		DebugModeEnabled:            "Debug mode enabled",
		LauncherStarting:            "%s launcher starting (version %s)...",
		LauncherHome:                "%s Home: %s",
		LauncherFlags:               "Launcher flags: console=%t public=%t no_browser=%t config=%s",
		ResolveConfigPathFailed:     "Failed to resolve config path: %v",
		AutoInitConfigFailed:        "Warning: Failed to initialize %s config automatically: %v",
		EnsurePicoChannelFailed:     "Warning: failed to ensure pico channel on startup: %v",
		LoadLauncherConfigFailed:    "Warning: Failed to load %s: %v",
		InvalidPort:                 "Invalid port %q: %v",
		DashboardAuthSetupFailed:    "Dashboard auth setup failed: %v",
		OpenAuthStoreFailed:         "Warning: could not open auth store: %v",
		InvalidAllowedCIDR:          "Invalid allowed CIDR configuration: %v",
		LocalServerAddress:          "Server will listen on http://localhost:%s",
		PublicServerAddress:         "Public access enabled at http://%s:%s",
		ServerListening:             "Server listening on %s",
		ServerStartFailed:           "Server failed to start: %v",
		AutoOpenBrowserFailed:       "Warning: Failed to auto-open browser: %v",
		ShuttingDown:                "Shutting down...",
		OpenBrowserFailed:           "Failed to open browser: %v",
		OpenGitHubFailed:            "Failed to open GitHub: %v",
		OpenDocsFailed:              "Failed to open docs: %v",
		RestartRequestReceived:      "Restart request received...",
		RestartGatewayFailed:        "Failed to restart gateway: %v",
		GatewayRestarted:            "Gateway restarted (PID: %d)",
		SystemTrayUnavailableNoCGO:  "System tray is unavailable in %s builds without cgo; running without tray",
		AuthPasswordStoreUnavailable: "password store unavailable (%v); to recover, stop the application, delete the database file and restart it",
		AuthInvalidJSON:             "invalid JSON",
		AuthTooManyLoginAttempts:    "too many login attempts",
		AuthPasswordVerifyFailed:    "password verification failed: %v",
		AuthInvalidPassword:         "invalid password",
		AuthMethodNotAllowed:        "method not allowed",
		AuthContentTypeJSONRequired: "Content-Type must be application/json",
		AuthInvalidJSONBody:         "invalid JSON body",
		AuthMarshalResponseFailed:   "marshal response failed: %v",
		AuthPasswordStoreNotConfigured: "password store not configured",
		AuthMustAuthenticateToChange:   "must be authenticated to change password",
		AuthPasswordEmpty:           "password must not be empty",
		AuthPasswordsDoNotMatch:     "passwords do not match",
		AuthPasswordTooShort:        "password must be at least 8 characters",
		AuthSavePasswordFailed:      "failed to save password: %v",
		StartupReadFailed:           "Failed to read startup setting: %v",
		StartupInvalidJSON:          "Invalid JSON: %v",
		StartupUpdateFailed:         "Failed to update startup setting: %v",
		StartupVerifyFailed:         "Failed to verify startup setting: %v",
		StartupApplyNextLogin:       "Changes apply on next login.",
		StartupUnsupportedPlatform:  "Current platform does not support launch at login.",
		StartupAutoStartUnsupported: "autostart is not supported on this platform",
		StartupDesktopComment:       "Start RuClaw Web on login",
		LauncherConfigReadFailed:    "Failed to load launcher config: %v",
		LauncherConfigInvalidJSON:   "Invalid JSON: %v",
		LauncherConfigSaveFailed:    "Failed to save launcher config: %v",
		PicoProxyFailed:             "Gateway unavailable: %v",
		PicoGatewayUnavailable:      "Gateway not available for WebSocket proxy",
		PicoGatewayNotAvailable:     "Gateway not available",
		PicoInvalidToken:            "Invalid Pico token",
		PicoConfigLoadFailed:        "Failed to load config: %v",
		PicoConfigSaveFailed:        "Failed to save config: %v",
		PicoEnsureConfigLoadFailed:  "failed to load config: %w",
		PicoEnsureConfigSaveFailed:  "failed to save config: %w",
		OAuthLoadCredentialsFailed:  "failed to load credentials: %v",
		OAuthReadRequestFailed:      "failed to read request body",
		OAuthInvalidJSON:            "invalid JSON: %v",
		OAuthUnsupportedProvider:    "unsupported provider %q",
		OAuthUnsupportedMethodForProvider: "unsupported login method %q for provider %q",
		OAuthTokenRequired:          "token is required",
		OAuthTokenLoginFailed:       "token login failed: %v",
		OAuthDeviceCodeRequestFailed: "failed to request device code: %v",
		OAuthGeneratePKCEFailed:     "failed to generate PKCE: %v",
		OAuthGenerateStateFailed:    "failed to generate state: %v",
		OAuthUnsupportedMethod:      "unsupported login method",
		OAuthMissingFlowID:          "missing flow id",
		OAuthFlowNotFound:           "flow not found",
		OAuthFlowNoPolling:          "flow does not support polling",
		OAuthDeviceCodePollFailed:   "device code poll failed: %v",
		OAuthSaveCredentialFailed:   "failed to save credential: %v",
		OAuthDeleteCredentialFailed: "failed to delete credential: %v",
		OAuthUpdateConfigFailed:     "failed to update config: %v",
		OAuthCallbackMissingStateTitle: "Missing state",
		OAuthCallbackFlowNotFoundTitle: "OAuth flow not found",
		OAuthCallbackAlreadyCompletedTitle: "Flow already completed",
		OAuthCallbackAuthorizationFailedTitle: "Authorization failed",
		OAuthCallbackMissingCodeTitle: "Missing authorization code",
		OAuthCallbackUnsupportedProviderTitle: "Unsupported provider",
		OAuthCallbackTokenExchangeFailedTitle: "Token exchange failed",
		OAuthCallbackSaveCredentialFailedTitle: "Failed to save credential",
		OAuthCallbackSuccessTitle:    "Authentication successful",
		OAuthCallbackCloseWindow:     "You can close this window.",
		OAuthCallbackWindowTitle:     "RuClaw OAuth",
		OAuthProviderNoBrowser:       "provider %q does not support browser oauth",
		OAuthFlowExpired:             "flow expired",
		OAuthEmptyCredential:         "empty credential",
		OAuthPersistCredentialFailed: "saving credential: %w",
		OAuthSyncProviderConfigFailed: "syncing provider auth config: %w",
		FlowMissingID:                "missing flow id",
		FlowNotFound:                 "flow not found",
		QRCodeFetchFailed:            "failed to get QR code: %v",
		QRImageGenerateFailed:        "failed to generate QR image: %v",
		WeixinCreateClientFailed:     "failed to create weixin client: %v",
		WeixinClientFailed:           "client error: %v",
		WeixinMissingBotToken:        "login confirmed but missing bot_token",
		WeixinSaveTokenFailed:        "failed to save token: %v",
		WecomMissingBotCredentials:   "login confirmed but missing bot credentials",
		WecomSaveCredentialsFailed:   "failed to save credentials: %v",
		WecomInvalidGenerateURL:      "invalid WeCom QR generate URL: %w",
		WecomInvalidQueryURL:         "invalid WeCom QR query URL: %w",
		WecomMissingResponseFields:   "response missing scode or auth_url",
	},
	LanguageChinese: {
		AppTooltip:                  "%s - Web Console",
		MenuOpen:                    "打开控制台",
		MenuOpenTooltip:             "在浏览器中打开 RuClaw 控制台",
		MenuAbout:                   "关于",
		MenuAboutTooltip:            "关于 RuClaw",
		MenuVersion:                 "版本: %s",
		MenuVersionTooltip:          "当前版本号",
		MenuGitHub:                  "GitHub",
		MenuDocs:                    "文档",
		MenuRestart:                 "重启服务",
		MenuRestartTooltip:          "重启核心服务",
		MenuQuit:                    "退出",
		MenuQuitTooltip:             "退出 RuClaw",
		Exiting:                     "正在退出 RuClaw...",
		DocUrl:                      "https://github.com/Perdonus/ruclaw/tree/main/docs",
		FlagPort:                    "监听端口",
		FlagPublic:                  "监听所有网卡 (0.0.0.0)，而不只是 localhost",
		FlagNoBrowser:               "启动时不要自动打开浏览器",
		FlagLang:                    "语言: ru (俄语), en (英语) 或 zh (中文)。默认: ru",
		FlagConsole:                 "控制台模式，无 GUI",
		FlagDebug:                   "启用调试日志",
		UsageTitle:                  "%s Launcher - Web 控制台和网关管理器",
		UsageLine:                   "用法: %s [选项] [config.json]",
		UsageArguments:              "参数:",
		UsageConfigArgument:         "  config.json    配置文件路径 (默认: ~/.ruclaw/config.json)",
		UsageOptions:                "选项:",
		UsageExamples:               "示例:",
		ExampleDefaultMode:          "      在 GUI 模式下使用默认配置路径",
		ExampleConfigFile:           "      指定配置文件",
		ExamplePublicAccess:         "      允许局域网中的其他设备访问",
		ExampleConsoleDebug:         "      在终端中运行并启用调试日志",
		OpenBrowserPrompt:           "请在浏览器中打开以下地址之一:",
		DashboardPasswordRuntime:    "控制台密码 (本次运行): %s",
		DashboardPasswordEnv:        "控制台密码: 来自环境变量 PICOCLAW_LAUNCHER_TOKEN",
		DashboardPasswordConfig:     "控制台密码: 已配置在 %s 中",
		DebugModeEnabled:            "调试模式已启用",
		LauncherStarting:            "%s launcher 正在启动 (版本 %s)...",
		LauncherHome:                "%s Home: %s",
		LauncherFlags:               "Launcher 参数: console=%t public=%t no_browser=%t config=%s",
		ResolveConfigPathFailed:     "无法解析配置路径: %v",
		AutoInitConfigFailed:        "警告: 无法自动初始化 %s 配置: %v",
		EnsurePicoChannelFailed:     "警告: 启动时无法确保 pico channel: %v",
		LoadLauncherConfigFailed:    "警告: 无法加载 %s: %v",
		InvalidPort:                 "无效端口 %q: %v",
		DashboardAuthSetupFailed:    "控制台认证设置失败: %v",
		OpenAuthStoreFailed:         "警告: 无法打开认证存储: %v",
		InvalidAllowedCIDR:          "无效的 allowed CIDR 配置: %v",
		LocalServerAddress:          "服务器将监听 http://localhost:%s",
		PublicServerAddress:         "已启用公网访问: http://%s:%s",
		ServerListening:             "服务器正在监听 %s",
		ServerStartFailed:           "服务器启动失败: %v",
		AutoOpenBrowserFailed:       "警告: 无法自动打开浏览器: %v",
		ShuttingDown:                "正在关闭...",
		OpenBrowserFailed:           "无法打开浏览器: %v",
		OpenGitHubFailed:            "无法打开 GitHub: %v",
		OpenDocsFailed:              "无法打开文档: %v",
		RestartRequestReceived:      "收到重启请求...",
		RestartGatewayFailed:        "无法重启网关: %v",
		GatewayRestarted:            "网关已重启 (PID: %d)",
		SystemTrayUnavailableNoCGO:  "在未启用 cgo 的 %s 构建中系统托盘不可用；将以无托盘模式运行",
		AuthPasswordStoreUnavailable: "密码存储不可用 (%v)；如需恢复，请停止应用、删除数据库文件并重新启动",
		AuthInvalidJSON:             "无效 JSON",
		AuthTooManyLoginAttempts:    "登录尝试次数过多",
		AuthPasswordVerifyFailed:    "密码验证失败: %v",
		AuthInvalidPassword:         "密码无效",
		AuthMethodNotAllowed:        "不支持该方法",
		AuthContentTypeJSONRequired: "Content-Type 必须是 application/json",
		AuthInvalidJSONBody:         "无效的 JSON 请求体",
		AuthMarshalResponseFailed:   "响应序列化失败: %v",
		AuthPasswordStoreNotConfigured: "密码存储未配置",
		AuthMustAuthenticateToChange:   "修改密码前必须先登录",
		AuthPasswordEmpty:           "密码不能为空",
		AuthPasswordsDoNotMatch:     "两次输入的密码不一致",
		AuthPasswordTooShort:        "密码至少需要 8 个字符",
		AuthSavePasswordFailed:      "保存密码失败: %v",
		StartupReadFailed:           "无法读取自启动设置: %v",
		StartupInvalidJSON:          "无效 JSON: %v",
		StartupUpdateFailed:         "无法更新自启动设置: %v",
		StartupVerifyFailed:         "无法验证自启动设置: %v",
		StartupApplyNextLogin:       "更改将在下次登录后生效。",
		StartupUnsupportedPlatform:  "当前平台不支持登录时启动。",
		StartupAutoStartUnsupported: "此平台不支持自启动",
		StartupDesktopComment:       "登录时启动 RuClaw Web",
		LauncherConfigReadFailed:    "无法加载 launcher 配置: %v",
		LauncherConfigInvalidJSON:   "无效 JSON: %v",
		LauncherConfigSaveFailed:    "无法保存 launcher 配置: %v",
		PicoProxyFailed:             "网关不可用: %v",
		PicoGatewayUnavailable:      "网关不可用于 WebSocket 代理",
		PicoGatewayNotAvailable:     "网关不可用",
		PicoInvalidToken:            "无效的 Pico token",
		PicoConfigLoadFailed:        "无法加载配置: %v",
		PicoConfigSaveFailed:        "无法保存配置: %v",
		PicoEnsureConfigLoadFailed:  "无法加载配置: %w",
		PicoEnsureConfigSaveFailed:  "无法保存配置: %w",
		OAuthLoadCredentialsFailed:  "无法加载凭据: %v",
		OAuthReadRequestFailed:      "无法读取请求体",
		OAuthInvalidJSON:            "无效 JSON: %v",
		OAuthUnsupportedProvider:    "不支持的 provider %q",
		OAuthUnsupportedMethodForProvider: "provider %q 不支持登录方式 %q",
		OAuthTokenRequired:          "必须提供 token",
		OAuthTokenLoginFailed:       "token 登录失败: %v",
		OAuthDeviceCodeRequestFailed: "请求 device code 失败: %v",
		OAuthGeneratePKCEFailed:     "生成 PKCE 失败: %v",
		OAuthGenerateStateFailed:    "生成 state 失败: %v",
		OAuthUnsupportedMethod:      "不支持的登录方式",
		OAuthMissingFlowID:          "缺少 flow id",
		OAuthFlowNotFound:           "未找到 flow",
		OAuthFlowNoPolling:          "该 flow 不支持轮询",
		OAuthDeviceCodePollFailed:   "轮询 device code 失败: %v",
		OAuthSaveCredentialFailed:   "保存凭据失败: %v",
		OAuthDeleteCredentialFailed: "删除凭据失败: %v",
		OAuthUpdateConfigFailed:     "更新配置失败: %v",
		OAuthCallbackMissingStateTitle: "缺少 state",
		OAuthCallbackFlowNotFoundTitle: "未找到 OAuth flow",
		OAuthCallbackAlreadyCompletedTitle: "Flow 已完成",
		OAuthCallbackAuthorizationFailedTitle: "授权失败",
		OAuthCallbackMissingCodeTitle: "缺少授权码",
		OAuthCallbackUnsupportedProviderTitle: "不支持的 provider",
		OAuthCallbackTokenExchangeFailedTitle: "交换 token 失败",
		OAuthCallbackSaveCredentialFailedTitle: "保存凭据失败",
		OAuthCallbackSuccessTitle:    "认证成功",
		OAuthCallbackCloseWindow:     "现在可以关闭此窗口。",
		OAuthCallbackWindowTitle:     "RuClaw OAuth",
		OAuthProviderNoBrowser:       "provider %q 不支持 browser oauth",
		OAuthFlowExpired:             "flow 已过期",
		OAuthEmptyCredential:         "空凭据",
		OAuthPersistCredentialFailed: "保存凭据: %w",
		OAuthSyncProviderConfigFailed: "同步 provider auth 配置: %w",
		FlowMissingID:                "缺少 flow id",
		FlowNotFound:                 "未找到 flow",
		QRCodeFetchFailed:            "获取二维码失败: %v",
		QRImageGenerateFailed:        "生成二维码图片失败: %v",
		WeixinCreateClientFailed:     "创建 Weixin 客户端失败: %v",
		WeixinClientFailed:           "客户端错误: %v",
		WeixinMissingBotToken:        "登录已确认，但缺少 bot_token",
		WeixinSaveTokenFailed:        "保存 token 失败: %v",
		WecomMissingBotCredentials:   "登录已确认，但缺少机器人凭据",
		WecomSaveCredentialsFailed:   "保存凭据失败: %v",
		WecomInvalidGenerateURL:      "无效的 WeCom 二维码生成 URL: %w",
		WecomInvalidQueryURL:         "无效的 WeCom 二维码查询 URL: %w",
		WecomMissingResponseFields:   "响应缺少 scode 或 auth_url",
	},
}

// SetLauncherLanguage overrides the launcher language explicitly.
func SetLauncherLanguage(lang string) {
	currentLauncherLanguage.Store(normalizeLauncherLanguage(lang))
}

// GetLauncherLanguage returns the current launcher language.
func GetLauncherLanguage() Language {
	if lang, ok := currentLauncherLanguage.Load().(Language); ok && lang != "" {
		return lang
	}
	return LanguageRussian
}

// TranslateLauncher translates a launcher/backend message for the active language.
func TranslateLauncher(key TranslationKey, args ...any) string {
	lang := GetLauncherLanguage()
	if trans, ok := launcherTranslations[lang][key]; ok {
		if len(args) > 0 {
			return fmt.Sprintf(trans, args...)
		}
		return trans
	}
	if trans, ok := launcherTranslations[LanguageEnglish][key]; ok {
		if len(args) > 0 {
			return fmt.Sprintf(trans, args...)
		}
		return trans
	}
	return string(key)
}

func normalizeLauncherLanguage(lang string) Language {
	normalized := strings.ToLower(strings.TrimSpace(lang))
	if normalized == "" {
		return LanguageRussian
	}
	if idx := strings.IndexAny(normalized, "-_."); idx > 0 {
		normalized = normalized[:idx]
	}
	switch normalized {
	case "en", "english":
		return LanguageEnglish
	case "zh", "cn", "chinese":
		return LanguageChinese
	case "ru", "russian", "russian-ru", "русский":
		return LanguageRussian
	default:
		return LanguageRussian
	}
}
