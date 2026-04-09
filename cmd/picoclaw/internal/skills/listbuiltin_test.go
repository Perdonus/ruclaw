package skills

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestNewListbuiltinSubcommand(t *testing.T) {
	cmd := newListBuiltinCommand()

	require.NotNil(t, cmd)

	assert.Equal(t, "list-builtin", cmd.Use)
	assert.Equal(t, "Показать доступные встроенные навыки", cmd.Short)

	assert.NotNil(t, cmd.Run)

	assert.True(t, cmd.HasExample())
	assert.False(t, cmd.HasSubCommands())

	assert.False(t, cmd.HasFlags())

	assert.Len(t, cmd.Aliases, 0)
}
