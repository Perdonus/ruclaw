package skills

import (
	"github.com/spf13/cobra"

	"github.com/Perdonus/ruclaw/pkg/skills"
)

func newShowCommand(loaderFn func() (*skills.SkillsLoader, error)) *cobra.Command {
	cmd := &cobra.Command{
		Use:     "show",
		Short:   "Показать подробности навыка",
		Args:    cobra.ExactArgs(1),
		Example: `ruclaw skills show weather`,
		RunE: func(_ *cobra.Command, args []string) error {
			loader, err := loaderFn()
			if err != nil {
				return err
			}
			skillsShowCmd(loader, args[0])
			return nil
		},
	}

	return cmd
}
