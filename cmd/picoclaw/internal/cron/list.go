package cron

import "github.com/spf13/cobra"

func newListCommand(storePath func() string) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "list",
		Short: "Показать все задачи по расписанию",
		Args:  cobra.NoArgs,
		RunE: func(_ *cobra.Command, _ []string) error {
			cronListCmd(storePath())
			return nil
		},
	}

	return cmd
}
