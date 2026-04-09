package discord

import (
	"github.com/Perdonus/ruclaw/pkg/audio/tts"
	"github.com/Perdonus/ruclaw/pkg/bus"
	"github.com/Perdonus/ruclaw/pkg/channels"
	"github.com/Perdonus/ruclaw/pkg/config"
)

func init() {
	channels.RegisterFactory("discord", func(cfg *config.Config, b *bus.MessageBus) (channels.Channel, error) {
		ch, err := NewDiscordChannel(cfg.Channels.Discord, b)
		if err == nil {
			ch.tts = tts.DetectTTS(cfg)
		}
		return ch, err
	})
}
