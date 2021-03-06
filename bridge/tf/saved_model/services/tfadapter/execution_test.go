/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package tfadapter

import (
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/mantik-ai/core/go_shared/ds/element"
	"github.com/mantik-ai/core/go_shared/ds/element/builder"
	"github.com/mantik-ai/core/go_shared/serving"
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestMultiplyExecute(t *testing.T) {
	model, err := LoadModel("../../test/resources/samples/double_multiply/payload")
	defer model.Cleanup()

	assert.NoError(t, err)
	inputData := builder.Struct(
		builder.Tensor([]float64{1.0}),
	)

	println("Model ", ds.ToJsonString(model.AlgorithmType.Input.Underlying))

	result, err := ExecuteData(model, inputData)
	assert.NoError(t, err)

	assert.Equal(t, 1, result.RowCount())
	destinationTensor := result.Get(0, 0).(*element.TensorElement).Values.([]float64)
	assert.Equal(t, []float64{2.0}, destinationTensor)
}

func TestEmptyInput(t *testing.T) {
	model, err := LoadModel("../../test/resources/samples/mnist_annotated/payload")
	assert.NoError(t, err)
	defer model.Cleanup()

	assert.Equal(t, true, model.AnalyzeResult.InputTabular)
	assert.Equal(t, true, model.AnalyzeResult.OutputTabular)

	var inputData = builder.Embedded()

	result, err := ExecuteData(model, inputData)
	assert.NoError(t, err)
	assert.Equal(t, 0, result.RowCount())
}

func TestSimpleRow(t *testing.T) {
	model, err := LoadModel("../../test/resources/samples/mnist_annotated/payload")
	assert.NoError(t, err)
	defer model.Cleanup()

	numbers := make([]float32, 784)
	inputData := builder.Embedded(
		builder.Row(
			builder.Tensor(numbers),
		),
	)
	result, err := ExecuteData(model, inputData)
	assert.NoError(t, err)
	assert.Equal(t, 1, result.RowCount())
}

func TestAdaptedMnist(t *testing.T) {
	model, err := LoadModel("../../test/resources/samples/mnist_annotated/payload")
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
	model, err := LoadModel("../../test/resources/samples/mnist_annotated/payload")
	assert.NoError(t, err)
	defer model.Cleanup()

	numbers1 := make([]float32, 784)
	numbers2 := make([]float32, 784)

	inputData := builder.Embedded(
		builder.Row(builder.Tensor(numbers1)),
		builder.Row(builder.Tensor(numbers2)),
	)

	result, err := ExecuteData(model, inputData)
	assert.NoError(t, err)
	assert.Equal(t, 2, result.RowCount())
}

func TestMultiInOut(t *testing.T) {
	model, err := LoadModel("../../test/resources/samples/multi_in_out/payload")
	assert.NoError(t, err)
	defer model.Cleanup()
	inputData := builder.Embedded(
		builder.Row(
			builder.Tensor([]int64{1, 2, 3}), builder.Tensor([]int64{4, 5, 6}),
		),
		builder.Row(
			builder.Tensor([]int64{7, 8, 9}), builder.Tensor([]int64{10, 11, 12}),
		),
	)
	result, err := ExecuteData(model, inputData)
	assert.NoError(t, err)

	assert.Equal(t, 2, result.RowCount())
	assert.Equal(t, []int64{5, 7, 9}, result.Get(0, 0).(*element.TensorElement).Values)
	assert.Equal(t, []int64{9}, result.Get(0, 1).(*element.TensorElement).Values)
	assert.Equal(t, []int64{17, 19, 21}, result.Get(1, 0).(*element.TensorElement).Values)
	assert.Equal(t, []int64{21}, result.Get(1, 1).(*element.TensorElement).Values)
}

// Test adaption of an algorithm which just expects one row, into one which can accept multiple ones.
func TestAdaptedSingle(t *testing.T) {
	model, err := LoadModel("../../test/resources/samples/double_multiply/payload")
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
