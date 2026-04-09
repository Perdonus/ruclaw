package commands

import (
	"context"
	"fmt"
)

// TurnInfo is a mirrored struct from agent.TurnInfo to avoid circular dependencies.
type TurnInfo struct {
	TurnID       string
	ParentTurnID string
	Depth        int
	ChildTurnIDs []string
	IsFinished   bool
}

func subagentsCommand() Definition {
	return Definition{
		Name:        "subagents",
		Description: "Показать активных подагентов и дерево задач",
		Handler: func(ctx context.Context, req Request, rt *Runtime) error {
			getTurnFn := rt.GetActiveTurn
			if getTurnFn == nil {
				return req.Reply("Текущая среда выполнения не поддерживает просмотр активных задач.")
			}

			turnRaw := getTurnFn()
			if turnRaw == nil {
				return req.Reply("В этой сессии нет активных подагентов.")
			}

			if treeStr, ok := turnRaw.(string); ok {
				if treeStr == "" {
					return req.Reply("В этой сессии нет активных подагентов.")
				}
				return req.Reply(fmt.Sprintf("🦞 **Дерево задач RuClaw**\n```text\n%s\n```", treeStr))
			}

			return req.Reply(fmt.Sprintf("🦞 **Активные подагенты RuClaw**\n```text\n%+v\n```", turnRaw))
		},
	}
}
