package commands

import (
	"context"
	"fmt"
	"strings"
)

func listCommand() Definition {
	return Definition{
		Name:        "list",
		Description: "Показать доступные списки",
		SubCommands: []SubCommand{
			{
				Name:        "models",
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
						provider = "по умолчанию из config.json"
					}
					return req.Reply(fmt.Sprintf(
						"Активная модель RuClaw: %s\nПровайдер: %s\n\nЧтобы изменить список моделей, обновите config.json.",
						name, provider,
					))
				},
			},
			{
				Name:        "channels",
				Description: "Включённые каналы",
				Handler: func(_ context.Context, req Request, rt *Runtime) error {
					if rt == nil || rt.GetEnabledChannels == nil {
						return req.Reply(unavailableMsg)
					}
					enabled := rt.GetEnabledChannels()
					if len(enabled) == 0 {
						return req.Reply("В RuClaw нет включённых каналов.")
					}
					return req.Reply(fmt.Sprintf("Включённые каналы RuClaw:\n- %s", strings.Join(enabled, "\n- ")))
				},
			},
			{
				Name:        "agents",
				Description: "Доступные агенты",
				Handler:     agentsHandler(),
			},
			{
				Name:        "skills",
				Description: "Установленные навыки",
				Handler: func(_ context.Context, req Request, rt *Runtime) error {
					if rt == nil || rt.ListSkillNames == nil {
						return req.Reply(unavailableMsg)
					}
					names := rt.ListSkillNames()
					if len(names) == 0 {
						return req.Reply("В RuClaw нет установленных навыков.")
					}
					return req.Reply(fmt.Sprintf(
						"Установленные навыки RuClaw:\n- %s\n\nИспользуйте /use <skill> <message>, чтобы принудительно применить навык к одному запросу, или /use <skill>, чтобы применить его к следующему сообщению.",
						strings.Join(names, "\n- "),
					))
				},
			},
		},
	}
}
