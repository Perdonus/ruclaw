package migrate

import (
	"github.com/spf13/cobra"

	"github.com/Perdonus/ruclaw/pkg/migrate"
)

func NewMigrateCommand() *cobra.Command {
	var opts migrate.Options

	cmd := &cobra.Command{
		Use:   "migrate",
		Short: "Перенести данные из xxxclaw (openclaw и т. п.) в RuClaw",
		Args:  cobra.NoArgs,
		Example: `  ruclaw migrate
  ruclaw migrate --from openclaw
  ruclaw migrate --dry-run
  ruclaw migrate --refresh
  ruclaw migrate --force`,
		RunE: func(cmd *cobra.Command, _ []string) error {
			m := migrate.NewMigrateInstance(opts)
			result, err := m.Run(opts)
			if err != nil {
				return err
			}
			if !opts.DryRun {
				m.PrintSummary(result)
			}
			return nil
		},
	}

	cmd.Flags().BoolVar(&opts.DryRun, "dry-run", false,
		"Показать, что будет перенесено, не внося изменений")
	cmd.Flags().StringVar(&opts.Source, "from", "openclaw",
		"Источник миграции (например, openclaw)")
	cmd.Flags().BoolVar(&opts.Refresh, "refresh", false,
		"Повторно синхронизировать файлы рабочего пространства из OpenClaw")
	cmd.Flags().BoolVar(&opts.ConfigOnly, "config-only", false,
		"Перенести только config, без файлов рабочего пространства")
	cmd.Flags().BoolVar(&opts.WorkspaceOnly, "workspace-only", false,
		"Перенести только рабочее пространство, без config")
	cmd.Flags().BoolVar(&opts.Force, "force", false,
		"Не задавать подтверждающих вопросов")
	cmd.Flags().StringVar(&opts.SourceHome, "source-home", "",
		"Переопределить домашний каталог источника (по умолчанию: ~/.openclaw)")
	cmd.Flags().StringVar(&opts.TargetHome, "target-home", "",
		"Переопределить домашний каталог назначения (по умолчанию: ~/.picoclaw)")

	return cmd
}
