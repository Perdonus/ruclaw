// RuClaw Launcher - Web-based chat and management interface
//
// Provides a web UI for chatting with RuClaw via the Pico Channel WebSocket,
// with configuration management and gateway process control.
//
// Usage:
//
//	go build -o ruclaw-launcher ./web/backend/
//	./ruclaw-launcher [config.json]
//	./ruclaw-launcher -public config.json

package main

import (
	"errors"
	"flag"
	"fmt"
	"net/http"
	"net/url"
	"os"
	"os/signal"
	"path/filepath"
	"strconv"
	"strings"
	"syscall"
	"time"

	"github.com/Perdonus/ruclaw/pkg/config"
	"github.com/Perdonus/ruclaw/pkg/logger"
	"github.com/Perdonus/ruclaw/web/backend/api"
	"github.com/Perdonus/ruclaw/web/backend/dashboardauth"
	"github.com/Perdonus/ruclaw/web/backend/launcherconfig"
	"github.com/Perdonus/ruclaw/web/backend/middleware"
	"github.com/Perdonus/ruclaw/web/backend/utils"
)

const (
	appName = "RuClaw"

	logPath   = "logs"
	panicFile = "launcher_panic.log"
	logFile   = "launcher.log"
)

var (
	appVersion = config.Version

	server     *http.Server
	serverAddr string
	// browserLaunchURL is opened by openBrowser() (auto-open + tray "open console").
	// Includes ?token= for same-machine dashboard login; keep serverAddr without secrets for other use.
	browserLaunchURL string
	apiHandler       *api.Handler

	noBrowser *bool
)

func shouldEnableLauncherFileLogging(enableConsole, debug bool) bool {
	return !enableConsole || debug
}

func dashboardTokenConfigHelpPath(source launcherconfig.DashboardTokenSource, launcherPath string) string {
	if source != launcherconfig.DashboardTokenSourceConfig {
		return ""
	}
	return launcherPath
}

// maskSecret masks a secret for display. It always shows up to the first 3
// runes. The last 4 runes are only appended when at least 5 runes remain
// hidden in the middle (i.e. string length >= 12), so an 8-char minimum
// password never exposes its tail. Strings of 3 chars or fewer are fully
// masked.
func maskSecret(s string) string {
	runes := []rune(s)
	n := len(runes)
	const prefixLen, suffixLen, minHidden = 3, 4, 5
	if n < prefixLen+suffixLen+minHidden {
		if n <= prefixLen {
			return "**********"
		}
		return string(runes[:prefixLen]) + "**********"
	}
	return string(runes[:prefixLen]) + "**********" + string(runes[n-suffixLen:])
}

func languageFromArgs(args []string) string {
	for i := 0; i < len(args); i++ {
		switch arg := strings.TrimSpace(args[i]); {
		case arg == "-lang" || arg == "--lang":
			if i+1 < len(args) {
				return args[i+1]
			}
		case strings.HasPrefix(arg, "-lang="):
			return strings.TrimPrefix(arg, "-lang=")
		case strings.HasPrefix(arg, "--lang="):
			return strings.TrimPrefix(arg, "--lang=")
		}
	}
	return ""
}

