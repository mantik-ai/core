package adapt

import (
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"testing"
)

func TestPrimitiveVoidConversion(t *testing.T) {
	converter, err := LookupAutoAdapter(ds.String, ds.Void)
	assert.NoError(t, err)
	converted, err := converter(element.Primitive{"Hello World"})
	assert.Equal(t, element.Primitive{nil}, converted)
}

func TestComplexVoidConversion(t *testing.T) {
	from := ds.FromJsonStringOrPanic(`{"type":"tensor", "shape": [1,2], "componentType": "uint8"}`)
	converter, err := LookupAutoAdapter(from, ds.Void)
	assert.NoError(t, err)
	converted, err := converter(&element.TensorElement{[]int32{1, 2}})
	assert.Equal(t, element.Primitive{nil}, converted)
}
