package operations

import (
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/element/builder"
	"testing"
)

func TestFindEqualsOperationForPrimitives(t *testing.T) {
	a1 := FindEqualsOperation(ds.Int32)
	assert.False(t, a1(element.Primitive{int32(5)}, element.Primitive{int32(6)}))
	assert.True(t, a1(element.Primitive{int32(5)}, element.Primitive{int32(5)}))

	a2 := FindEqualsOperation(ds.Void)
	assert.True(t, a2(element.Primitive{nil}, element.Primitive{nil}))

	a3 := FindEqualsOperation(ds.String)
	assert.False(t, a3(element.Primitive{"A"}, element.Primitive{"B"}))
	assert.True(t, a3(element.Primitive{"A"}, element.Primitive{"A"}))
}

func TestFindEqualsOperationForTabulars(t *testing.T) {
	dt := ds.FromJsonStringOrPanic(
		`
		{"columns": {"x": "int32", "y": "string"}}
	`)
	eo := FindEqualsOperation(dt)
	row1 := builder.PrimitiveRow(
		int32(1), "Hello",
	)
	row2 := builder.PrimitiveRow(
		int32(2), "World",
	)
	row3 := builder.PrimitiveRow(
		int32(1), "World",
	)
	row4 := builder.PrimitiveRow(
		int32(1), "Hello",
	)
	e1 := &element.EmbeddedTabularElement{
		[]*element.TabularRow{row4, row2},
	}
	e2 := &element.EmbeddedTabularElement{
		[]*element.TabularRow{row1, row2},
	}
	e3 := &element.EmbeddedTabularElement{
		[]*element.TabularRow{row4},
	}
	e4 := &element.EmbeddedTabularElement{
		[]*element.TabularRow{row1, row3},
	}
	assert.True(t, eo(e1, e2))
	assert.True(t, eo(e1, e1))
	assert.False(t, eo(e1, e3))
	assert.False(t, eo(e1, e4))
}

func TestFindEqualsOperationForImages(t *testing.T) {
	image := ds.FromJsonStringOrPanic(
		`{"type":"image", "width":4,"height":3,"components":{"blue":{"componentType":"int32"}}}`,
	)
	eo := FindEqualsOperation(image)
	data1 := &element.ImageElement{Bytes: []byte("foo Bar")}
	data2 := &element.ImageElement{Bytes: []byte("bim bam")}
	data3 := &element.ImageElement{Bytes: []byte("foo Bar")}
	assert.False(t, eo(data1, data2))
	assert.True(t, eo(data1, data1))
	assert.True(t, eo(data1, data3))
}

func TestFindEqualsForTensors(t *testing.T) {
	tensor := ds.Tensor{
		ds.Ref(ds.Float32),
		[]int{2, 3},
	}
	eo := FindEqualsOperation(&tensor)
	data1 := &element.TensorElement{[]float32{1.0, 1.5, 2.0, 3.5, 4.0, 2.5}}
	data2 := &element.TensorElement{[]float32{1.0, 1.5, 2.1, 3.5, 4.0, 2.5}}
	data3 := &element.TensorElement{[]float32{1.0, 1.5, 2.0, 3.5, 4.0, 2.5}}
	assert.False(t, eo(data1, data2))
	assert.True(t, eo(data1, data1))
	assert.True(t, eo(data1, data3))
}
