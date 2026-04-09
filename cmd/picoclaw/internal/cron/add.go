package cron

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/Perdonus/ruclaw/pkg/cron"
)

func newAddCommand(storePath func() string) *cobra.Command {
	var (
		name    string
		message string
		every   int64
		cronExp string
		channel string
		to      string
	)

	cmd := &cobra.Command{
		Use:   "add",
		Short: "Добавить новую задачу по расписанию",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			if every <= 0 && cronExp == "" {
				return fmt.Errorf("нужно указать либо --every, либо --cron")
			}

			var schedule cron.CronSchedule
			if every > 0 {
				everyMS := every * 1000
				schedule = cron.CronSchedule{Kind: "every", EveryMS: &everyMS}
			} else {
				schedule = cron.CronSchedule{Kind: "cron", Expr: cronExp}
			}

			cs := cron.NewCronService(storePath(), nil)
			job, err := cs.AddJob(name, schedule, message, channel, to)
			if err != nil {
				return fmt.Errorf("ошибка при добавлении задачи: %w", err)
			}

			fmt.Printf("✓ Задача '%s' добавлена (%s)\n", job.Name, job.ID)

			return nil
		},
	}

	cmd.Flags().StringVarP(&name, "name", "n", "", "Имя задачи")
	cmd.Flags().StringVarP(&message, "message", "m", "", "Сообщение для агента")
	cmd.Flags().Int64VarP(&every, "every", "e", 0, "Запускать каждые N секунд")
	cmd.Flags().StringVarP(&cronExp, "cron", "c", "", "Cron-выражение (например, '0 9 * * *')")
	cmd.Flags().StringVar(&to, "to", "", "Получатель доставки")
	cmd.Flags().StringVar(&channel, "channel", "", "Канал доставки")

	_ = cmd.MarkFlagRequired("name")
	_ = cmd.MarkFlagRequired("message")
	cmd.MarkFlagsMutuallyExclusive("every", "cron")

	return cmd
}
