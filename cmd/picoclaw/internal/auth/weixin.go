package auth

import (
	"context"
	"fmt"
	"time"

	"github.com/spf13/cobra"

	"github.com/Perdonus/ruclaw/cmd/picoclaw/internal"
	"github.com/Perdonus/ruclaw/pkg/channels/weixin"
	"github.com/Perdonus/ruclaw/pkg/config"
)

func newWeixinCommand() *cobra.Command {
	var baseURL string
	var proxy string
	var timeout int

	cmd := &cobra.Command{
		Use:   "weixin",
		Short: "Подключить личный аккаунт WeChat по QR-коду",
		Long: `Запустить интерактивный вход Weixin (личный WeChat) по QR-коду.

QR-код будет показан прямо в терминале. Отсканируйте его мобильным приложением
WeChat, чтобы авторизовать аккаунт. После успешного входа токен бота сохранится
в конфигурации RuClaw, и шлюз можно будет запустить сразу.

Пример:
  ruclaw auth weixin`,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return runWeixinOnboard(baseURL, proxy, time.Duration(timeout)*time.Second)
		},
	}

	cmd.Flags().StringVar(&baseURL, "base-url", "https://ilinkai.weixin.qq.com/", "Базовый URL iLink API")
	cmd.Flags().StringVar(&proxy, "proxy", "", "URL HTTP-прокси (например, http://localhost:7890)")
	cmd.Flags().IntVar(&timeout, "timeout", 300, "Тайм-аут входа в секундах")

	return cmd
}

func runWeixinOnboard(baseURL, proxy string, timeout time.Duration) error {
	fmt.Println("Запускаю вход Weixin (личный WeChat)...")
	fmt.Println()

	botToken, userID, accountID, returnedBaseURL, err := weixin.PerformLoginInteractive(
		context.Background(),
		weixin.AuthFlowOpts{
			BaseURL: baseURL,
			Timeout: timeout,
			Proxy:   proxy,
		},
	)
	if err != nil {
		return fmt.Errorf("ошибка входа: %w", err)
	}

	fmt.Println()
	fmt.Println("✅ Вход выполнен успешно!")
	fmt.Printf("   ID аккаунта: %s\n", accountID)
	if userID != "" {
		fmt.Printf("   ID пользователя: %s\n", userID)
	}
	fmt.Println()

	// Prefer the server-returned base URL (may be region-specific)
	effectiveBaseURL := returnedBaseURL
	if effectiveBaseURL == "" {
		effectiveBaseURL = baseURL
	}

	if err := saveWeixinConfig(botToken, effectiveBaseURL, proxy); err != nil {
		fmt.Printf("⚠️  Не удалось автоматически сохранить конфигурацию: %v\n", err)
		printManualWeixinConfig(botToken, effectiveBaseURL)
		return nil
	}

	fmt.Println("✓ Конфигурация RuClaw обновлена. Запустите шлюз командой:")
	fmt.Println()
	fmt.Println("  ruclaw gateway")
	fmt.Println()
	fmt.Println("Чтобы ограничить список пользователей WeChat, которые могут писать боту,")
	fmt.Println("добавьте их user ID в channels.weixin.allow_from вашей конфигурации.")

	return nil
}

// saveWeixinConfig patches channels.weixin in the config and saves it.
func saveWeixinConfig(token, baseURL, proxy string) error {
	cfgPath := internal.GetConfigPath()

	cfg, err := config.LoadConfig(cfgPath)
	if err != nil {
		return fmt.Errorf("не удалось загрузить конфигурацию: %w", err)
	}

	cfg.Channels.Weixin.Enabled = true
	cfg.Channels.Weixin.SetToken(token)
	const defaultBase = "https://ilinkai.weixin.qq.com/"
	if baseURL != "" && baseURL != defaultBase {
		cfg.Channels.Weixin.BaseURL = baseURL
	}
	if proxy != "" {
		cfg.Channels.Weixin.Proxy = proxy
	}

	return config.SaveConfig(cfgPath, cfg)
}

func printManualWeixinConfig(token, baseURL string) {
	fmt.Println()
	fmt.Println("Добавьте следующий блок в секцию channels вашей конфигурации RuClaw:")
	fmt.Println()
	fmt.Println(`  "weixin": {`)
	fmt.Println(`    "enabled": true,`)
	fmt.Printf("    \"token\": %q,\n", token)
	const defaultBase = "https://ilinkai.weixin.qq.com/"
	if baseURL != "" && baseURL != defaultBase {
		fmt.Printf("    \"base_url\": %q,\n", baseURL)
	}
	fmt.Println(`    "allow_from": []`)
	fmt.Println(`  }`)
}
