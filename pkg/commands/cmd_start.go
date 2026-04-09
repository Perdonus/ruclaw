package commands

import "context"

func startCommand() Definition {
	return Definition{
		Name:        "start",
		Description: "Начать работу с RuClaw",
		Usage:       "/start",
		Handler: func(_ context.Context, req Request, _ *Runtime) error {
			return req.Reply("Привет! Я RuClaw 🦞. Напишите /help, чтобы увидеть доступные команды.")
		},
	}
}
