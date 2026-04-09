package api

import "testing"

func TestNormalizeLauncherLanguage(t *testing.T) {
	tests := []struct {
		input string
		want  Language
	}{
		{input: "", want: LanguageRussian},
		{input: "ru_RU.UTF-8", want: LanguageRussian},
		{input: "en_US.UTF-8", want: LanguageEnglish},
		{input: "zh-CN", want: LanguageChinese},
		{input: "fr_FR.UTF-8", want: LanguageRussian},
	}

	for _, tt := range tests {
		if got := normalizeLauncherLanguage(tt.input); got != tt.want {
			t.Fatalf("normalizeLauncherLanguage(%q) = %q, want %q", tt.input, got, tt.want)
		}
	}
}

func TestSetLauncherLanguageDefaultsToRussian(t *testing.T) {
	orig := GetLauncherLanguage()
	t.Cleanup(func() {
		SetLauncherLanguage(string(orig))
	})

	SetLauncherLanguage("")
	if got := GetLauncherLanguage(); got != LanguageRussian {
		t.Fatalf("GetLauncherLanguage() = %q, want %q", got, LanguageRussian)
	}
	if got := TranslateLauncher(OAuthCallbackFlowNotFoundTitle); got != "OAuth flow не найден" {
		t.Fatalf("TranslateLauncher() = %q, want Russian default", got)
	}
}
