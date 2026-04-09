package maixcam

import (
	"github.com/Perdonus/ruclaw/pkg/bus"
	"github.com/Perdonus/ruclaw/pkg/channels"
	"github.com/Perdonus/ruclaw/pkg/config"
)

func init() {
	channels.RegisterFactory("maixcam", func(cfg *config.Config, b *bus.MessageBus) (channels.Channel, error) {
		return NewMaixCamChannel(cfg.Channels.MaixCam, b)
	})
}
