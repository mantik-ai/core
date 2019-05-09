package operations

import (
	"encoding/json"
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"testing"
)

func TestBinaryJson(t *testing.T) {
	x, err := json.Marshal(AddCode)
	assert.NoError(t, err)
	assert.Equal(t, []byte("\"add\""), x)

	codes := []BinaryOperation{AddCode, SubCode, MulCode, DivCode}
	for _, c := range codes {
		data, err := json.Marshal(c)
		assert.NoError(t, err)
		var back BinaryOperation
		err = json.Unmarshal(data, &back)
		assert.NoError(t, err)
		assert.Equal(t, c, back)
	}
}

func TestFindBinaryFunction(t *testing.T) {
	add, err := FindBinaryFunction(AddCode, ds.Int32)
	assert.NoError(t, err)
	assert.Equal(t, element.Primitive{int32(11)}, add(element.Primitive{int32(5)}, element.Primitive{int32(6)}))

	sub, err := FindBinaryFunction(SubCode, ds.Int64)
	assert.NoError(t, err)
	assert.Equal(t, element.Primitive{int64(243574330118)}, sub(element.Primitive{int64(243574573542)}, element.Primitive{int64(243424)}))

	mul, err := FindBinaryFunction(MulCode, ds.Float64)
	assert.NoError(t, err)
	assert.Equal(t, element.Primitive{float64(1.1456661516367202e+17)}, mul(element.Primitive{float64(53454534.2442)}, element.Primitive{float64(2143253454.24)}))

	div, err := FindBinaryFunction(DivCode, ds.Uint32)
	assert.NoError(t, err)
	assert.Equal(t, element.Primitive{uint32(2000000000)}, div(element.Primitive{uint32(4000000000)}, element.Primitive{uint32(2)}))
}
