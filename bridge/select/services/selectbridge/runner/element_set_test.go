package runner

import (
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element/builder"
	"testing"
)

func TestElementSet_Add(t *testing.T) {
	format := ds.FromJsonStringOrPanic(`{"columns":{"x":"int32", "y": "float32"}}`)

	set, err := NewElementSet(format)
	assert.NoError(t, err)

	added, err := set.Add(builder.PrimitiveRow(int32(5), float32(1.4)))
	assert.NoError(t, err)
	assert.True(t, added)

	added, err = set.Add(builder.PrimitiveRow(int32(5), float32(1.5)))
	assert.NoError(t, err)
	assert.True(t, added)

	assert.Equal(t, 2, set.Size())

	added, err = set.Add(builder.PrimitiveRow(int32(5), float32(1.4)))
	assert.NoError(t, err)
	assert.False(t, added)

	assert.Equal(t, 2, set.Size())

	added, err = set.Add(builder.PrimitiveRow(int32(4), float32(1.4)))
	assert.NoError(t, err)
	assert.True(t, added)

	added, err = set.Add(builder.PrimitiveRow(int32(4), float32(1.4)))
	assert.NoError(t, err)
	assert.False(t, added)

	assert.Equal(t, 3, set.Size())

	set.Clear()
	assert.Equal(t, 0, set.Size())

	added, err = set.Add(builder.PrimitiveRow(int32(4), float32(1.4)))
	assert.NoError(t, err)
	assert.True(t, added)

	assert.Equal(t, 1, set.Size())
}
