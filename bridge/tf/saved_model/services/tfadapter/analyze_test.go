package tfadapter

import (
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/serving"
	"testing"
)

func TestAnalyzeSimpleMultiply(t *testing.T) {
	model, err := LoadModel("../../test/resources/samples/double_multiply/payload")

	// check compatibility to executable model
	var executable serving.ExecutableAlgorithm = model
	assert.Equal(t, executable.Type(), model.AlgorithmType)

	defer model.Cleanup()
	assert.NoError(t, err)
	assert.Equal(t, false, model.AnalyzeResult.InputTabular)
	assert.Equal(t, false, model.AnalyzeResult.OutputTabular)

	expectedInput, err := ds.FromJsonString(
		`
		{
			"columns": {
				"x": {
					"type": "tensor",
					"shape": [],
					"componentType": "float64"
				}
			},
			"rowCount": 1
		}
		`,
	)
	assert.NoError(t, err)
	assert.Equal(t, expectedInput, model.AlgorithmType.Input.Underlying)

	expectedOutput, err := ds.FromJsonString(
		`
		{
			"columns": {
				"y": {
					"type": "tensor",
					"shape": [],
					"componentType": "float64"
				}
			},
			"rowCount": 1
		}
		`,
	)
	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, model.AlgorithmType.Output.Underlying)

}

func TestAnalyzeMnist(t *testing.T) {
	model, err := LoadModel("../../test/resources/samples/mnist_annotated/payload")
	defer model.Cleanup()
	assert.NoError(t, err)
	assert.Equal(t, true, model.AnalyzeResult.InputTabular)
	assert.Equal(t, true, model.AnalyzeResult.OutputTabular)

	expectedInput, err := ds.FromJsonString(
		`
		{
			"columns": {
				"x": {
					"type": "tensor",
					"shape": [784],
					"componentType": "float32"
				}
			}
		}
		`,
	)
	assert.NoError(t, err)

	assert.Equal(t, expectedInput, model.AlgorithmType.Input.Underlying)

	expectedOutput, err := ds.FromJsonString(
		`
		{
			"columns": {
				"y": {
					"type": "tensor",
					"shape": [10],
					"componentType": "float32"
				}
			}
		}
		`,
	)
	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, model.AlgorithmType.Output.Underlying)
	assert.Equal(t, []string{"y"}, model.AnalyzeResult.OutputOrder)
}

func TestAnalyzeMixedMultiInOut(t *testing.T) {
	model, err := LoadModel("../../test/resources/samples/multi_in_out/payload")
	defer model.Cleanup()

	assert.NoError(t, err)
	assert.Equal(t, true, model.AnalyzeResult.InputTabular)
	assert.Equal(t, true, model.AnalyzeResult.OutputTabular)

	expectedInput, err := ds.FromJsonString(
		`
		{
			"columns": {
				"a": {
					"type": "tensor",
					"shape": [3],
					"componentType": "int64"
				},
				"b": {
					"type": "tensor",
					"shape": [3],
					"componentType": "int64"
				}
			}
		}
		`,
	)
	assert.NoError(t, err)
	assert.Equal(t, expectedInput, model.AlgorithmType.Input.Underlying)

	expectedOutput, err := ds.FromJsonString(
		`
		{
			"columns": {
				"x": {
					"type": "tensor",
					"shape": [3],
					"componentType": "int64"
				},
				"y": {
					"type": "tensor",
					"shape": [],
					"componentType": "int64"
				}
			}
		}
		`,
	)
	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, model.AlgorithmType.Output.Underlying)
}
