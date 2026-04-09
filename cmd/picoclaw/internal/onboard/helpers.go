package onboard

import (
	"fmt"
	"io/fs"
	"os"
	"path/filepath"

	"golang.org/x/term"

	"github.com/Perdonus/ruclaw/cmd/picoclaw/internal"
	"github.com/Perdonus/ruclaw/pkg/config"
	"github.com/Perdonus/ruclaw/pkg/credential"
)

func onboard(encrypt bool) {
	configPath := internal.GetConfigPath()

	configExists := false
	if _, err := os.Stat(configPath); err == nil {
		configExists = true
		if encrypt {
			// Only ask for confirmation when *both* config and SSH key already exist,
			// indicating a full re-onboard that would reset the config to defaults.
			sshKeyPath, _ := credential.DefaultSSHKeyPath()
			if _, err := os.Stat(sshKeyPath); err == nil {
				// Both exist — confirm a full reset.
				fmt.Printf("Конфигурация уже существует: %s\n", configPath)
				fmt.Print("Перезаписать конфигурацию значениями по умолчанию? (y/n): ")
				var response string
				fmt.Scanln(&response)
				if response != "y" {
					fmt.Println("Отменено.")
					return
				}
				configExists = false // user agreed to reset; treat as fresh
			}
			// Config exists but SSH key is missing — keep existing config, only add SSH key.
		}
	}

	var err error
	if encrypt {
		fmt.Println("\nНастройка шифрования учётных данных")
		fmt.Println("----------------------------------")
		passphrase, pErr := promptPassphrase()
		if pErr != nil {
			fmt.Printf("Ошибка: %v\n", pErr)
			os.Exit(1)
		}
		// Expose the passphrase to credential.PassphraseProvider (which calls
		// os.Getenv by default) so that SaveConfig can encrypt api_keys.
		// This process is a one-shot CLI tool; the env var is never exposed outside
		// the current process and disappears when it exits.
		os.Setenv(credential.PassphraseEnvVar, passphrase)

		if err = setupSSHKey(); err != nil {
			fmt.Printf("Ошибка создания SSH-ключа: %v\n", err)
			os.Exit(1)
		}
	}

	var cfg *config.Config
	if configExists {
		// Preserve the existing config; SaveConfig will re-encrypt api_keys with the new passphrase.
		cfg, err = config.LoadConfig(configPath)
		if err != nil {
			fmt.Printf("Ошибка загрузки существующей конфигурации: %v\n", err)
			os.Exit(1)
		}
	} else {
		cfg = config.DefaultConfig()
	}
	if err := config.SaveConfig(configPath, cfg); err != nil {
		fmt.Printf("Ошибка сохранения конфигурации: %v\n", err)
		os.Exit(1)
	}

	workspace := cfg.WorkspacePath()
	createWorkspaceTemplates(workspace)

	fmt.Printf("\n%s RuClaw готов к работе!\n", internal.Logo)
	fmt.Println("\nЧто делать дальше:")
	if encrypt {
		fmt.Println("  1. Перед запуском RuClaw задайте passphrase в переменной среды:")
		fmt.Println("       export PICOCLAW_KEY_PASSPHRASE=<your-passphrase>   # Linux/macOS, имя сохранено для совместимости")
		fmt.Println("       set PICOCLAW_KEY_PASSPHRASE=<your-passphrase>      # Windows cmd, имя сохранено для совместимости")
		fmt.Println("")
		fmt.Println("  2. Добавьте API-ключ в файл конфигурации:", configPath)
	} else {
		fmt.Println("  1. Добавьте API-ключ в файл конфигурации:", configPath)
	}
	fmt.Println("")
	fmt.Println("     Рекомендуем:")
	fmt.Println("     - OpenRouter: https://openrouter.ai/keys (доступ к 100+ моделям)")
	fmt.Println("     - Ollama:     https://ollama.com (локально, бесплатно)")
	fmt.Println("")
	fmt.Println("     Подробнее в README.md: поддерживается 17+ провайдеров.")
	fmt.Println("")
	if encrypt {
		fmt.Println("  3. Чат: ruclaw agent -m \"Привет!\"")
	} else {
		fmt.Println("  2. Чат: ruclaw agent -m \"Привет!\"")
	}
}

