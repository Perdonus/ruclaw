package cron

import "github.com/spf13/cobra"

func newEnableCommand(storePath func() string) *cobra.Command {
	return &cobra.Command{
		Use:     "enable",
		Short:   "Включить задачу",
		Args:    cobra.ExactArgs(1),
		Example: `ruclaw cron enable 1`,
		RunE: func(_ *cobra.Command, args []string) error {
			cronSetJobEnabled(storePath(), args[0], true)
			return nil
		},
	}
}
