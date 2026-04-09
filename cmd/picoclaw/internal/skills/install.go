package skills

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/Perdonus/ruclaw/cmd/picoclaw/internal"
	"github.com/Perdonus/ruclaw/pkg/skills"
)

func newInstallCommand(installerFn func() (*skills.SkillInstaller, error)) *cobra.Command {
	var registry string

	cmd := &cobra.Command{
		Use:   "install",
		Short: "Установить навык из GitHub",
		Example: `
ruclaw skills install Perdonus/ruclaw-skills/weather
ruclaw skills install --registry clawhub github
`,
		Args: func(cmd *cobra.Command, args []string) error {
			if registry != "" {
				if len(args) != 1 {
					return fmt.Errorf("если указан --registry, требуется ровно 1 аргумент: <slug>")
				}
				return nil
			}

			if len(args) != 1 {
				return fmt.Errorf("требуется ровно 1 аргумент: <github>")
			}

			return nil
		},
		RunE: func(_ *cobra.Command, args []string) error {
			installer, err := installerFn()
			if err != nil {
				return err
			}

			if registry != "" {
				cfg, err := internal.LoadConfig()
				if err != nil {
					return err
				}

				return skillsInstallFromRegistry(cfg, registry, args[0])
			}

			return skillsInstallCmd(installer, args[0])
		},
	}

	cmd.Flags().StringVar(&registry, "registry", "", "Установить из реестра: --registry <name> <slug>")

	return cmd
}