// promptPassphrase reads the encryption passphrase twice from the terminal
// (with echo disabled) and returns it. Returns an error if the passphrase is
// empty or if the two inputs do not match.
func promptPassphrase() (string, error) {
	fmt.Print("Введите passphrase для шифрования учётных данных: ")
	p1, err := term.ReadPassword(int(os.Stdin.Fd()))
	fmt.Println()
	if err != nil {
		return "", fmt.Errorf("чтение passphrase: %w", err)
	}
	if len(p1) == 0 {
		return "", fmt.Errorf("passphrase не должен быть пустым")
	}

	fmt.Print("Подтвердите passphrase: ")
	p2, err := term.ReadPassword(int(os.Stdin.Fd()))
	fmt.Println()
	if err != nil {
		return "", fmt.Errorf("чтение подтверждения passphrase: %w", err)
	}

	if string(p1) != string(p2) {
		return "", fmt.Errorf("passphrase не совпадают")
	}
	return string(p1), nil
}

// setupSSHKey generates the RuClaw SSH key using pkg/credential's default path.
// If the key already exists the user is warned and asked to confirm overwrite.
// Answering anything other than "y" keeps the existing key (not an error).
func setupSSHKey() error {
	keyPath, err := credential.DefaultSSHKeyPath()
	if err != nil {
		return fmt.Errorf("не удалось определить путь к SSH-ключу: %w", err)
	}

	if _, err := os.Stat(keyPath); err == nil {
		fmt.Printf("\n⚠️  ВНИМАНИЕ: %s уже существует.\n", keyPath)
		fmt.Println("    Перезапись сделает недействительными все учётные данные, зашифрованные этим ключом.")
		fmt.Print("    Перезаписать? (y/n): ")
		var response string
		fmt.Scanln(&response)
		if response != "y" {
			fmt.Println("Оставляю существующий SSH-ключ.")
			return nil
		}
	}

	if err := credential.GenerateSSHKey(keyPath); err != nil {
		return err
	}
	fmt.Printf("SSH-ключ создан: %s\n", keyPath)
	return nil
}

func createWorkspaceTemplates(workspace string) {
	err := copyEmbeddedToTarget(workspace)
	if err != nil {
		fmt.Printf("Ошибка копирования шаблонов рабочего пространства: %v\n", err)
	}
}

func copyEmbeddedToTarget(targetDir string) error {
	// Ensure target directory exists
	if err := os.MkdirAll(targetDir, 0o755); err != nil {
		return fmt.Errorf("не удалось создать целевой каталог: %w", err)
	}

	// Walk through all files in embed.FS
	err := fs.WalkDir(embeddedFiles, "workspace", func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}

		// Skip directories
		if d.IsDir() {
			return nil
		}

		// Read embedded file
		data, err := embeddedFiles.ReadFile(path)
		if err != nil {
			return fmt.Errorf("не удалось прочитать встроенный файл %s: %w", path, err)
		}

		newPath, err := filepath.Rel("workspace", path)
		if err != nil {
			return fmt.Errorf("не удалось получить относительный путь для %s: %v\n", path, err)
		}

		// Build target file path
		targetPath := filepath.Join(targetDir, newPath)

		// Ensure target file's directory exists
		if err := os.MkdirAll(filepath.Dir(targetPath), 0o755); err != nil {
			return fmt.Errorf("не удалось создать каталог %s: %w", filepath.Dir(targetPath), err)
		}

		// Write file
		if err := os.WriteFile(targetPath, data, 0o644); err != nil {
			return fmt.Errorf("не удалось записать файл %s: %w", targetPath, err)
		}

		return nil
	})

	return err
}
