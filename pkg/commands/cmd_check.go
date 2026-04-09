package commands

import (
	"context"
	"fmt"
)

func checkCommand() Definition {
	return Definition{
		Name:        "check",
		Description: "Проверить доступность канала RuClaw",
		SubCommands: []SubCommand{
			{
				Name:        "channel",
				Description: "Проверить, включён ли канал",
				ArgsUsage:   "<канал>",
				Handler: func(_ context.Context, req Request, rt *Runtime) error {
					if rt == nil || rt.SwitchChannel == nil {
						return req.Reply(unavailableMsg)
					}
					value := nthToken(req.Text, 2)
					if value == "" {
						return req.Reply("Использование: /check channel <канал>")
					}
					if err := rt.SwitchChannel(value); err != nil {
						return req.Reply(err.Error())
					}
					return req.Reply(fmt.Sprintf("Канал '%s' доступен и включён в RuClaw.", value))
				},
			},
		},
	}
}
