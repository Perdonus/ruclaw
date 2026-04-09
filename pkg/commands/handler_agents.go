package commands

import (
	"context"
	"fmt"
	"strings"
)

// agentsHandler returns a shared handler for both /show agents and /list agents.
func agentsHandler() Handler {
	return func(_ context.Context, req Request, rt *Runtime) error {
		if rt == nil || rt.ListAgentIDs == nil {
			return req.Reply(unavailableMsg)
		}
		ids := rt.ListAgentIDs()
		if len(ids) == 0 {
			return req.Reply("В RuClaw нет зарегистрированных агентов.")
		}
		return req.Reply(fmt.Sprintf("Зарегистрированные агенты RuClaw: %s", strings.Join(ids, ", ")))
	}
}
