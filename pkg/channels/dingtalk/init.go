package dingtalk

import (
	"github.com/Perdonus/ruclaw/pkg/bus"
	"github.com/Perdonus/ruclaw/pkg/channels"
	"github.com/Perdonus/ruclaw/pkg/config"
)

func init() {
	channels.RegisterFactory("dingtalk", func(cfg *config.Config, b *bus.MessageBus) (channels.Channel, error) {
		return NewDingTalkChannel(cfg.Channels.DingTalk, b)
	})
}
