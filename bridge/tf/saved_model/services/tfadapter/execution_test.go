package tfadapter

import (
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/element/builder"
	"gl.ambrosys.de/mantik/go_shared/serving"
	"testing"
)

func TestMultiplyExecute(t *testing.T) {
	model, err := LoadModel("../../test/resources/samples/double_multiply/trained_model")
	defer model.Cleanup()

	assert.NoError(t, err)
	inputData := builder.Rows(
		builder.Row(
			builder.Tensor([]float64{1.0}),
		),
	)
	result, err := ExecuteData(model, inputData)

	assert.Equal(t, 1, len(result))
	destinationTensor := result[0].Columns[0].(*element.TensorElement).Values.([]float64)
	assert.Equal(t, []float64{2.0}, destinationTensor)
}

func TestEmptyInput(t *testing.T) {
	model, err := LoadModel("../../test/resources/samples/mnist_annotated/trained_model")
	assert.NoError(t, err)
	defer model.Cleanup()

	assert.Equal(t, true, model.AnalyzeResult.InputTabular)
	assert.Equal(t, true, model.AnalyzeResult.OutputTabular)

	var inputData []*element.TabularRow = nil

	result, err := ExecuteData(model, inputData)
	assert.NoError(t, err)
	assert.Equal(t, 0, len(result))
}

func TestSimpleRow(t *testing.T) {
	model, err := LoadModel("../../test/resources/samples/mnist_annotated/trained_model")
	assert.NoError(t, err)
	defer model.Cleanup()

	numbers := make([]float32, 784)
	inputData := builder.Rows(
		builder.Row(
			builder.Tensor(numbers),
		),
	)
	result, err := ExecuteData(model, inputData)
	assert.NoError(t, err)
	assert.Equal(t, 1, len(result))
}

func TestAdaptedMnist(t *testing.T) {
	model, err := LoadModel("../../test/resources/samples/mnist_annotated/trained_model")
	assert.NoError(t, err)
	defer model.Cleanup()

	input := ds.FromJsonStringOrPanic(
		`
			{
				"columns": {
					"i": {
						"type": "image",
						"width": 28,
						"height": 28,
						"components": {
							"black": {
								"componentType": "uint8"
							}
						}
					}
				}
			}
		`)

	output := ds.FromJsonStringOrPanic(
		`
			{
				"columns": {
					"o": {
						"type": "tensor",
						"shape": [10],
						"componentType": "float64"
					}
				}
			}
		`)

	adapted, err := serving.AutoAdaptExecutableAlgorithm(model, input, output)
	assert.NoError(t, err)

	numbers := make([]byte, 784)
	image := &element.ImageElement{numbers}
	inputData := builder.RowsAsElements(builder.Row(image))
	outputData, err := adapted.Execute(inputData)

	assert.NoError(t, err)
	assert.Equal(t, 1, len(outputData))
}

func TestMultipleRows(t *testing.T) {
	model, err := LoadModel("../../test/resources/samples/mnist_annotated/trained_model")
	assert.NoError(t, err)
	defer model.Cleanup()

	numbers1 := make([]float32, 784)
	numbers2 := make([]float32, 784)

	inputData := builder.Rows(
		builder.Row(builder.Tensor(numbers1)),
		builder.Row(builder.Tensor(numbers2)),
	)

	result, err := ExecuteData(model, inputData)
	assert.NoError(t, err)
	assert.Equal(t, 2, len(result))
}

func TestMultiInOut(t *testing.T) {
	model, err := LoadModel("../../test/resources/samples/multi_in_out/trained_model")
	assert.NoError(t, err)
	defer model.Cleanup()
	inputData := builder.Rows(
		builder.Row(
			builder.Tensor([]int64{1, 2, 3}), builder.Tensor([]int64{4, 5, 6}),
		),
		builder.Row(
			builder.Tensor([]int64{7, 8, 9}), builder.Tensor([]int64{10, 11, 12}),
		),
	)
	result, err := ExecuteData(model, inputData)
	assert.NoError(t, err)

	assert.Equal(t, 2, len(result))
	assert.Equal(t, []int64{5, 7, 9}, result[0].Columns[0].(*element.TensorElement).Values)
	assert.Equal(t, []int64{9}, result[0].Columns[1].(*element.TensorElement).Values)
	assert.Equal(t, []int64{17, 19, 21}, result[1].Columns[0].(*element.TensorElement).Values)
	assert.Equal(t, []int64{21}, result[1].Columns[1].(*element.TensorElement).Values)
}

// Test adaption of an algorithm which just expects one row, into one which can accept multiple ones.
func TestAdaptedSingle(t *testing.T) {
	model, err := LoadModel("../../test/resources/samples/double_multiply/trained_model")
	defer model.Cleanup()

	assert.NoError(t, err)
	assert.Equal(t, 1, *model.Type().FixedElementCount())

	input := ds.FromJsonStringOrPanic(
		`
			{
				"columns": {
					"x" : "float64"
				}
			}
		`)

	output := ds.FromJsonStringOrPanic(
		`
			{
				"columns": {
					"y": "float64"
				}
			}
		`)

	adapted, err := serving.AutoAdaptExecutableAlgorithm(model, input, output)
	assert.NoError(t, err)
	assert.Equal(t, (*int)(nil), adapted.Type().FixedElementCount())

	inputData := builder.RowsAsElements(
		builder.PrimitiveRow(float64(1.0)),
		builder.PrimitiveRow(float64(2.0)),
	)
	result, err := adapted.Execute(inputData)

	assert.Equal(t, 2, len(result))
	assert.Equal(t, float64(2), result[0].(*element.TabularRow).Columns[0].(element.Primitive).X)
	assert.Equal(t, float64(4), result[1].(*element.TabularRow).Columns[0].(element.Primitive).X)

	t.Run("empty", func(t *testing.T) {
		resp, err := adapted.Execute(builder.RowsAsElements())
		assert.NoError(t, err)
		assert.Empty(t, resp)
	})
}
