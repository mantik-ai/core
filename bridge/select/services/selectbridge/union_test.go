package selectbridge

import (
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element/builder"
	"testing"
)

var unionTestInput1 = builder.RowsAsElements(
	builder.PrimitiveRow(int32(1), "Hello"),
	builder.PrimitiveRow(int32(2), "!"),
)

var unionTestInput2 = builder.RowsAsElements(
	builder.PrimitiveRow(int32(1), "Hello"),
	builder.PrimitiveRow(int32(3), "World"),
)

func TestUnion1(t *testing.T) {
	model, err := LoadModel("../../examples/union1")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(int32(1), "Hello"),
		builder.PrimitiveRow(int32(1), "Hello"),
		builder.PrimitiveRow(int32(2), "!"),
		builder.PrimitiveRow(int32(3), "World"),
	)

	transformed, err := model.Execute(unionTestInput1, unionTestInput2)
	sortByElement(transformed, 0, ds.Int32)

	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}

func TestUnionInOrder(t *testing.T) {
	model, err := LoadModel("../../examples/union1_order")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(int32(1), "Hello"),
		builder.PrimitiveRow(int32(2), "!"),
		builder.PrimitiveRow(int32(1), "Hello"),
		builder.PrimitiveRow(int32(3), "World"),
	)

	transformed, err := model.Execute(unionTestInput1, unionTestInput2)

	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}

func TestUnionDistinct(t *testing.T) {
	model, err := LoadModel("../../examples/union1_distinct")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(int32(1), "Hello"),
		builder.PrimitiveRow(int32(2), "!"),
		builder.PrimitiveRow(int32(3), "World"),
	)

	transformed, err := model.Execute(unionTestInput1, unionTestInput2)
	sortByElement(transformed, 0, ds.Int32)

	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}

func TestComplexUnion(t *testing.T) {
	model, err := LoadModel("../../examples/union2")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())
	assert.NotNil(t, model.header.Query)

	input1 := builder.RowsAsElements(
		builder.PrimitiveRow(int32(1), "Hello"), // selected
		builder.PrimitiveRow(int32(2), "Foo"),
		builder.PrimitiveRow(int32(1), "World"), // selected
	)
	input2 := builder.RowsAsElements(
		builder.PrimitiveRow(int32(1), "Bam"),
		builder.PrimitiveRow(int32(2), "Bim"), // selected
		builder.PrimitiveRow(int32(3), "Bom"),
	)
	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow("Hello"),
		builder.PrimitiveRow("World"),
		builder.PrimitiveRow("Bim"),
	)

	transformed, err := model.Execute(input1, input2)

	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}
