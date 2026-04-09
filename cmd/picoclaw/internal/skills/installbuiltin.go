package skills

import "github.com/spf13/cobra"

func newInstallBuiltinCommand(workspaceFn func() (string, error)) *cobra.Command {
	cmd := &cobra.Command{
		Use:     "install-builtin",
		Short:   "Установить все встроенные навыки в рабочее пространство",
		Example: `ruclaw skills install-builtin`,
		RunE: func(_ *cobra.Command, _ []string) error {
			workspace, err := workspaceFn()
			if err != nil {
				return err
			}
			skillsInstallBuiltinCmd(workspace)
			return nil
		},
	}

	return cmd
}
