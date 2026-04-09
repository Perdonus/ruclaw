package auth

import (
	"bufio"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/Perdonus/ruclaw/cmd/picoclaw/internal"
	"github.com/Perdonus/ruclaw/pkg/auth"
	"github.com/Perdonus/ruclaw/pkg/config"
	"github.com/Perdonus/ruclaw/pkg/providers"
)

const (
	supportedProvidersMsg = "доступные провайдеры: openai, anthropic, google-antigravity"
	defaultAnthropicModel = "claude-sonnet-4.6"
)

func authLoginCmd(provider string, useDeviceCode bool, useOauth bool) error {
	switch provider {
	case "openai":
		return authLoginOpenAI(useDeviceCode)
	case "anthropic":
		return authLoginAnthropic(useOauth)
	case "google-antigravity", "antigravity":
		return authLoginGoogleAntigravity()
	default:
		return fmt.Errorf("неподдерживаемый провайдер: %s (%s)", provider, supportedProvidersMsg)
	}
}

func authLoginOpenAI(useDeviceCode bool) error {
	cfg := auth.OpenAIOAuthConfig()

	var cred *auth.AuthCredential
	var err error

	if useDeviceCode {
		cred, err = auth.LoginDeviceCode(cfg)
	} else {
		cred, err = auth.LoginBrowser(cfg)
	}

	if err != nil {
		return fmt.Errorf("ошибка входа: %w", err)
	}

	if err = auth.SetCredential("openai", cred); err != nil {
		return fmt.Errorf("не удалось сохранить учётные данные: %w", err)
	}

	appCfg, err := internal.LoadConfig()
	if err == nil {
		// Update or add openai in ModelList
		foundOpenAI := false
		for i := range appCfg.ModelList {
			if isOpenAIModel(appCfg.ModelList[i].Model) {
				appCfg.ModelList[i].AuthMethod = "oauth"
				foundOpenAI = true
				break
			}
		}

		// If no openai in ModelList, add it
		if !foundOpenAI {
			appCfg.ModelList = append(appCfg.ModelList, &config.ModelConfig{
				ModelName:  "gpt-5.4",
				Model:      "openai/gpt-5.4",
				AuthMethod: "oauth",
			})
		}

		// Update default model to use OpenAI
		appCfg.Agents.Defaults.ModelName = "gpt-5.4"

		if err = config.SaveConfig(internal.GetConfigPath(), appCfg); err != nil {
			return fmt.Errorf("не удалось обновить конфигурацию: %w", err)
		}
	}

	fmt.Println("Вход в OpenAI выполнен успешно!")
	if cred.AccountID != "" {
		fmt.Printf("Аккаунт: %s\n", cred.AccountID)
	}
	fmt.Println("Модель RuClaw по умолчанию установлена: gpt-5.4")

	return nil
}

