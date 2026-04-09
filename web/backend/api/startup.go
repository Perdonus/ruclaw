package api

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
)

const (
	autoStartEntryName       = "RuClawLauncher"
	legacyAutoStartEntryName = "PicoClawLauncher"
	launchAgentLabel         = "com.ruclaw.launcher"
	legacyLaunchAgentLabel   = "io.picoclaw.launcher"
	linuxAutoStartFileName   = "ruclaw-launcher.desktop"
	legacyLinuxAutoStartFile = "picoclaw-web.desktop"
)

type autoStartRequest struct {
	Enabled bool `json:"enabled"`
}

type autoStartResponse struct {
	Enabled   bool   `json:"enabled"`
	Supported bool   `json:"supported"`
	Platform  string `json:"platform"`
	Message   string `json:"message,omitempty"`
}

var errAutoStartUnsupported = errors.New(TranslateLauncher(StartupAutoStartUnsupported))

func (h *Handler) registerStartupRoutes(mux *http.ServeMux) {
	mux.HandleFunc("GET /api/system/autostart", h.handleGetAutoStart)
	mux.HandleFunc("PUT /api/system/autostart", h.handleSetAutoStart)
}

func (h *Handler) handleGetAutoStart(w http.ResponseWriter, r *http.Request) {
	enabled, supported, message, err := h.getAutoStartStatus()
	if err != nil {
		http.Error(w, TranslateLauncher(StartupReadFailed, err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(autoStartResponse{
		Enabled:   enabled,
		Supported: supported,
		Platform:  runtime.GOOS,
		Message:   message,
	})
}

func (h *Handler) handleSetAutoStart(w http.ResponseWriter, r *http.Request) {
	var req autoStartRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, TranslateLauncher(StartupInvalidJSON, err), http.StatusBadRequest)
		return
	}

	if err := h.setAutoStart(req.Enabled); err != nil {
		if errors.Is(err, errAutoStartUnsupported) {
			http.Error(w, TranslateLauncher(StartupAutoStartUnsupported), http.StatusBadRequest)
			return
		}
		http.Error(w, TranslateLauncher(StartupUpdateFailed, err), http.StatusInternalServerError)
		return
	}

	enabled, supported, message, err := h.getAutoStartStatus()
	if err != nil {
		http.Error(w, TranslateLauncher(StartupVerifyFailed, err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(autoStartResponse{
		Enabled:   enabled,
		Supported: supported,
		Platform:  runtime.GOOS,
		Message:   message,
	})
}

func (h *Handler) resolveLaunchCommand() (string, []string, error) {
	exePath, err := os.Executable()
	if err != nil {
		return "", nil, err
	}

	args := []string{"-no-browser"}
	if h.debug {
		args = append(args, "-d")
	}
	if h.configPath != "" {
		args = append(args, h.configPath)
	}

	return exePath, args, nil
}

func (h *Handler) getAutoStartStatus() (enabled bool, supported bool, message string, err error) {
	switch runtime.GOOS {
	case "darwin":
		exists, err := anyFileExists(macLaunchAgentPath(), legacyMacLaunchAgentPath())
		return exists, true, TranslateLauncher(StartupApplyNextLogin), err
	case "linux":
		exists, err := anyFileExists(linuxAutoStartPath(), legacyLinuxAutoStartPath())
		return exists, true, TranslateLauncher(StartupApplyNextLogin), err
	case "windows":
		exists, err := windowsRunKeyExistsAny(autoStartEntryName, legacyAutoStartEntryName)
		return exists, true, TranslateLauncher(StartupApplyNextLogin), err
	default:
		return false, false, TranslateLauncher(StartupUnsupportedPlatform), nil
	}
}

func (h *Handler) setAutoStart(enabled bool) error {
	exePath, args, err := h.resolveLaunchCommand()
	if err != nil {
		return err
	}

	switch runtime.GOOS {
	case "darwin":
		return setDarwinAutoStart(enabled, exePath, args)
	case "linux":
		return setLinuxAutoStart(enabled, exePath, args)
	case "windows":
		return setWindowsAutoStart(enabled, exePath, args)
	default:
		return errAutoStartUnsupported
	}
}

func fileExists(path string) (bool, error) {
	_, err := os.Stat(path)
	if err == nil {
		return true, nil
	}
	if os.IsNotExist(err) {
		return false, nil
	}
	return false, err
}

func anyFileExists(paths ...string) (bool, error) {
	for _, path := range paths {
		exists, err := fileExists(path)
		if err != nil {
			return false, err
		}
		if exists {
			return true, nil
		}
	}
	return false, nil
}

func removeFileIfExists(path string) error {
	if err := os.Remove(path); err != nil && !os.IsNotExist(err) {
		return err
	}
	return nil
}

func removePaths(paths ...string) error {
	for _, path := range paths {
		if err := removeFileIfExists(path); err != nil {
			return err
		}
	}
	return nil
}

func uniqueStrings(values ...string) []string {
	seen := make(map[string]struct{}, len(values))
	result := make([]string, 0, len(values))
	for _, value := range values {
		if value == "" {
			continue
		}
		if _, ok := seen[value]; ok {
			continue
		}
		seen[value] = struct{}{}
		result = append(result, value)
	}
	return result
}

func macLaunchAgentPathForLabel(label string) string {
	home, _ := os.UserHomeDir()
	return filepath.Join(home, "Library", "LaunchAgents", label+".plist")
}

func macLaunchAgentPath() string {
	return macLaunchAgentPathForLabel(launchAgentLabel)
}

func legacyMacLaunchAgentPath() string {
	return macLaunchAgentPathForLabel(legacyLaunchAgentLabel)
}

func setDarwinAutoStart(enabled bool, exePath string, args []string) error {
	plistPath := macLaunchAgentPath()
	if enabled {
		if err := os.MkdirAll(filepath.Dir(plistPath), 0o755); err != nil {
			return err
		}
		content := buildDarwinPlist(exePath, args)
		if err := os.WriteFile(plistPath, []byte(content), 0o644); err != nil {
			return err
		}
		return removePaths(uniqueStrings(legacyMacLaunchAgentPath())...)
	}

	return removePaths(uniqueStrings(plistPath, legacyMacLaunchAgentPath())...)
}

func xmlEscape(s string) string {
	var b bytes.Buffer
	for _, r := range s {
		switch r {
		case '&':
			b.WriteString("&amp;")
		case '<':
			b.WriteString("&lt;")
		case '>':
			b.WriteString("&gt;")
		case '"':
			b.WriteString("&quot;")
		case '\'':
			b.WriteString("&apos;")
		default:
			b.WriteRune(r)
		}
	}
	return b.String()
}

func buildDarwinPlist(exePath string, args []string) string {
	programArgs := make([]string, 0, len(args)+1)
	programArgs = append(programArgs, exePath)
	programArgs = append(programArgs, args...)

	var b strings.Builder
	b.WriteString(`<?xml version="1.0" encoding="UTF-8"?>` + "\n")
	b.WriteString(
		`<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">` + "\n",
	)
	b.WriteString(`<plist version="1.0">` + "\n")
	b.WriteString(`<dict>` + "\n")
	b.WriteString(`  <key>Label</key>` + "\n")
	b.WriteString(`  <string>` + launchAgentLabel + `</string>` + "\n")
	b.WriteString(`  <key>ProgramArguments</key>` + "\n")
	b.WriteString(`  <array>` + "\n")
	for _, arg := range programArgs {
		b.WriteString(`    <string>` + xmlEscape(arg) + `</string>` + "\n")
	}
	b.WriteString(`  </array>` + "\n")
	b.WriteString(`  <key>RunAtLoad</key>` + "\n")
	b.WriteString(`  <true/>` + "\n")
	b.WriteString(`  <key>ProcessType</key>` + "\n")
	b.WriteString(`  <string>Background</string>` + "\n")
	b.WriteString(`</dict>` + "\n")
	b.WriteString(`</plist>` + "\n")
	return b.String()
}

func linuxAutoStartPath() string {
	return linuxAutoStartPathForName(linuxAutoStartFileName)
}

func legacyLinuxAutoStartPath() string {
	return linuxAutoStartPathForName(legacyLinuxAutoStartFile)
}

func linuxAutoStartPathForName(fileName string) string {
	home, _ := os.UserHomeDir()
	return filepath.Join(home, ".config", "autostart", fileName)
}

func shellQuote(s string) string {
	if s == "" {
		return "''"
	}
	if !strings.ContainsAny(s, " \t\n'\"\\$`") {
		return s
	}
	return "'" + strings.ReplaceAll(s, "'", "'\"'\"'") + "'"
}

func buildLinuxExecLine(exePath string, args []string) string {
	parts := make([]string, 0, len(args)+1)
	parts = append(parts, shellQuote(exePath))
	for _, arg := range args {
		parts = append(parts, shellQuote(arg))
	}
	return strings.Join(parts, " ")
}

func setLinuxAutoStart(enabled bool, exePath string, args []string) error {
	desktopPath := linuxAutoStartPath()
	if enabled {
		if err := os.MkdirAll(filepath.Dir(desktopPath), 0o755); err != nil {
			return err
		}
		content := strings.Join([]string{
			"[Desktop Entry]",
			"Type=Application",
			"Version=1.0",
			"Name=RuClaw Launcher",
			"Comment=" + TranslateLauncher(StartupDesktopComment),
			"Exec=" + buildLinuxExecLine(exePath, args),
			"Terminal=false",
			"X-GNOME-Autostart-enabled=true",
			"NoDisplay=true",
			"",
		}, "\n")
		if err := os.WriteFile(desktopPath, []byte(content), 0o644); err != nil {
			return err
		}
		return removePaths(uniqueStrings(legacyLinuxAutoStartPath())...)
	}

	return removePaths(uniqueStrings(desktopPath, legacyLinuxAutoStartPath())...)
}

func windowsCommandLine(exePath string, args []string) string {
	parts := make([]string, 0, len(args)+1)
	parts = append(parts, fmt.Sprintf("%q", exePath))
	for _, arg := range args {
		parts = append(parts, fmt.Sprintf("%q", arg))
	}
	return strings.Join(parts, " ")
}

func windowsRunKeyExists(entryName string) (bool, error) {
	cmd := exec.Command("reg", "query", `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`, "/v", entryName)
	if err := cmd.Run(); err != nil {
		var exitErr *exec.ExitError
		if errors.As(err, &exitErr) {
			return false, nil
		}
		return false, err
	}
	return true, nil
}

func windowsRunKeyExistsAny(entryNames ...string) (bool, error) {
	for _, entryName := range uniqueStrings(entryNames...) {
		exists, err := windowsRunKeyExists(entryName)
		if err != nil {
			return false, err
		}
		if exists {
			return true, nil
		}
	}
	return false, nil
}

func deleteWindowsRunKey(entryName string) error {
	cmd := exec.Command("reg", "delete", `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`, "/v", entryName, "/f")
	if err := cmd.Run(); err != nil {
		var exitErr *exec.ExitError
		if errors.As(err, &exitErr) {
			return nil
		}
		return err
	}
	return nil
}

func setWindowsAutoStart(enabled bool, exePath string, args []string) error {
	key := `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`
	if enabled {
		commandLine := windowsCommandLine(exePath, args)
		cmd := exec.Command("reg", "add", key, "/v", autoStartEntryName, "/t", "REG_SZ", "/d", commandLine, "/f")
		if err := cmd.Run(); err != nil {
			return err
		}
		return deleteWindowsRunKey(legacyAutoStartEntryName)
	}

	for _, entryName := range uniqueStrings(autoStartEntryName, legacyAutoStartEntryName) {
		if err := deleteWindowsRunKey(entryName); err != nil {
			return err
		}
	}
	return nil
}
