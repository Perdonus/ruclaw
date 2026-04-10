package agent

import "testing"

func TestIsTransientLLMError(t *testing.T) {
	t.Run("matches service unavailable", func(t *testing.T) {
		if !isTransientLLMError("API request failed:\n  Status: 500\n  Body:   {\"message\":\"Service unavailable.\"}") {
			t.Fatal("expected 500 service unavailable to be retriable")
		}
	})

	t.Run("matches unreachable network", func(t *testing.T) {
		if !isTransientLLMError("failed to send request: Post \"https://api.mistral.ai/v1/chat/completions\": dial tcp [2606:4700:7::2c3]:443: connect: network is unreachable") {
			t.Fatal("expected IPv6 routing failure to be retriable")
		}
	})

	t.Run("does not match prompt error", func(t *testing.T) {
		if isTransientLLMError("context window exceeded: prompt is too long") {
			t.Fatal("expected context window errors to stay out of transient retry bucket")
		}
	})

	t.Run("does not match client error", func(t *testing.T) {
		if isTransientLLMError("API request failed:\n  Status: 400\n  Body:   {\"message\":\"Bad request\"}") {
			t.Fatal("expected 400 errors to remain non-retriable")
		}
	})
}