func authLoginGoogleAntigravity() error {
	cfg := auth.GoogleAntigravityOAuthConfig()

	cred, err := auth.LoginBrowser(cfg)
	if err != nil {
		return fmt.Errorf("ошибка входа: %w", err)
	}

	cred.Provider = "google-antigravity"

	// Fetch user email from Google userinfo
	email, err := fetchGoogleUserEmail(cred.AccessToken)
	if err != nil {
		fmt.Printf("Предупреждение: не удалось получить email: %v\n", err)
	} else {
		cred.Email = email
		fmt.Printf("Эл. почта: %s\n", email)
	}

	// Fetch Cloud Code Assist project ID
	projectID, err := providers.FetchAntigravityProjectID(cred.AccessToken)
	if err != nil {
		fmt.Printf("Предупреждение: не удалось получить ID проекта: %v\n", err)
		fmt.Println("Возможно, для вашего аккаунта нужно включить Google Cloud Code Assist.")
	} else {
		cred.ProjectID = projectID
		fmt.Printf("Проект: %s\n", projectID)
	}

	if err = auth.SetCredential("google-antigravity", cred); err != nil {
		return fmt.Errorf("не удалось сохранить учётные данные: %w", err)
	}

	appCfg, err := internal.LoadConfig()
	if err == nil {
		// Update or add antigravity in ModelList
		foundAntigravity := false
		for i := range appCfg.ModelList {
			if isAntigravityModel(appCfg.ModelList[i].Model) {
				appCfg.ModelList[i].AuthMethod = "oauth"
				foundAntigravity = true
				break
			}
		}

		// If no antigravity in ModelList, add it
		if !foundAntigravity {
			appCfg.ModelList = append(appCfg.ModelList, &config.ModelConfig{
				ModelName:  "gemini-flash",
				Model:      "antigravity/gemini-3-flash",
				AuthMethod: "oauth",
			})
		}

		// Update default model
		appCfg.Agents.Defaults.ModelName = "gemini-flash"

		if err := config.SaveConfig(internal.GetConfigPath(), appCfg); err != nil {
			fmt.Printf("Предупреждение: не удалось обновить конфигурацию: %v\n", err)
		}
	}

	fmt.Println("\n✓ Google Antigravity подключён к RuClaw!")
	fmt.Println("Модель RuClaw по умолчанию установлена: gemini-flash")
	fmt.Println("Попробуйте: ruclaw agent -m \"Привет, мир\"")

	return nil
}

func authLoginAnthropic(useOauth bool) error {
	if useOauth {
		return authLoginAnthropicSetupToken()
	}

	fmt.Println("Выберите способ входа в Anthropic:")
	fmt.Println("  1) Setup token (из `claude setup-token`) (рекомендуется)")
	fmt.Println("  2) API key (из console.anthropic.com)")

	scanner := bufio.NewScanner(os.Stdin)
	for {
		fmt.Print("Выберите [1]: ")
		choice := "1"
		if scanner.Scan() {
			text := strings.TrimSpace(scanner.Text())
			if text != "" {
				choice = text
			}
		}

		switch choice {
		case "1":
			return authLoginAnthropicSetupToken()
		case "2":
			return authLoginPasteToken("anthropic")
		default:
			fmt.Printf("Неверный выбор: %s. Введите 1 или 2.\n", choice)
		}
	}
}

func authLoginAnthropicSetupToken() error {
	cred, err := auth.LoginSetupToken(os.Stdin)
	if err != nil {
		return fmt.Errorf("ошибка входа: %w", err)
	}

	if err = auth.SetCredential("anthropic", cred); err != nil {
		return fmt.Errorf("не удалось сохранить учётные данные: %w", err)
	}

	appCfg, err := internal.LoadConfig()
	if err == nil {
		found := false
		for i := range appCfg.ModelList {
			if isAnthropicModel(appCfg.ModelList[i].Model) {
				appCfg.ModelList[i].AuthMethod = "oauth"
				found = true
				break
			}
		}
		if !found {
			appCfg.ModelList = append(appCfg.ModelList, &config.ModelConfig{
				ModelName:  defaultAnthropicModel,
				Model:      "anthropic/" + defaultAnthropicModel,
				AuthMethod: "oauth",
			})
			// Only set default model if user has no default configured yet
			if appCfg.Agents.Defaults.GetModelName() == "" {
				appCfg.Agents.Defaults.ModelName = defaultAnthropicModel
			}
		}

		if err := config.SaveConfig(internal.GetConfigPath(), appCfg); err != nil {
			return fmt.Errorf("не удалось обновить конфигурацию: %w", err)
		}
	}

	fmt.Println("Setup token Anthropic сохранён для RuClaw!")

	return nil
}

func fetchGoogleUserEmail(accessToken string) (string, error) {
	req, err := http.NewRequest("GET", "https://www.googleapis.com/oauth2/v2/userinfo", nil)
	if err != nil {
		return "", err
	}
	req.Header.Set("Authorization", "Bearer "+accessToken)

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", fmt.Errorf("чтение ответа userinfo: %w", err)
	}
	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("запрос userinfo завершился ошибкой: %s", string(body))
	}

	var userInfo struct {
		Email string `json:"email"`
	}
	if err := json.Unmarshal(body, &userInfo); err != nil {
		return "", err
	}
	return userInfo.Email, nil
}

