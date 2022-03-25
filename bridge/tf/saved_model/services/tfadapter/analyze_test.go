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
	"github.com/mantik-ai/core/go_shared/serving"
	"github.com/stretchr/testify/assert"
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
			"type": "struct",
			"fields": {
				"x": {
					"type": "tensor",
					"shape": [],
					"componentType": "float64"
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
			"type": "struct",
			"fields": {
				"y": {
					"type": "tensor",
					"shape": [],
					"componentType": "float64"
				}
			}
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
