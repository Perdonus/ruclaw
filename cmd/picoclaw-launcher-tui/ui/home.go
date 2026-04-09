// PicoClaw - Ultra-lightweight personal AI agent
// License: MIT
//
// Copyright (c) 2026 PicoClaw contributors

package ui

import (
	"os"
	"os/exec"

	"github.com/gdamore/tcell/v2"
	"github.com/rivo/tview"
)

func (a *App) newHomePage() tview.Primitive {
	list := tview.NewList()
	list.SetBorder(true).
		SetTitle(" [#00f0ff::b] АКТИВНАЯ КОНФИГУРАЦИЯ ").
		SetTitleColor(tcell.NewHexColor(0x00f0ff)).
		SetBorderColor(tcell.NewHexColor(0x00f0ff))
	list.SetMainTextColor(tcell.NewHexColor(0xe0e0e0))
	list.SetSecondaryTextColor(tcell.NewHexColor(0x808080))
	list.SetSelectedStyle(
		tcell.StyleDefault.Background(tcell.NewHexColor(0x39ff14)).Foreground(tcell.NewHexColor(0x050510)),
	)
	list.SetHighlightFullLine(true)
	list.SetBackgroundColor(tcell.NewHexColor(0x050510))

	rebuildList := func() {
		sel := list.GetCurrentItem()
		list.Clear()
		list.AddItem("МОДЕЛЬ: "+a.cfg.CurrentModelLabel(), "Выбрать и настроить ИИ-модель", 'm', func() {
			a.navigateTo("schemes", a.newSchemesPage())
		})
		list.AddItem(
			"КАНАЛЫ: Настройка каналов связи",
			"Управление Telegram/Discord/WeChat-каналами",
			'n',
			func() {
				a.navigateTo("channels", a.newChannelsPage())
			},
		)
		list.AddItem("ШЛЮЗ: Управление сервисом", "Управление демоном шлюза RuClaw", 'g', func() {
			a.navigateTo("gateway", a.newGatewayPage())
		})
		list.AddItem("ЧАТ: Запустить агента", "Открыть интерактивную сессию чата", 'c', func() {
			a.tapp.Suspend(func() {
				cmd := exec.Command("picoclaw", "agent")
				cmd.Stdin = os.Stdin
				cmd.Stdout = os.Stdout
				cmd.Stderr = os.Stderr
				_ = cmd.Run()
			})
		})
		list.AddItem("ВЫХОД", "Закрыть лаунчер RuClaw", 'q', func() { a.tapp.Stop() })
		if sel >= 0 && sel < list.GetItemCount() {
			list.SetCurrentItem(sel)
		}
	}
	rebuildList()

	a.pageRefreshFns["home"] = rebuildList

	return a.buildShell(
		"home",
		list,
		" [#00f0ff]m:[-] модель  [#00f0ff]n:[-] каналы  [#00f0ff]g:[-] шлюз  [#00f0ff]c:[-] чат  [#ff2a2a]q:[-] выход ",
	)
}
