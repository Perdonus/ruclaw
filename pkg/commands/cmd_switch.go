package commands

import (
	"context"
	"fmt"
)

func switchCommand() Definition {
	return Definition{
		Name:        "switch",
		Description: "Переключить модель RuClaw",
		SubCommands: []SubCommand{
			{
				Name:        "model",
				Description: "Переключить RuClaw на другую модель",
				ArgsUsage:   "to <модель>",
				Handler: func(_ context.Context, req Request, rt *Runtime) error {
					if rt == nil || rt.SwitchModel == nil {
						return req.Reply(unavailableMsg)
					}
					// Parse: /switch model to <value>
					value := nthToken(req.Text, 3) // tokens: [/switch, model, to, <value>]
					if nthToken(req.Text, 2) != "to" || value == "" {
						return req.Reply("Использование: /switch model to <модель>")
					}
					oldModel, err := rt.SwitchModel(value)
					if err != nil {
						return req.Reply(err.Error())
					}
					return req.Reply(fmt.Sprintf("Модель RuClaw переключена: %s -> %s.", oldModel, value))
				},
			},
			{
				Name:        "channel",
				Description: "Канал перенесён в /check channel",
				Handler: func(_ context.Context, req Request, _ *Runtime) error {
					return req.Reply("Команда /switch channel устарела. Используйте: /check channel <канал>")
				},
			},
		},
	}
}
