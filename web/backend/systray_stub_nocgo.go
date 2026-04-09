//go:build !cgo

package main

import (
	"context"
	"os"
	"os/signal"
	"runtime"
	"syscall"
	"time"

	"github.com/Perdonus/ruclaw/pkg/logger"
)

// runTray falls back to a headless mode on platforms where systray requires cgo.
func runTray() {
	logger.Infof(T(SystemTrayUnavailableNoCGO, runtime.GOOS))

	if !*noBrowser {
		go func() {
			time.Sleep(browserDelay)
			if err := openBrowser(); err != nil {
				logger.Errorf(T(AutoOpenBrowserFailed, err))
			}
		}()
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	<-ctx.Done()
	shutdownApp()
}