func authLoginPasteToken(provider string) error {
	cred, err := auth.LoginPasteToken(provider, os.Stdin)
	if err != nil {
		return fmt.Errorf("ошибка входа: %w", err)
	}

	if err = auth.SetCredential(provider, cred); err != nil {
		return fmt.Errorf("не удалось сохранить учётные данные: %w", err)
	}

	appCfg, err := internal.LoadConfig()
	if err == nil {
		switch provider {
		case "anthropic":
			// Update ModelList
			found := false
			for i := range appCfg.ModelList {
				if isAnthropicModel(appCfg.ModelList[i].Model) {
					appCfg.ModelList[i].AuthMethod = "token"
					found = true
					break
				}
			}
			if !found {
				appCfg.ModelList = append(appCfg.ModelList, &config.ModelConfig{
					ModelName:  defaultAnthropicModel,
					Model:      "anthropic/" + defaultAnthropicModel,
					AuthMethod: "token",
				})
				appCfg.Agents.Defaults.ModelName = defaultAnthropicModel
			}
		case "openai":
			// Update ModelList
			found := false
			for i := range appCfg.ModelList {
				if isOpenAIModel(appCfg.ModelList[i].Model) {
					appCfg.ModelList[i].AuthMethod = "token"
					found = true
					break
				}
			}
			if !found {
				appCfg.ModelList = append(appCfg.ModelList, &config.ModelConfig{
					ModelName:  "gpt-5.4",
					Model:      "openai/gpt-5.4",
					AuthMethod: "token",
				})
			}
			// Update default model
			appCfg.Agents.Defaults.ModelName = "gpt-5.4"
		}
		if err := config.SaveConfig(internal.GetConfigPath(), appCfg); err != nil {
			return fmt.Errorf("не удалось обновить конфигурацию: %w", err)
		}
	}

	fmt.Printf("Токен для %s сохранён в RuClaw.\n", provider)

	if appCfg != nil {
		fmt.Printf("Модель RuClaw по умолчанию установлена: %s\n", appCfg.Agents.Defaults.GetModelName())
	}

	return nil
}

func authLogoutCmd(provider string) error {
	if provider != "" {
		if err := auth.DeleteCredential(provider); err != nil {
			return fmt.Errorf("не удалось удалить учётные данные: %w", err)
		}

		appCfg, err := internal.LoadConfig()
		if err == nil {
			// Clear AuthMethod in ModelList
			for i := range appCfg.ModelList {
				switch provider {
				case "openai":
					if isOpenAIModel(appCfg.ModelList[i].Model) {
						appCfg.ModelList[i].AuthMethod = ""
					}
				case "anthropic":
					if isAnthropicModel(appCfg.ModelList[i].Model) {
						appCfg.ModelList[i].AuthMethod = ""
					}
				case "google-antigravity", "antigravity":
					if isAntigravityModel(appCfg.ModelList[i].Model) {
						appCfg.ModelList[i].AuthMethod = ""
					}
				}
			}
			config.SaveConfig(internal.GetConfigPath(), appCfg)
		}

		fmt.Printf("Вы вышли из %s\n", provider)

		return nil
	}

	if err := auth.DeleteAllCredentials(); err != nil {
		return fmt.Errorf("не удалось удалить учётные данные: %w", err)
	}

	appCfg, err := internal.LoadConfig()
	if err == nil {
		// Clear all AuthMethods in ModelList
		for i := range appCfg.ModelList {
			appCfg.ModelList[i].AuthMethod = ""
		}
		config.SaveConfig(internal.GetConfigPath(), appCfg)
	}

	fmt.Println("Вы вышли из всех провайдеров")

	return nil
}

