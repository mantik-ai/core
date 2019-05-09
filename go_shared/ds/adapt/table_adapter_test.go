package adapt

import (
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element/builder"
	"testing"
)

// Two columns, exactly flipped
func TestSimpleTableChange(t *testing.T) {
	in := ds.FromJsonStringOrPanic(`{ "columns": { "x": "int32", "y": "string" } }`)
	out := ds.FromJsonStringOrPanic(`{ "columns": { "y": "string", "x": "int32" } }`)

	adapter, err := LookupAutoAdapter(in, out)
	assert.NoError(t, err)

	adapted, err := adapter(builder.PrimitiveRow(int32(5), "Hello World"))
	assert.NoError(t, err)
	assert.Equal(t, builder.PrimitiveRow("Hello World", int32(5)), adapted)
}

func TestEmbeddedTabularChange(t *testing.T) {
	in := ds.FromJsonStringOrPanic(`{ "columns": { "x": "int32", "y": "string" } }`)
	out := ds.FromJsonStringOrPanic(`{ "columns": { "y": "string", "x": "int32" } }`)

	adapter, err := LookupAutoAdapter(in, out)
	assert.NoError(t, err)

	input := builder.Embedded(
		builder.PrimitiveRow(int32(5), "Hello"),
		builder.PrimitiveRow(int32(6), "World"),
	)
	expected := builder.Embedded(
		builder.PrimitiveRow("Hello", int32(5)),
		builder.PrimitiveRow("World", int32(6)),
	)

	adapted, err := adapter(input)
	assert.NoError(t, err)
	assert.Equal(t, expected, adapted)
}

func TestTableRowCountMismatch(t *testing.T) {
	in := ds.FromJsonStringOrPanic(`{ "columns": { "x": "int32", "y": "string" }, "rowCount": 1 }`)
	out := ds.FromJsonStringOrPanic(`{ "columns": { "y": "string", "x": "int32" } }`)
	// This should work, we can convert something with rowCount = 1 into something wihtout row Count
	_, err := LookupAutoAdapter(in, out)
	assert.NoError(t, err)
	// the other way should not work, we cannot convert no row count into something with row count
	_, err = LookupAutoAdapter(out, in)
	assert.Error(t, err)
}

// Two columns, changed types
func TestSimpleTypeChange(t *testing.T) {
	in := ds.FromJsonStringOrPanic(`{ "columns": { "x": "int32", "y": "string" } }`)
	out := ds.FromJsonStringOrPanic(`{ "columns": { "x": "int64", "y": "string" } }`)

	adapter, err := LookupAutoAdapter(in, out)
	assert.NoError(t, err)

	adapted, err := adapter(builder.PrimitiveRow(int32(5), "Hello World"))
	assert.NoError(t, err)
	assert.Equal(t, builder.PrimitiveRow(int64(5), "Hello World"), adapted)
}

// A table with one column dropped
func TestTableReduce(t *testing.T) {
	in := ds.FromJsonStringOrPanic(`{ "columns": { "x": "int32", "y": "string", "z": "float32" } }`)
	out := ds.FromJsonStringOrPanic(`{ "columns": { "z": "float32", "x": "int64" } }`)

	adapter, err := LookupAutoAdapter(in, out)
	assert.NoError(t, err)

	adapted, err := adapter(builder.PrimitiveRow(int32(5), "Hello World", float32(0.5)))
	assert.NoError(t, err)
	assert.Equal(t, builder.PrimitiveRow(float32(0.5), int64(5)), adapted)
}

// One column, changed name and type
func TestSingleColumnTypeNameChange(t *testing.T) {
	in := ds.FromJsonStringOrPanic(`{ "columns": { "x": "int32" } }`)
	out := ds.FromJsonStringOrPanic(`{ "columns": { "y": "int64" } }`)

	adapter, err := LookupAutoAdapter(in, out)
	assert.NoError(t, err)

	adapted, err := adapter(builder.PrimitiveRow(int32(5)))
	assert.NoError(t, err)
	assert.Equal(t, builder.PrimitiveRow(int64(5)), adapted)
}
