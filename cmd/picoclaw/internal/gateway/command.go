package gateway

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/Perdonus/ruclaw/cmd/picoclaw/internal"
	"github.com/Perdonus/ruclaw/pkg/gateway"
	"github.com/Perdonus/ruclaw/pkg/logger"
	"github.com/Perdonus/ruclaw/pkg/utils"
)

func NewGatewayCommand() *cobra.Command {
	var debug bool
	var noTruncate bool
	var allowEmpty bool

	cmd := &cobra.Command{
		Use:     "gateway",
		Aliases: []string{"g"},
		Short:   "Запустить gateway RuClaw",
		Args:    cobra.NoArgs,
		PreRunE: func(_ *cobra.Command, _ []string) error {
			if noTruncate && !debug {
				return fmt.Errorf("параметр --no-truncate можно использовать только вместе с --debug (-d)")
			}

			if noTruncate {
				utils.SetDisableTruncation(true)
				logger.Info("Обрезка строк глобально отключена флагом 'no-truncate'")
			}

			return nil
		},
		RunE: func(_ *cobra.Command, _ []string) error {
			return gateway.Run(debug, internal.GetPicoclawHome(), internal.GetConfigPath(), allowEmpty)
		},
	}

	cmd.Flags().BoolVarP(&debug, "debug", "d", false, "Включить отладочный лог")
	cmd.Flags().BoolVarP(&noTruncate, "no-truncate", "T", false, "Отключить обрезку строк в debug-логах")
	cmd.Flags().BoolVarP(
		&allowEmpty,
		"allow-empty",
		"E",
		false,
		"Продолжить запуск даже без модели по умолчанию",
	)

	return cmd
}