func authStatusCmd() error {
	store, err := auth.LoadStore()
	if err != nil {
		return fmt.Errorf("не удалось загрузить хранилище авторизации: %w", err)
	}

	if len(store.Credentials) == 0 {
		fmt.Println("Нет подключённых провайдеров.")
		fmt.Println("Запустите: ruclaw auth login --provider <name>")
		return nil
	}

	fmt.Println("\nПодключённые провайдеры:")
	fmt.Println("--------------------------")
	for provider, cred := range store.Credentials {
		status := "активен"
		if cred.IsExpired() {
			status = "истёк"
		} else if cred.NeedsRefresh() {
			status = "требует обновления"
		}

		fmt.Printf("  %s:\n", provider)
		fmt.Printf("    Способ: %s\n", cred.AuthMethod)
		fmt.Printf("    Статус: %s\n", status)
		if cred.AccountID != "" {
			fmt.Printf("    Аккаунт: %s\n", cred.AccountID)
		}
		if cred.Email != "" {
			fmt.Printf("    Эл. почта: %s\n", cred.Email)
		}
		if cred.ProjectID != "" {
			fmt.Printf("    Проект: %s\n", cred.ProjectID)
		}
		if !cred.ExpiresAt.IsZero() {
			fmt.Printf("    Истекает: %s\n", cred.ExpiresAt.Format("2006-01-02 15:04"))
		}

		if provider == "anthropic" && cred.AuthMethod == "oauth" {
			usage, err := auth.FetchAnthropicUsage(cred.AccessToken)
			if err != nil {
				fmt.Printf("    Использование: недоступно (%v)\n", err)
			} else {
				fmt.Printf("    Использование (5 ч): %.1f%%\n", usage.FiveHourUtilization*100)
				fmt.Printf("    Использование (7 д): %.1f%%\n", usage.SevenDayUtilization*100)
			}
		}
	}

	return nil
}

func authModelsCmd() error {
	cred, err := auth.GetCredential("google-antigravity")
	if err != nil || cred == nil {
		return fmt.Errorf(
			"в Google Antigravity вход не выполнен.\nзапустите: ruclaw auth login --provider google-antigravity",
		)
	}

	// Refresh token if needed
	if cred.NeedsRefresh() && cred.RefreshToken != "" {
		oauthCfg := auth.GoogleAntigravityOAuthConfig()
		refreshed, refreshErr := auth.RefreshAccessToken(cred, oauthCfg)
		if refreshErr == nil {
			cred = refreshed
			_ = auth.SetCredential("google-antigravity", cred)
		}
	}

	projectID := cred.ProjectID
	if projectID == "" {
		return fmt.Errorf("ID проекта не сохранён. Попробуйте войти снова")
	}

	fmt.Printf("Получаю модели для проекта: %s\n\n", projectID)

	models, err := providers.FetchAntigravityModels(cred.AccessToken, projectID)
	if err != nil {
		return fmt.Errorf("ошибка получения моделей: %w", err)
	}

	if len(models) == 0 {
		return fmt.Errorf("доступных моделей нет")
	}

	fmt.Println("Доступные модели Google Antigravity:")
	fmt.Println("-----------------------------")
	for _, m := range models {
		status := "✓"
		if m.IsExhausted {
			status = "✗ (квота исчерпана)"
		}
		name := m.ID
		if m.DisplayName != "" {
			name = fmt.Sprintf("%s (%s)", m.ID, m.DisplayName)
		}
		fmt.Printf("  %s %s\n", status, name)
	}

	return nil
}

// isAntigravityModel checks if a model string belongs to antigravity provider
func isAntigravityModel(model string) bool {
	return model == "antigravity" ||
		model == "google-antigravity" ||
		strings.HasPrefix(model, "antigravity/") ||
		strings.HasPrefix(model, "google-antigravity/")
}

// isOpenAIModel checks if a model string belongs to openai provider
func isOpenAIModel(model string) bool {
	return model == "openai" ||
		strings.HasPrefix(model, "openai/")
}

// isAnthropicModel checks if a model string belongs to anthropic provider
func isAnthropicModel(model string) bool {
	return model == "anthropic" ||
		strings.HasPrefix(model, "anthropic/")
}
