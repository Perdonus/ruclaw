package cron

import "github.com/spf13/cobra"

func newRemoveCommand(storePath func() string) *cobra.Command {
	cmd := &cobra.Command{
		Use:     "remove",
		Short:   "Удалить задачу по ID",
		Args:    cobra.ExactArgs(1),
		Example: `ruclaw cron remove 1`,
		RunE: func(_ *cobra.Command, args []string) error {
			cronRemoveCmd(storePath(), args[0])
			return nil
		},
	}

	return cmd
}
