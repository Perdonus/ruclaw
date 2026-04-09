package version

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/Perdonus/ruclaw/cmd/picoclaw/internal"
	"github.com/Perdonus/ruclaw/pkg/config"
)

func NewVersionCommand() *cobra.Command {
	cmd := &cobra.Command{
		Use:     "version",
		Aliases: []string{"v"},
		Short:   "Показать информацию о версии",
		Run: func(_ *cobra.Command, _ []string) {
			printVersion()
		},
	}

	return cmd
}

func printVersion() {
	fmt.Printf("%s RuClaw %s\n", internal.Logo, config.FormatVersion())
	build, goVer := config.FormatBuildInfo()
	if build != "" {
		fmt.Printf("  Сборка: %s\n", build)
	}
	if goVer != "" {
		fmt.Printf("  Go: %s\n", goVer)
	}
}