func main() {
	if langArg := languageFromArgs(os.Args[1:]); langArg != "" {
		SetLanguage(langArg)
	}

	port := flag.String("port", "18800", T(FlagPort))
	public := flag.Bool("public", false, T(FlagPublic))
	noBrowser = flag.Bool("no-browser", false, T(FlagNoBrowser))
	lang := flag.String("lang", "", T(FlagLang))
	console := flag.Bool("console", false, T(FlagConsole))

	var debug bool
	flag.BoolVar(&debug, "d", false, T(FlagDebug))
	flag.BoolVar(&debug, "debug", false, T(FlagDebug))

	flag.Usage = func() {
		fmt.Fprintf(os.Stderr, "%s\n\n", T(UsageTitle, appName))
		fmt.Fprintf(os.Stderr, "%s\n\n", T(UsageLine, os.Args[0]))
		fmt.Fprintf(os.Stderr, "%s\n", T(UsageArguments))
		fmt.Fprintf(os.Stderr, "%s\n\n", T(UsageConfigArgument))
		fmt.Fprintf(os.Stderr, "%s\n", T(UsageOptions))
		flag.PrintDefaults()
		fmt.Fprintf(os.Stderr, "\n%s\n", T(UsageExamples))
		fmt.Fprintf(os.Stderr, "  %s\n", os.Args[0])
		fmt.Fprintf(os.Stderr, "%s\n", T(ExampleDefaultMode))
		fmt.Fprintf(os.Stderr, "  %s ./config.json\n", os.Args[0])
		fmt.Fprintf(os.Stderr, "%s\n", T(ExampleConfigFile))
		fmt.Fprintf(
			os.Stderr,
			"  %s -public ./config.json\n",
			os.Args[0],
		)
		fmt.Fprintf(os.Stderr, "%s\n", T(ExamplePublicAccess))
		fmt.Fprintf(os.Stderr, "  %s -console -d ./config.json\n", os.Args[0])
		fmt.Fprintf(os.Stderr, "%s\n", T(ExampleConsoleDebug))
	}
	flag.Parse()

	// Initialize logger
	picoHome := utils.GetPicoclawHome()

	f := filepath.Join(picoHome, logPath, panicFile)
	panicFunc, err := logger.InitPanic(f)
	if err != nil {
		panic(fmt.Sprintf("error initializing panic log: %v", err))
	}
	defer panicFunc()

	enableConsole := *console
	fileLoggingEnabled := shouldEnableLauncherFileLogging(enableConsole, debug)
	if fileLoggingEnabled {
		// GUI mode writes launcher logs to file. Debug mode keeps file logging enabled in console mode too.
		if !debug {
			logger.DisableConsole()
		}

		f := filepath.Join(picoHome, logPath, logFile)
		if err = logger.EnableFileLogging(f); err != nil {
			panic(fmt.Sprintf("error enabling file logging: %v", err))
		}
		defer logger.DisableFileLogging()
	}
	if debug {
		logger.SetLevel(logger.DEBUG)
	}

	// Allow an explicit CLI language override after flag parsing.
	if *lang != "" {
		SetLanguage(*lang)
	}

	// Resolve config path
	configPath := utils.GetDefaultConfigPath()
	if flag.NArg() > 0 {
		configPath = flag.Arg(0)
	}

	absPath, err := filepath.Abs(configPath)
	if err != nil {
		logger.Fatalf(T(ResolveConfigPathFailed, err))
	}
	err = utils.EnsureOnboarded(absPath)
	if err != nil {
		logger.Errorf(T(AutoInitConfigFailed, appName, err))
	}
	if !debug {
		logger.SetLevelFromString(config.ResolveGatewayLogLevel(absPath))
	}

	logger.InfoC("web", T(LauncherStarting, appName, appVersion))
	logger.InfoC("web", T(LauncherHome, appName, picoHome))
	if debug {
		logger.InfoC("web", T(DebugModeEnabled))
		logger.DebugC(
			"web",
			T(LauncherFlags, enableConsole, *public, *noBrowser, absPath),
		)
	}

	var explicitPort bool
	var explicitPublic bool
	flag.Visit(func(f *flag.Flag) {
		switch f.Name {
		case "port":
			explicitPort = true
		case "public":
			explicitPublic = true
		}
	})

	launcherPath := launcherconfig.PathForAppConfig(absPath)
	launcherCfg, err := launcherconfig.Load(launcherPath, launcherconfig.Default())
	if err != nil {
		logger.ErrorC("web", T(LoadLauncherConfigFailed, launcherPath, err))
		launcherCfg = launcherconfig.Default()
	}

	effectivePort := *port
	effectivePublic := *public
	if !explicitPort {
		effectivePort = strconv.Itoa(launcherCfg.Port)
	}
	if !explicitPublic {
		effectivePublic = launcherCfg.Public
	}

	portNum, err := strconv.Atoi(effectivePort)
	if err != nil || portNum < 1 || portNum > 65535 {
		if err == nil {
			err = errors.New("must be in range 1-65535")
		}
		logger.Fatalf(T(InvalidPort, effectivePort, err))
	}

	dashboardToken, dashboardSigningKey, dashboardTokenSource, dashErr := launcherconfig.EnsureDashboardSecrets(
		launcherCfg,
	)
	if dashErr != nil {
		logger.Fatalf(T(DashboardAuthSetupFailed, dashErr))
	}
	dashboardSessionCookie := middleware.SessionCookieValue(dashboardSigningKey, dashboardToken)

	// Open the bcrypt password store (creates the DB file on first run).
	authStore, authStoreErr := dashboardauth.New(picoHome)
	if authStoreErr != nil {
		logger.ErrorC("web", T(OpenAuthStoreFailed, authStoreErr))
		authStore = nil
	} else {
		defer authStore.Close()
	}

	// Determine listen address
	var addr string
	if effectivePublic {
		addr = "0.0.0.0:" + effectivePort
	} else {
		addr = "127.0.0.1:" + effectivePort
	}

	// Initialize Server components
	mux := http.NewServeMux()

	api.RegisterLauncherAuthRoutes(mux, api.LauncherAuthRouteOpts{
		DashboardToken: dashboardToken,
		SessionCookie:  dashboardSessionCookie,
		PasswordStore:  authStore,
		StoreError:     authStoreErr,
	})

	// API Routes (e.g. /api/status)
	apiHandler = api.NewHandler(absPath)
	apiHandler.SetDebug(debug)
	if _, err = apiHandler.EnsurePicoChannel(""); err != nil {
		logger.ErrorC("web", T(EnsurePicoChannelFailed, err))
	}
	apiHandler.SetServerOptions(portNum, effectivePublic, explicitPublic, launcherCfg.AllowedCIDRs)
	apiHandler.RegisterRoutes(mux)

	// Frontend Embedded Assets
	registerEmbedRoutes(mux)

	accessControlledMux, err := middleware.IPAllowlist(launcherCfg.AllowedCIDRs, mux)
	if err != nil {
		logger.Fatalf(T(InvalidAllowedCIDR, err))
	}

	dashAuth := middleware.LauncherDashboardAuth(middleware.LauncherDashboardAuthConfig{
		ExpectedCookie: dashboardSessionCookie,
		Token:          dashboardToken,
	}, accessControlledMux)

	// Apply middleware stack
	handler := middleware.Recoverer(
		middleware.Logger(
			middleware.ReferrerPolicyNoReferrer(
				middleware.JSONContentType(dashAuth),
			),
		),
	)

	// Print startup banner and token (console mode only).
	if enableConsole || debug {
		fmt.Print(utils.Banner)
		fmt.Println()
		fmt.Printf("  %s\n", T(OpenBrowserPrompt))
		fmt.Println()
		fmt.Printf("    >> http://localhost:%s <<\n", effectivePort)
		if effectivePublic {
			if ip := utils.GetLocalIP(); ip != "" {
				fmt.Printf("    >> http://%s:%s <<\n", ip, effectivePort)
			}
		}
		fmt.Println()
		switch dashboardTokenSource {
		case launcherconfig.DashboardTokenSourceRandom:
			fmt.Printf("  %s\n", T(DashboardPasswordRuntime, maskSecret(dashboardToken)))
		case launcherconfig.DashboardTokenSourceEnv:
			fmt.Printf("  %s\n", T(DashboardPasswordEnv))
		case launcherconfig.DashboardTokenSourceConfig:
			fmt.Printf("  %s\n", T(DashboardPasswordConfig, launcherPath))
		}
		fmt.Println()
	}

	switch dashboardTokenSource {
	case launcherconfig.DashboardTokenSourceEnv:
		logger.InfoC("web", T(DashboardPasswordEnv))
	case launcherconfig.DashboardTokenSourceConfig:
		logger.InfoC("web", T(DashboardPasswordConfig, launcherPath))
	case launcherconfig.DashboardTokenSourceRandom:
		if !enableConsole {
			logger.InfoC("web", T(DashboardPasswordRuntime, maskSecret(dashboardToken)))
		}
	}

	// Log startup info to file
	logger.InfoC("web", T(LocalServerAddress, effectivePort))
	if effectivePublic {
		if ip := utils.GetLocalIP(); ip != "" {
			logger.InfoC("web", T(PublicServerAddress, ip, effectivePort))
		}
	}

	// Share the local URL with the launcher runtime.
	serverAddr = fmt.Sprintf("http://localhost:%s", effectivePort)
	if dashboardToken != "" {
		browserLaunchURL = serverAddr + "?token=" + url.QueryEscape(dashboardToken)
	} else {
		browserLaunchURL = serverAddr
	}

	// Auto-open browser will be handled by the launcher runtime.

	// Auto-start gateway after backend starts listening.
	go func() {
		time.Sleep(1 * time.Second)
		apiHandler.TryAutoStartGateway()
	}()

	// Start the Server in a goroutine
	server = &http.Server{Addr: addr, Handler: handler}
	go func() {
		logger.InfoC("web", T(ServerListening, addr))
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			logger.Fatalf(T(ServerStartFailed, err))
		}
	}()

	defer shutdownApp()

	// Start system tray or run in console mode
	if enableConsole {
		if !*noBrowser {
			// Auto-open browser after systray is ready (if not disabled)
			// Check no-browser flag via environment or pass as parameter if needed
			if err := openBrowser(); err != nil {
				logger.Errorf(T(AutoOpenBrowserFailed, err))
			}
		}

		sigChan := make(chan os.Signal, 1)
		signal.Notify(sigChan, os.Interrupt, syscall.SIGTERM)

		// Main event loop - wait for signals or config changes
		for {
			select {
			case <-sigChan:
				logger.Info(T(ShuttingDown))

				return
			}
		}
	} else {
		// GUI mode: start system tray
		runTray()
	}
}
