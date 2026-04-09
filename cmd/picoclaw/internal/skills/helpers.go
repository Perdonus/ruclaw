package skills

import (
	"context"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/Perdonus/ruclaw/cmd/picoclaw/internal"
	"github.com/Perdonus/ruclaw/pkg/config"
	"github.com/Perdonus/ruclaw/pkg/skills"
	"github.com/Perdonus/ruclaw/pkg/utils"
)

const skillsSearchMaxResults = 20

func resolveBuiltinSkillsDir(baseDir string) string {
	candidates := []string{
		filepath.Join(baseDir, "skills"),
		filepath.Join(baseDir, "ruclaw", "skills"),
		filepath.Join(baseDir, "picoclaw", "skills"),
	}

	if baseDir == "." || baseDir == "" {
		candidates = append([]string{filepath.Join(baseDir, "workspace", "skills")}, candidates...)
	}

	for _, candidate := range candidates {
		info, err := os.Stat(candidate)
		if err == nil && info.IsDir() {
			return candidate
		}
	}

	return candidates[0]
}

func extractSkillDescription(data []byte) string {
	lines := strings.Split(string(data), "\n")
	if len(lines) > 0 && strings.TrimSpace(lines[0]) == "---" {
		for _, line := range lines[1:] {
			trimmed := strings.TrimSpace(line)
			if trimmed == "---" {
				break
			}
			if idx := strings.Index(trimmed, ":"); idx > 0 && strings.EqualFold(strings.TrimSpace(trimmed[:idx]), "description") {
				return strings.TrimSpace(trimmed[idx+1:])
			}
		}
	}

	for _, line := range lines {
		trimmed := strings.TrimSpace(strings.TrimLeft(line, "# "))
		if trimmed != "" {
			return trimmed
		}
	}

	return ""
}

func skillsListCmd(loader *skills.SkillsLoader) {
	allSkills := loader.ListSkills()

	if len(allSkills) == 0 {
		fmt.Println("Установленных навыков нет.")
		return
	}

	fmt.Println("\nУстановленные навыки:")
	fmt.Println("---------------------")
	for _, skill := range allSkills {
		fmt.Printf("  ✓ %s (%s)\n", skill.Name, skill.Source)
		if skill.Description != "" {
			fmt.Printf("    %s\n", skill.Description)
		}
	}
}

func skillsInstallCmd(installer *skills.SkillInstaller, repo string) error {
	fmt.Printf("Устанавливаю навык из %s...\n", repo)

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	if err := installer.InstallFromGitHub(ctx, repo); err != nil {
		return fmt.Errorf("не удалось установить навык: %w", err)
	}

	fmt.Printf("\u2713 Навык '%s' успешно установлен!\n", filepath.Base(repo))

	return nil
}

