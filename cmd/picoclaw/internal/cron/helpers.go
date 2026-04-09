package cron

import (
	"fmt"
	"time"

	"github.com/Perdonus/ruclaw/pkg/cron"
)

func cronListCmd(storePath string) {
	cs := cron.NewCronService(storePath, nil)
	jobs := cs.ListJobs(true) // Show all jobs, including disabled

	if len(jobs) == 0 {
		fmt.Println("Задач по расписанию нет.")
		return
	}

	fmt.Println("\nЗадачи по расписанию:")
	fmt.Println("---------------------")
	for _, job := range jobs {
		var schedule string
		if job.Schedule.Kind == "every" && job.Schedule.EveryMS != nil {
			schedule = fmt.Sprintf("каждые %d c", *job.Schedule.EveryMS/1000)
		} else if job.Schedule.Kind == "cron" {
			schedule = job.Schedule.Expr
		} else {
			schedule = "одноразово"
		}

		nextRun := "запланировано"
		if job.State.NextRunAtMS != nil {
			nextTime := time.UnixMilli(*job.State.NextRunAtMS)
			nextRun = nextTime.Format("2006-01-02 15:04")
		}

		status := "включена"
		if !job.Enabled {
			status = "отключена"
		}

		fmt.Printf("  %s (%s)\n", job.Name, job.ID)
		fmt.Printf("    Расписание: %s\n", schedule)
		fmt.Printf("    Статус: %s\n", status)
		fmt.Printf("    Следующий запуск: %s\n", nextRun)
	}
}

func cronRemoveCmd(storePath, jobID string) {
	cs := cron.NewCronService(storePath, nil)
	if cs.RemoveJob(jobID) {
		fmt.Printf("✓ Задача %s удалена\n", jobID)
	} else {
		fmt.Printf("✗ Задача %s не найдена\n", jobID)
	}
}

func cronSetJobEnabled(storePath, jobID string, enabled bool) {
	cs := cron.NewCronService(storePath, nil)
	job := cs.EnableJob(jobID, enabled)
	if job != nil {
		action := "включена"
		if !enabled {
			action = "отключена"
		}
		fmt.Printf("✓ Задача '%s' %s\n", job.Name, action)
	} else {
		fmt.Printf("✗ Задача %s не найдена\n", jobID)
	}
}
