package auth

import "github.com/spf13/cobra"

func newLogoutCommand() *cobra.Command {
	var provider string

	cmd := &cobra.Command{
		Use:   "logout",
		Short: "Удалить сохранённые учётные данные",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return authLogoutCmd(provider)
		},
	}

	cmd.Flags().StringVarP(&provider, "provider", "p", "", "Провайдер, из которого нужно выйти (openai, anthropic); пусто = все")

	return cmd
}
