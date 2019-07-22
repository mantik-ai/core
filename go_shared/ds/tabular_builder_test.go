package ds

import (
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestTabularBuidler(t *testing.T) {
	dt := BuildTabular().Add("x", Float32).AddTensor("y", Int32, []int{1, 2}).Result()
	expected := FromJsonStringOrPanic(`
{
	"columns": {
		"x": "float32",
		"y": {
			"type": "tensor",
			"componentType": "int32",
			"shape": [1,2]
		}
	}	
}
`)
	assert.Equal(t, expected, dt)
}
