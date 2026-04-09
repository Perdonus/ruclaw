package commands

import (
	"context"
	"fmt"
)

func showCommand() Definition {
	return Definition{
		Name:        "show",
		Description: "Показать текущее состояние RuClaw",
		SubCommands: []SubCommand{
			{
				Name:        "model",
				Description: "Активная модель и провайдер",
				Handler: func(_ context.Context, req Request, rt *Runtime) error {
					if rt == nil || rt.GetModelInfo == nil {
						return req.Reply(unavailableMsg)
					}
					name, provider := rt.GetModelInfo()
					if name == "" {
						name = "не выбрана"
					}
					if provider == "" {
						provider = "не указан"
					}
					return req.Reply(fmt.Sprintf("Активная модель: %s (провайдер: %s)", name, provider))
				},
			},
			{
				Name:        "channel",
				Description: "Текущий канал",
				Handler: func(_ context.Context, req Request, _ *Runtime) error {
					return req.Reply(fmt.Sprintf("Текущий канал: %s", req.Channel))
				},
			},
			{
				Name:        "agents",
				Description: "Доступные агенты",
				Handler:     agentsHandler(),
			},
		},
	}
}
