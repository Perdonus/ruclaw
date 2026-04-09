package matrix

import (
	"path/filepath"

	"github.com/Perdonus/ruclaw/pkg/bus"
	"github.com/Perdonus/ruclaw/pkg/channels"
	"github.com/Perdonus/ruclaw/pkg/config"
)

func init() {
	channels.RegisterFactory("matrix", func(cfg *config.Config, b *bus.MessageBus) (channels.Channel, error) {
		matrixCfg := cfg.Channels.Matrix
		cryptoDatabasePath := matrixCfg.CryptoDatabasePath
		if cryptoDatabasePath == "" {
			cryptoDatabasePath = filepath.Join(cfg.WorkspacePath(), "matrix")
		}
		return NewMatrixChannel(matrixCfg, b, cryptoDatabasePath)
	})
}
