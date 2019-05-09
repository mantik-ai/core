package runner

import (
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds/element/builder"
	"select/services/selectbridge/ops"
	"testing"
)

func TestSimpleRun(t *testing.T) {
	// returns the 2nd argument
	p := Program{
		2,
		1,
		1,
		ops.OpList{
			&ops.GetOp{1},
			&ops.GetOp{0},
		},
	}
	r, err := CreateRunner(&p)
	assert.NoError(t, err)
	result, err := r.Run(builder.PrimitiveElements(int32(1), int32(2)))
	assert.NoError(t, err)
	assert.Equal(t, builder.PrimitiveElements(int32(2), int32(1)), result)
}
