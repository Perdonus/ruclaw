package status

import (
	"fmt"
	"os"

	"github.com/Perdonus/ruclaw/cmd/picoclaw/internal"
	"github.com/Perdonus/ruclaw/pkg/auth"
	"github.com/Perdonus/ruclaw/pkg/config"
)

func statusCmd() {
	cfg, err := internal.LoadConfig()
	if err != nil {
		fmt.Printf("Ошибка загрузки config: %v\n", err)
		return
	}

	configPath := internal.GetConfigPath()

	fmt.Printf("%s Состояние RuClaw\n", internal.Logo)
	fmt.Printf("Версия: %s\n", config.FormatVersion())
	build, _ := config.FormatBuildInfo()
	if build != "" {
		fmt.Printf("Сборка: %s\n", build)
	}
	fmt.Println()

	if _, err := os.Stat(configPath); err == nil {
		fmt.Println("Config:", configPath, "✓")
	} else {
		fmt.Println("Config:", configPath, "✗")
	}

	workspace := cfg.WorkspacePath()
	if _, err := os.Stat(workspace); err == nil {
		fmt.Println("Рабочее пространство:", workspace, "✓")
	} else {
		fmt.Println("Рабочее пространство:", workspace, "✗")
	}

	if _, err := os.Stat(configPath); err == nil {
		fmt.Printf("Модель: %s\n", cfg.Agents.Defaults.GetModelName())

		store, _ := auth.LoadStore()
		if store != nil && len(store.Credentials) > 0 {
			fmt.Println("\nOAuth/токен-авторизация:")
			for provider, cred := range store.Credentials {
				status := "авторизован"
				if cred.IsExpired() {
					status = "истёк"
				} else if cred.NeedsRefresh() {
					status = "требует обновления"
				}
				fmt.Printf("  %s (%s): %s\n", provider, cred.AuthMethod, status)
			}
		}
	}
}
