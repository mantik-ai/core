package selectbridge

import (
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds/element/builder"
	"testing"
)

func TestFlip(t *testing.T) {
	model, err := LoadModel("../../examples/flip")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())
	assert.Equal(t, model.NativeType(), model.Type())

	input := builder.RowsAsElements(
		builder.PrimitiveRow(int32(1), "Hello"),
		builder.PrimitiveRow(int32(2), "World"),
	)

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow("Hello", int32(1)),
		builder.PrimitiveRow("World", int32(2)),
	)

	transformed, err := model.Execute(input)
	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}

func TestFilter1(t *testing.T) {
	model, err := LoadModel("../../examples/filter1")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())
	assert.Equal(t, model.NativeType(), model.Type())

	input := builder.RowsAsElements(
		builder.PrimitiveRow(int32(1), "Hello"),
		builder.PrimitiveRow(int32(2), "World"),
		builder.PrimitiveRow(int32(1), "!!!"),
	)

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(int32(1), "Hello"),
		builder.PrimitiveRow(int32(1), "!!!"),
	)

	transformed, err := model.Execute(input)
	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}

func TestCalculation(t *testing.T) {
	model, err := LoadModel("../../examples/calculation1")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())
	assert.Equal(t, model.NativeType(), model.Type())

	input := builder.RowsAsElements(
		builder.PrimitiveRow(int32(4), int32(5)),
		builder.PrimitiveRow(int32(10), int32(3)),
	)

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(int32(9), int32(-1)),
		builder.PrimitiveRow(int32(13), int32(7)),
	)

	transformed, err := model.Execute(input)
	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}

func TestFallThrough(t *testing.T) {
	model, err := LoadModel("../../examples/empty1")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())
	assert.Equal(t, model.NativeType(), model.Type())

	input := builder.RowsAsElements(
		builder.PrimitiveRow(int32(4), "Hello"),
		builder.PrimitiveRow(int32(10), "World"),
	)

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(int32(4), "Hello"),
		builder.PrimitiveRow(int32(10), "World"),
	)

	transformed, err := model.Execute(input)
	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}

func TestCast(t *testing.T) {
	model, err := LoadModel("../../examples/cast1")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())
	assert.Equal(t, model.NativeType(), model.Type())

	input := builder.RowsAsElements(
		builder.PrimitiveRow(int32(4), "100"),
		builder.PrimitiveRow(int32(8), "-200"),
	)

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(int64(4), int32(100)),
		builder.PrimitiveRow(int64(8), int32(-200)),
	)

	transformed, err := model.Execute(input)
	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}

func TestComplex1(t *testing.T) {
	model, err := LoadModel("../../examples/complex1")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())
	assert.Equal(t, model.NativeType(), model.Type())

	input := builder.RowsAsElements(
		builder.PrimitiveRow(int32(100), int32(200), "Hello"),
		builder.PrimitiveRow(int32(100), int32(300), "World"),
		builder.PrimitiveRow(int32(200), int32(400), "World"),
	)
	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(int32(300), "Hello"),
		builder.PrimitiveRow(int32(400), "World"),
	)
	transformed, err := model.Execute(input)
	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}

func TestNullHandling(t *testing.T) {
	model, err := LoadModel("../../examples/null")
	assert.NoError(t, err)

	input := builder.RowsAsElements(
		builder.PrimitiveRow(int32(100), "Hello"),
		builder.PrimitiveRow(nil, "??"),
		builder.PrimitiveRow(int32(200), "World"),
	)

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(int32(100), "Hello"),
		builder.PrimitiveRow(int32(200), "World"),
	)

	transformed, err := model.Execute(input)
	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}
