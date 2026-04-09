//go:build (!darwin && !freebsd) || cgo

package main

import (
	"fmt"

	"fyne.io/systray"

	"github.com/Perdonus/ruclaw/pkg/logger"
	"github.com/Perdonus/ruclaw/web/backend/utils"
)

func runTray() {
	systray.Run(onReady, onExit)
}

// onReady is called when the system tray is ready
func onReady() {
	// Set icon and tooltip
	systray.SetIcon(getIcon())
	systray.SetTooltip(fmt.Sprintf(T(AppTooltip), appName))

	// Create menu items
	mOpen := systray.AddMenuItem(T(MenuOpen), T(MenuOpenTooltip))
	mAbout := systray.AddMenuItem(T(MenuAbout), T(MenuAboutTooltip))

	// Add version info under About menu
	mVersion := mAbout.AddSubMenuItem(fmt.Sprintf(T(MenuVersion), appVersion), T(MenuVersionTooltip))
	mVersion.Disable()
	mRepo := mAbout.AddSubMenuItem(T(MenuGitHub), "")
	mDocs := mAbout.AddSubMenuItem(T(MenuDocs), "")

	systray.AddSeparator()

	// Add restart option
	mRestart := systray.AddMenuItem(T(MenuRestart), T(MenuRestartTooltip))

	systray.AddSeparator()

	// Quit option
	mQuit := systray.AddMenuItem(T(MenuQuit), T(MenuQuitTooltip))

	// Handle menu clicks
	go func() {
		for {
			select {
			case <-mOpen.ClickedCh:
				if err := openBrowser(); err != nil {
					logger.Errorf(T(OpenBrowserFailed, err))
				}

			case <-mVersion.ClickedCh:
				// Version info - do nothing, just shows current version

			case <-mRepo.ClickedCh:
				if err := utils.OpenBrowser("https://github.com/Perdonus/ruclaw"); err != nil {
					logger.Errorf(T(OpenGitHubFailed, err))
				}

			case <-mDocs.ClickedCh:
				if err := utils.OpenBrowser(T(DocUrl)); err != nil {
					logger.Errorf(T(OpenDocsFailed, err))
				}

			case <-mRestart.ClickedCh:
				fmt.Println(T(RestartRequestReceived))
				if apiHandler != nil {
					if pid, err := apiHandler.RestartGateway(); err != nil {
						logger.Errorf(T(RestartGatewayFailed, err))
					} else {
						logger.Infof(T(GatewayRestarted, pid))
					}
				}

			case <-mQuit.ClickedCh:
				systray.Quit()
			}
		}
	}()

	if !*noBrowser {
		// Auto-open browser after systray is ready (if not disabled)
		// Check no-browser flag via environment or pass as parameter if needed
		if err := openBrowser(); err != nil {
			logger.Errorf(T(AutoOpenBrowserFailed, err))
		}
	}
}

// onExit is called when the system tray is exiting
func onExit() {
	logger.Info(T(Exiting))
}
