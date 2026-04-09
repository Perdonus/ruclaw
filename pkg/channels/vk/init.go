package vk

import (
	"github.com/Perdonus/ruclaw/pkg/bus"
	"github.com/Perdonus/ruclaw/pkg/channels"
	"github.com/Perdonus/ruclaw/pkg/config"
)

func init() {
	channels.RegisterFactory("vk", func(cfg *config.Config, b *bus.MessageBus) (channels.Channel, error) {
		return NewVKChannel(cfg, b)
	})
}
