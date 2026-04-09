package commands

import (
	"context"
	"fmt"
	"strings"
)

func helpCommand() Definition {
	return Definition{
		Name:        "help",
		Description: "Показать список команд",
		Usage:       "/help",
		Handler: func(_ context.Context, req Request, rt *Runtime) error {
			var defs []Definition
			if rt != nil && rt.ListDefinitions != nil {
				defs = rt.ListDefinitions()
			} else {
				defs = BuiltinDefinitions()
			}
			return req.Reply(formatHelpMessage(defs))
		},
	}
}

func formatHelpMessage(defs []Definition) string {
	if len(defs) == 0 {
		return "В RuClaw нет доступных команд."
	}

	lines := make([]string, 0, len(defs))
	for _, def := range defs {
		usage := def.EffectiveUsage()
		if usage == "" {
			usage = "/" + def.Name
		}
		desc := def.Description
		if desc == "" {
			desc = "Описание пока не задано."
		}
		lines = append(lines, fmt.Sprintf("%s - %s", usage, desc))
	}
	return "Команды RuClaw:\n" + strings.Join(lines, "\n")
}
