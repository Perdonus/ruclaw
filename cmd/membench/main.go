package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"strings"

	"github.com/spf13/cobra"

	"github.com/Perdonus/ruclaw/pkg/logger"
)

var (
	flagData   string
	flagOut    string
	flagMode   string
	flagBudget int
)

func main() {
	// Suppress seahorse INFO logs during benchmark
	logger.SetLevel(logger.WARN)

	rootCmd := &cobra.Command{
		Use:   "membench",
		Short: "Инструмент бенчмарка памяти для RuClaw",
	}

	ingestCmd := &cobra.Command{
		Use:   "ingest",
		Short: "Загрузить данные LOCOMO в бэкенды хранения",
		RunE:  runIngest,
	}
	ingestCmd.Flags().StringVar(&flagData, "data", "", "Каталог датасета LOCOMO (обязательно)")
	ingestCmd.Flags().StringVar(&flagOut, "out", "./bench-out", "Рабочий каталог для вывода")
	ingestCmd.Flags().StringVar(&flagMode, "mode", "all", "Режимы загрузки: legacy, seahorse или all")

	evalCmd := &cobra.Command{
		Use:   "eval",
		Short: "Запустить QA-оценку на загруженных данных",
		RunE:  runEval,
	}
	evalCmd.Flags().StringVar(&flagData, "data", "", "Каталог датасета LOCOMO (обязательно)")
	evalCmd.Flags().StringVar(&flagOut, "out", "./bench-out", "Рабочий каталог для вывода")
	evalCmd.Flags().StringVar(&flagMode, "mode", "all", "Режимы оценки: legacy, seahorse или all")
	evalCmd.Flags().IntVar(&flagBudget, "budget", 4000, "Токенный бюджет для retrieval")

	reportCmd := &cobra.Command{
		Use:   "report",
		Short: "Показать результаты сравнения из оценки",
		RunE:  runReport,
	}
	reportCmd.Flags().StringVar(&flagOut, "out", "./bench-out", "Рабочий каталог для вывода")

	runCmd := &cobra.Command{
		Use:   "run",
		Short: "Упрощённый режим: eval + report (загрузка выполняется внутри)",
		RunE:  runAll,
	}
	runCmd.Flags().StringVar(&flagData, "data", "", "Каталог датасета LOCOMO (обязательно)")
	runCmd.Flags().StringVar(&flagOut, "out", "./bench-out", "Рабочий каталог для вывода")
	runCmd.Flags().StringVar(&flagMode, "mode", "all", "Режимы запуска: legacy, seahorse или all")
	runCmd.Flags().IntVar(&flagBudget, "budget", 4000, "Токенный бюджет для retrieval")

	rootCmd.AddCommand(ingestCmd, evalCmd, reportCmd, runCmd)

	if err := rootCmd.Execute(); err != nil {
		os.Exit(1)
	}
}

func modesFromFlag() []string {
	switch strings.ToLower(flagMode) {
	case "all":
		return []string{"legacy", "seahorse"}
	default:
		return []string{strings.ToLower(flagMode)}
	}
}

func runIngest(cmd *cobra.Command, args []string) error {
	if flagData == "" {
		return fmt.Errorf("нужно указать --data")
	}
	modes := modesFromFlag()
	if len(modes) == 0 {
		return nil
	}

	ctx := context.Background()
	samples, err := LoadDataset(flagData)
	if err != nil {
		return fmt.Errorf("загрузить датасет: %w", err)
	}
	log.Printf("Загружено %d сэмплов из %s", len(samples), flagData)

	for _, mode := range modes {
		switch mode {
		case "legacy":
			legacy := NewLegacyStore()
			for i := range samples {
				legacy.IngestSample(&samples[i])
			}
			log.Printf("legacy: загружено %d сэмплов", len(samples))
		case "seahorse":
			dbPath := filepath.Join(flagOut, "seahorse.db")
			if err := os.MkdirAll(flagOut, 0o755); err != nil {
				return fmt.Errorf("создать каталог вывода: %w", err)
			}
			_, err := IngestSeahorse(ctx, samples, dbPath)
			if err != nil {
				return fmt.Errorf("загрузка в seahorse: %w", err)
			}
		}
	}
	return nil
}

func runEval(cmd *cobra.Command, args []string) error {
	if flagData == "" {
		return fmt.Errorf("нужно указать --data")
	}
	modes := modesFromFlag()
	if len(modes) == 0 {
		return nil
	}

	ctx := context.Background()
	samples, err := LoadDataset(flagData)
	if err != nil {
		return fmt.Errorf("загрузить датасет: %w", err)
	}
	log.Printf("Загружено %d сэмплов", len(samples))

	var allResults []EvalResult

	for _, mode := range modes {
		switch mode {
		case "legacy":
			legacy := NewLegacyStore()
			for i := range samples {
				legacy.IngestSample(&samples[i])
			}
			results := EvalLegacy(ctx, samples, legacy, flagBudget)
			allResults = append(allResults, results...)
			log.Printf("legacy: оценено %d сэмплов", len(results))
		case "seahorse":
			dbPath := filepath.Join(flagOut, "seahorse.db")
			ir, err := IngestSeahorse(ctx, samples, dbPath)
			if err != nil {
				return fmt.Errorf("загрузка в seahorse: %w", err)
			}
			results := EvalSeahorse(ctx, samples, ir, flagBudget)
			allResults = append(allResults, results...)
			log.Printf("seahorse: оценено %d сэмплов", len(results))
		}
	}

	if err := SaveResults(allResults, flagOut); err != nil {
		return fmt.Errorf("сохранить результаты: %w", err)
	}
	if err := SaveAggregated(allResults, flagOut); err != nil {
		return fmt.Errorf("сохранить агрегированные результаты: %w", err)
	}

	PrintComparison(allResults, nil)
	return nil
}

func runReport(cmd *cobra.Command, args []string) error {
	entries, err := os.ReadDir(flagOut)
	if err != nil {
		return fmt.Errorf("прочитать каталог вывода: %w", err)
	}

	var allResults []EvalResult
	for _, entry := range entries {
		if !entry.IsDir() && strings.HasPrefix(entry.Name(), "eval_") && strings.HasSuffix(entry.Name(), ".json") {
			path := filepath.Join(flagOut, entry.Name())
			var r EvalResult
			data, err := os.ReadFile(path)
			if err != nil {
				log.Printf("ПРЕДУПРЕЖДЕНИЕ: чтение %s: %v", path, err)
				continue
			}
			if err := json.Unmarshal(data, &r); err != nil {
				log.Printf("ПРЕДУПРЕЖДЕНИЕ: разбор %s: %v", path, err)
				continue
			}
			allResults = append(allResults, r)
		}
	}

	if len(allResults) == 0 {
		return fmt.Errorf("в %s не найдены результаты eval", flagOut)
	}

	PrintComparison(allResults, nil)
	return nil
}

func runAll(cmd *cobra.Command, args []string) error {
	return runEval(cmd, args)
}
