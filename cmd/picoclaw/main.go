// RuClaw - Ultra-lightweight personal AI agent
// Inspired by and based on nanobot: https://github.com/HKUDS/nanobot
// License: MIT
//
// Copyright (c) 2026 RuClaw contributors

package main

import (
	"fmt"
	"os"
	"time"

	"github.com/spf13/cobra"

	"github.com/Perdonus/ruclaw/cmd/picoclaw/internal"
	"github.com/Perdonus/ruclaw/cmd/picoclaw/internal/agent"
	"github.com/Perdonus/ruclaw/cmd/picoclaw/internal/auth"
	"github.com/Perdonus/ruclaw/cmd/picoclaw/internal/cron"
	"github.com/Perdonus/ruclaw/cmd/picoclaw/internal/gateway"
	"github.com/Perdonus/ruclaw/cmd/picoclaw/internal/migrate"
	"github.com/Perdonus/ruclaw/cmd/picoclaw/internal/model"
	"github.com/Perdonus/ruclaw/cmd/picoclaw/internal/onboard"
	"github.com/Perdonus/ruclaw/cmd/picoclaw/internal/skills"
	"github.com/Perdonus/ruclaw/cmd/picoclaw/internal/status"
	"github.com/Perdonus/ruclaw/cmd/picoclaw/internal/version"
	"github.com/Perdonus/ruclaw/pkg/config"
	"github.com/Perdonus/ruclaw/pkg/updater"
)

func NewPicoclawCommand() *cobra.Command {
	short := fmt.Sprintf("%s RuClaw - Персональный ИИ-ассистент %s\n\n", internal.Logo, config.GetVersion())

	cmd := &cobra.Command{
		Use:     "ruclaw",
		Short:   short,
		Example: "ruclaw version",
	}

	cmd.AddCommand(
		onboard.NewOnboardCommand(),
		agent.NewAgentCommand(),
		auth.NewAuthCommand(),
		gateway.NewGatewayCommand(),
		status.NewStatusCommand(),
		cron.NewCronCommand(),
		migrate.NewMigrateCommand(),
		skills.NewSkillsCommand(),
		model.NewModelCommand(),
		updater.NewUpdateCommand("ruclaw"),
		version.NewVersionCommand(),
	)

	return cmd
}

const (
	colorBlue = "\033[1;38;2;62;93;185m"
	colorRed  = "\033[1;38;2;213;70;70m"
	banner    = "\r\n" +
		colorBlue + "██████╗ ██╗   ██╗ ██████╗██╗      █████╗ ██╗    ██╗\n" +
		colorBlue + "██╔══██╗██║   ██║██╔════╝██║     ██╔══██╗██║    ██║\n" +
		colorBlue + "██████╔╝██║   ██║██║     ██║     ███████║██║ █╗ ██║\n" +
		colorBlue + "██╔══██╗██║   ██║██║     ██║     ██╔══██║██║███╗██║\n" +
		colorBlue + "██║  ██║╚██████╔╝╚██████╗███████╗██║  ██║╚███╔███╔╝\n" +
		colorBlue + "╚═╝  ╚═╝ ╚═════╝  ╚═════╝╚══════╝╚═╝  ╚═╝ ╚══╝╚══╝\n" +
		colorRed + "                     RuClaw\n " +
		"\033[0m\r\n"
)

func main() {
	fmt.Printf("%s", banner)

	tzEnv := os.Getenv("TZ")
	if tzEnv != "" {
		fmt.Println("Переменная TZ:", tzEnv)
		zoneinfoEnv := os.Getenv("ZONEINFO")
		fmt.Println("Переменная ZONEINFO:", zoneinfoEnv)
		loc, err := time.LoadLocation(tzEnv)
		if err != nil {
			fmt.Println("Не удалось загрузить часовой пояс:", err)
		} else {
			fmt.Println("Часовой пояс успешно загружен:", loc)
			time.Local = loc //nolint:gosmopolitan // We intentionally set local timezone from TZ env
		}
	}

	cmd := NewPicoclawCommand()
	if err := cmd.Execute(); err != nil {
		os.Exit(1)
	}
}
