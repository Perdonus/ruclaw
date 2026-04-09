package commands

import "context"

func reloadCommand() Definition {
	return Definition{
		Name:        "reload",
		Description: "Перезагрузить конфигурацию RuClaw",
		Usage:       "/reload",
		Handler: func(_ context.Context, req Request, rt *Runtime) error {
			if rt == nil || rt.ReloadConfig == nil {
				return req.Reply(unavailableMsg)
			}
			if err := rt.ReloadConfig(); err != nil {
				return req.Reply("Не удалось перезагрузить конфигурацию RuClaw: " + err.Error())
			}
			return req.Reply("Перезагрузка конфигурации RuClaw запущена.")
		},
	}
}
