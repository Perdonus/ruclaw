package auth

import "github.com/spf13/cobra"

func newLoginCommand() *cobra.Command {
	var (
		provider      string
		useDeviceCode bool
		useOauth      bool
	)

	cmd := &cobra.Command{
		Use:   "login",
		Short: "Войти через OAuth или вставить токен",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return authLoginCmd(provider, useDeviceCode, useOauth)
		},
	}

	cmd.Flags().StringVarP(&provider, "provider", "p", "", "Провайдер для входа (openai, anthropic, google-antigravity)")
	cmd.Flags().BoolVar(&useDeviceCode, "device-code", false, "Использовать Device Code для входа без браузера (headless-среды)")
	cmd.Flags().BoolVar(
		&useOauth, "setup-token", false,
		"Использовать режим setup-token для Anthropic (из `claude setup-token`)",
	)
	_ = cmd.MarkFlagRequired("provider")

	return cmd
}