// skillsInstallFromRegistry installs a skill from a named registry (e.g. clawhub).
func skillsInstallFromRegistry(cfg *config.Config, registryName, slug string) error {
	err := utils.ValidateSkillIdentifier(registryName)
	if err != nil {
		return fmt.Errorf("✗  некорректное имя реестра: %w", err)
	}

	err = utils.ValidateSkillIdentifier(slug)
	if err != nil {
		return fmt.Errorf("✗  некорректный slug: %w", err)
	}

	fmt.Printf("Устанавливаю навык '%s' из реестра %s...\n", slug, registryName)

	clawHubConfig := cfg.Tools.Skills.Registries.ClawHub
	registryMgr := skills.NewRegistryManagerFromConfig(skills.RegistryConfig{
		MaxConcurrentSearches: cfg.Tools.Skills.MaxConcurrentSearches,
		ClawHub: skills.ClawHubConfig{
			Enabled:         clawHubConfig.Enabled,
			BaseURL:         clawHubConfig.BaseURL,
			AuthToken:       clawHubConfig.AuthToken.String(),
			SearchPath:      clawHubConfig.SearchPath,
			SkillsPath:      clawHubConfig.SkillsPath,
			DownloadPath:    clawHubConfig.DownloadPath,
			Timeout:         clawHubConfig.Timeout,
			MaxZipSize:      clawHubConfig.MaxZipSize,
			MaxResponseSize: clawHubConfig.MaxResponseSize,
		},
	})

	registry := registryMgr.GetRegistry(registryName)
	if registry == nil {
		return fmt.Errorf("✗  реестр '%s' не найден или не включён. Проверьте конфигурацию RuClaw.", registryName)
	}

	workspace := cfg.WorkspacePath()
	targetDir := filepath.Join(workspace, "skills", slug)

	if _, err = os.Stat(targetDir); err == nil {
		return fmt.Errorf("\u2717 навык '%s' уже установлен в %s", slug, targetDir)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	if err = os.MkdirAll(filepath.Join(workspace, "skills"), 0o755); err != nil {
		return fmt.Errorf("\u2717 не удалось создать каталог навыков: %v", err)
	}

	result, err := registry.DownloadAndInstall(ctx, slug, "", targetDir)
	if err != nil {
		rmErr := os.RemoveAll(targetDir)
		if rmErr != nil {
			fmt.Printf("\u2717 Не удалось удалить частично установленный навык: %v\n", rmErr)
		}
		return fmt.Errorf("✗ не удалось установить навык: %w", err)
	}

	if result.IsMalwareBlocked {
		rmErr := os.RemoveAll(targetDir)
		if rmErr != nil {
			fmt.Printf("\u2717 Не удалось удалить частично установленный навык: %v\n", rmErr)
		}

		return fmt.Errorf("\u2717 Навык '%s' помечен как вредоносный и не может быть установлен.\n", slug)
	}

	if result.IsSuspicious {
		fmt.Printf("\u26a0\ufe0f  Предупреждение: навык '%s' помечен как подозрительный.\n", slug)
	}

	fmt.Printf("\u2713 Навык '%s' v%s успешно установлен!\n", slug, result.Version)
	if result.Summary != "" {
		fmt.Printf("  %s\n", result.Summary)
	}

	return nil
}

func skillsRemoveCmd(installer *skills.SkillInstaller, skillName string) {
	fmt.Printf("Удаляю навык '%s'...\n", skillName)

	if err := installer.Uninstall(skillName); err != nil {
		fmt.Printf("✗ Не удалось удалить навык: %v\n", err)
		os.Exit(1)
	}

	fmt.Printf("✓ Навык '%s' успешно удалён!\n", skillName)
}

func skillsInstallBuiltinCmd(workspace string) {
	builtinSkillsDir := resolveBuiltinSkillsDir(filepath.Dir(workspace))
	if info, err := os.Stat(builtinSkillsDir); err != nil || !info.IsDir() {
		builtinSkillsDir = resolveBuiltinSkillsDir(".")
	}
	workspaceSkillsDir := filepath.Join(workspace, "skills")

	fmt.Printf("Копирую встроенные навыки в рабочее пространство RuClaw...\n")

	skillsToInstall := []string{
		"weather",
		"news",
		"stock",
		"calculator",
	}

	for _, skillName := range skillsToInstall {
		builtinPath := filepath.Join(builtinSkillsDir, skillName)
		workspacePath := filepath.Join(workspaceSkillsDir, skillName)

		if _, err := os.Stat(builtinPath); err != nil {
			fmt.Printf("⊘ Встроенный навык '%s' не найден: %v\n", skillName, err)
			continue
		}

		if err := os.MkdirAll(workspacePath, 0o755); err != nil {
			fmt.Printf("✗ Не удалось создать каталог для %s: %v\n", skillName, err)
			continue
		}

		if err := copyDirectory(builtinPath, workspacePath); err != nil {
			fmt.Printf("✗ Не удалось скопировать %s: %v\n", skillName, err)
		}
	}

	fmt.Println("\n✓ Все встроенные навыки установлены!")
	fmt.Println("Теперь их можно использовать в рабочем пространстве RuClaw.")
}

func skillsListBuiltinCmd() {
	cfg, err := internal.LoadConfig()
	if err != nil {
		fmt.Printf("Ошибка загрузки конфигурации: %v\n", err)
		return
	}
	builtinSkillsDir := resolveBuiltinSkillsDir(filepath.Dir(cfg.WorkspacePath()))
	if info, statErr := os.Stat(builtinSkillsDir); statErr != nil || !info.IsDir() {
		builtinSkillsDir = resolveBuiltinSkillsDir(".")
	}

	fmt.Println("\nДоступные встроенные навыки:")
	fmt.Println("----------------------------")

	entries, err := os.ReadDir(builtinSkillsDir)
	if err != nil {
		fmt.Printf("Ошибка чтения встроенных навыков: %v\n", err)
		return
	}

	if len(entries) == 0 {
		fmt.Println("Встроенных навыков нет.")
		return
	}

	for _, entry := range entries {
		if entry.IsDir() {
			skillName := entry.Name()
			skillFile := filepath.Join(builtinSkillsDir, skillName, "SKILL.md")

			description := "Без описания"
			if _, err := os.Stat(skillFile); err == nil {
				data, err := os.ReadFile(skillFile)
				if err == nil {
					if desc := extractSkillDescription(data); desc != "" {
						description = desc
					}
				}
			}
			status := "✓"
			fmt.Printf("  %s  %s\n", status, entry.Name())
			if description != "" {
				fmt.Printf("     %s\n", description)
			}
		}
	}
}

func skillsSearchCmd(query string) {
	fmt.Println("Ищу доступные навыки для RuClaw...")

	cfg, err := internal.LoadConfig()
	if err != nil {
		fmt.Printf("✗ Не удалось загрузить конфигурацию: %v\n", err)
		return
	}

	clawHubConfig := cfg.Tools.Skills.Registries.ClawHub
	registryMgr := skills.NewRegistryManagerFromConfig(skills.RegistryConfig{
		MaxConcurrentSearches: cfg.Tools.Skills.MaxConcurrentSearches,
		ClawHub: skills.ClawHubConfig{
			Enabled:         clawHubConfig.Enabled,
			BaseURL:         clawHubConfig.BaseURL,
			AuthToken:       clawHubConfig.AuthToken.String(),
			SearchPath:      clawHubConfig.SearchPath,
			SkillsPath:      clawHubConfig.SkillsPath,
			DownloadPath:    clawHubConfig.DownloadPath,
			Timeout:         clawHubConfig.Timeout,
			MaxZipSize:      clawHubConfig.MaxZipSize,
			MaxResponseSize: clawHubConfig.MaxResponseSize,
		},
	})

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	results, err := registryMgr.SearchAll(ctx, query, skillsSearchMaxResults)
	if err != nil {
		fmt.Printf("✗ Не удалось получить список навыков: %v\n", err)
		return
	}

	if len(results) == 0 {
		fmt.Println("Доступных навыков нет.")
		return
	}

	fmt.Printf("\nДоступные навыки (%d):\n", len(results))
	fmt.Println("----------------------")
	for _, result := range results {
		fmt.Printf("  📦 %s\n", result.DisplayName)
		fmt.Printf("     %s\n", result.Summary)
		fmt.Printf("     Slug: %s\n", result.Slug)
		fmt.Printf("     Реестр: %s\n", result.RegistryName)
		if result.Version != "" {
			fmt.Printf("     Версия: %s\n", result.Version)
		}
		fmt.Println()
	}
}

func skillsShowCmd(loader *skills.SkillsLoader, skillName string) {
	content, ok := loader.LoadSkill(skillName)
	if !ok {
		fmt.Printf("✗ Навык '%s' не найден\n", skillName)
		return
	}

	fmt.Printf("\n📦 Навык: %s\n", skillName)
	fmt.Println("----------------------")
	fmt.Println(content)
}

func copyDirectory(src, dst string) error {
	return filepath.Walk(src, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}

		relPath, err := filepath.Rel(src, path)
		if err != nil {
			return err
		}

		dstPath := filepath.Join(dst, relPath)

		if info.IsDir() {
			return os.MkdirAll(dstPath, info.Mode())
		}

		srcFile, err := os.Open(path)
		if err != nil {
			return err
		}
		defer srcFile.Close()

		dstFile, err := os.OpenFile(dstPath, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, info.Mode())
		if err != nil {
			return err
		}
		defer dstFile.Close()

		_, err = io.Copy(dstFile, srcFile)
		return err
	})
}
