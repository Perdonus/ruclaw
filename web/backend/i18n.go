package main

import "github.com/Perdonus/ruclaw/web/backend/api"

type Language = api.Language

const (
	LanguageRussian Language = api.LanguageRussian
	LanguageEnglish Language = api.LanguageEnglish
	LanguageChinese Language = api.LanguageChinese
)

type TranslationKey = api.TranslationKey

const (
	AppTooltip         TranslationKey = api.AppTooltip
	MenuOpen           TranslationKey = api.MenuOpen
	MenuOpenTooltip    TranslationKey = api.MenuOpenTooltip
	MenuAbout          TranslationKey = api.MenuAbout
	MenuAboutTooltip   TranslationKey = api.MenuAboutTooltip
	MenuVersion        TranslationKey = api.MenuVersion
	MenuVersionTooltip TranslationKey = api.MenuVersionTooltip
	MenuGitHub         TranslationKey = api.MenuGitHub
	MenuDocs           TranslationKey = api.MenuDocs
	MenuRestart        TranslationKey = api.MenuRestart
	MenuRestartTooltip TranslationKey = api.MenuRestartTooltip
	MenuQuit           TranslationKey = api.MenuQuit
	MenuQuitTooltip    TranslationKey = api.MenuQuitTooltip
	Exiting            TranslationKey = api.Exiting
	DocUrl             TranslationKey = api.DocUrl
	FlagPort           TranslationKey = api.FlagPort
	FlagPublic         TranslationKey = api.FlagPublic
	FlagNoBrowser      TranslationKey = api.FlagNoBrowser
	FlagLang           TranslationKey = api.FlagLang
	FlagConsole        TranslationKey = api.FlagConsole
	FlagDebug          TranslationKey = api.FlagDebug
	UsageTitle         TranslationKey = api.UsageTitle
	UsageLine          TranslationKey = api.UsageLine
	UsageArguments     TranslationKey = api.UsageArguments
	UsageConfigArgument TranslationKey = api.UsageConfigArgument
	UsageOptions       TranslationKey = api.UsageOptions
	UsageExamples      TranslationKey = api.UsageExamples
	ExampleDefaultMode TranslationKey = api.ExampleDefaultMode
	ExampleConfigFile  TranslationKey = api.ExampleConfigFile
	ExamplePublicAccess TranslationKey = api.ExamplePublicAccess
	ExampleConsoleDebug TranslationKey = api.ExampleConsoleDebug
	OpenBrowserPrompt  TranslationKey = api.OpenBrowserPrompt
	DashboardPasswordRuntime TranslationKey = api.DashboardPasswordRuntime
	DashboardPasswordEnv TranslationKey = api.DashboardPasswordEnv
	DashboardPasswordConfig TranslationKey = api.DashboardPasswordConfig
	DebugModeEnabled   TranslationKey = api.DebugModeEnabled
	LauncherStarting   TranslationKey = api.LauncherStarting
	LauncherHome       TranslationKey = api.LauncherHome
	LauncherFlags      TranslationKey = api.LauncherFlags
	ResolveConfigPathFailed TranslationKey = api.ResolveConfigPathFailed
	AutoInitConfigFailed TranslationKey = api.AutoInitConfigFailed
	EnsurePicoChannelFailed TranslationKey = api.EnsurePicoChannelFailed
	LoadLauncherConfigFailed TranslationKey = api.LoadLauncherConfigFailed
	InvalidPort        TranslationKey = api.InvalidPort
	DashboardAuthSetupFailed TranslationKey = api.DashboardAuthSetupFailed
	OpenAuthStoreFailed TranslationKey = api.OpenAuthStoreFailed
	InvalidAllowedCIDR TranslationKey = api.InvalidAllowedCIDR
	LocalServerAddress TranslationKey = api.LocalServerAddress
	PublicServerAddress TranslationKey = api.PublicServerAddress
	ServerListening    TranslationKey = api.ServerListening
	ServerStartFailed  TranslationKey = api.ServerStartFailed
	AutoOpenBrowserFailed TranslationKey = api.AutoOpenBrowserFailed
	ShuttingDown       TranslationKey = api.ShuttingDown
	OpenBrowserFailed  TranslationKey = api.OpenBrowserFailed
	OpenGitHubFailed   TranslationKey = api.OpenGitHubFailed
	OpenDocsFailed     TranslationKey = api.OpenDocsFailed
	RestartRequestReceived TranslationKey = api.RestartRequestReceived
	RestartGatewayFailed TranslationKey = api.RestartGatewayFailed
	GatewayRestarted   TranslationKey = api.GatewayRestarted
	SystemTrayUnavailableNoCGO TranslationKey = api.SystemTrayUnavailableNoCGO
)

func SetLanguage(lang string) {
	api.SetLauncherLanguage(lang)
}

func GetLanguage() Language {
	return api.GetLauncherLanguage()
}

func T(key TranslationKey, args ...any) string {
	return api.TranslateLauncher(api.TranslationKey(key), args...)
}
