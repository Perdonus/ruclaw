package agent

import "strings"

var transientLLMErrorSnippets = []string{
	"status: 429",
	"status: 500",
	"status: 502",
	"status: 503",
	"status: 504",
	"service unavailable",
	"internal_server_error",
	"temporarily unavailable",
	"try again later",
	"network is unreachable",
	"no route to host",
	"connection reset by peer",
	"broken pipe",
	"server closed idle connection",
	"unexpected eof",
	"eof",
}

func isTransientLLMError(errMsg string) bool {
	errMsg = strings.ToLower(strings.TrimSpace(errMsg))
	if errMsg == "" {
		return false
	}
	for _, snippet := range transientLLMErrorSnippets {
		if strings.Contains(errMsg, snippet) {
			return true
		}
	}
	return false
}
