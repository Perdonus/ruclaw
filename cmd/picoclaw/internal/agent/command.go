package agent

import (
	"github.com/spf13/cobra"
)

func NewAgentCommand() *cobra.Command {
	var (
		message    string
		sessionKey string
		model      string
		debug      bool
	)

	cmd := &cobra.Command{
		Use:   "agent",
		Short: "Напрямую общаться с агентом",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return agentCmd(message, sessionKey, model, debug)
		},
	}

	cmd.Flags().BoolVarP(&debug, "debug", "d", false, "Включить отладочный лог")
	cmd.Flags().StringVarP(&message, "message", "m", "", "Отправить одно сообщение (неинтерактивный режим)")
	cmd.Flags().StringVarP(&sessionKey, "session", "s", "cli:default", "Ключ сессии")
	cmd.Flags().StringVarP(&model, "model", "", "", "Модель для использования")

	return cmd
}
