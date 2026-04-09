package model

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/Perdonus/ruclaw/cmd/picoclaw/internal"
	"github.com/Perdonus/ruclaw/pkg/config"
)

// LocalModel is a special model name that indicates that the model is local and with or without api_key.
const LocalModel = "local-model"

func NewModelCommand() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "model [model_name]",
		Short: "Показать или изменить модель по умолчанию",
		Long: `Показать или изменить конфигурацию модели по умолчанию.

Если аргумент не указан, команда покажет текущую модель по умолчанию.
Если указано имя модели, оно будет установлено как модель по умолчанию.

Примеры:
  ruclaw model                    # Показать текущую модель по умолчанию
  ruclaw model gpt-5.2            # Сделать gpt-5.2 моделью по умолчанию
  ruclaw model claude-sonnet-4.6  # Сделать claude-sonnet-4.6 моделью по умолчанию
  ruclaw model local-model        # Использовать локальный VLLM-сервер по умолчанию

Примечание: 'local-model' — это специальное значение для локального VLLM-сервера
(по умолчанию он работает на localhost:8000) и не требует API-ключа.`,
		Args: cobra.MaximumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			configPath := internal.GetConfigPath()

			// Load current config
			cfg, err := config.LoadConfig(configPath)
			if err != nil {
				return fmt.Errorf("не удалось загрузить config: %w", err)
			}

			if len(args) == 0 {
				// Show current default model
				showCurrentModel(cfg)
				return nil
			}

			// Set new default model
			modelName := args[0]
			return setDefaultModel(configPath, cfg, modelName)
		},
	}

	return cmd
}

func showCurrentModel(cfg *config.Config) {
	defaultModel := cfg.Agents.Defaults.ModelName

	if defaultModel == "" {
		fmt.Println("Модель по умолчанию сейчас не задана.")
		fmt.Println("\nДоступные модели в config:")
		listAvailableModels(cfg)
	} else {
		fmt.Printf("Текущая модель по умолчанию: %s\n", defaultModel)
		fmt.Println("\nДоступные модели в config:")
		listAvailableModels(cfg)
	}
}

func listAvailableModels(cfg *config.Config) {
	if len(cfg.ModelList) == 0 {
		fmt.Println("  В model_list не настроено ни одной модели")
		return
	}

	defaultModel := cfg.Agents.Defaults.ModelName

	for _, model := range cfg.ModelList {
		marker := "  "
		if model.ModelName == defaultModel {
			marker = "> "
		}
		if !model.Enabled {
			continue
		}
		fmt.Printf("%s- %s (%s)\n", marker, model.ModelName, model.Model)
	}
}

func setDefaultModel(configPath string, cfg *config.Config, modelName string) error {
	// Validate that the model exists in model_list
	modelFound := false
	for _, model := range cfg.ModelList {
		if model.Enabled && model.ModelName == modelName {
			modelFound = true
			break
		}
	}

	if !modelFound && modelName != LocalModel {
		return fmt.Errorf("модель '%s' не найдена в config", modelName)
	}

	// Update the default model
	// Clear old model field and set new model_name
	oldModel := cfg.Agents.Defaults.ModelName

	cfg.Agents.Defaults.ModelName = modelName

	// Save config back to file
	if err := config.SaveConfig(configPath, cfg); err != nil {
		return fmt.Errorf("не удалось сохранить config: %w", err)
	}

	fmt.Printf("✓ Модель по умолчанию изменена: '%s' -> '%s'\n",
		formatModelName(oldModel), modelName)
	fmt.Println("\nНовая модель по умолчанию будет использоваться во всех диалогах с агентом.")

	return nil
}

func formatModelName(name string) string {
	if name == "" {
		return "(нет)"
	}
	return name
}
