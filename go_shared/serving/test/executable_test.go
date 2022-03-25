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
package test

import (
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/mantik-ai/core/go_shared/ds/element"
	"github.com/mantik-ai/core/go_shared/ds/element/builder"
	"github.com/mantik-ai/core/go_shared/serving"
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestAutoAdaptExecutableAlgorithm(t *testing.T) {
	threeTimes := NewThreeTimes()

	expected, err := serving.ParseAlgorithmType(
		`
{
	"input": {
		"columns": {
			"a": "int8"
		}
	},
	"output": {
		"columns": {
			"b": "int64"
		}
	}
}
`)
	assert.NoError(t, err)

	adapted, err := serving.AutoAdaptExecutableAlgorithm(threeTimes, expected.Input.Underlying, expected.Output.Underlying)
	assert.NoError(t, err)
	result, err := adapted.Execute(
		[]element.Element{builder.PrimitiveRow(int8(2))},
	)
	assert.NoError(t, err)
	assert.Equal(t, int64(6), result[0].(*element.TabularRow).Columns[0].(element.Primitive).X)
}

func TestAutoAdaptLearnableAlgorithm(t *testing.T) {
	learnAlg := NewLearnAlgorithm()

	mantikHeader, err := serving.ParseMantikHeader([]byte(
		`{
	"kind":"trainable",
	"trainingType": {
		"columns": {
			"a1": "int8"
		}
	},
	"statType": {
		"columns": {
			"o1": "int64"
		}
	},
	"type": {
		"input": {
			"columns": {
				"t1": "int8"
			}
		},
		"output": {
			"columns": {
				"t2": "int64"
			}
		}
	}
}
`))
	assert.NoError(t, err)

	adapted, err := serving.AutoAdapt(learnAlg, mantikHeader)
	assert.NoError(t, err)
	casted := adapted.(serving.TrainableAlgorithm)
	result, err := casted.Train(
		[]element.Element{
			builder.PrimitiveRow(int8(2)),
			builder.PrimitiveRow(int8(3)),
		},
	)
	assert.NoError(t, err)
	assert.Equal(t, int64(5), result[0].(*element.TabularRow).Columns[0].(element.Primitive).X)

	dir, err := casted.LearnResultDirectory()
	assert.NoError(t, err)
	oldDir, err := learnAlg.LearnResultDirectory()
	assert.NoError(t, err)
	assert.Equal(t, oldDir, dir)
}

func TestAutoAdaptDataSource(t *testing.T) {
	source := NewDataSet()
	mantikHeader, err := serving.ParseMantikHeader([]byte(
		`{
		"kind": "dataset",
		"type": {
			"columns": {
				"x": "string",
				"y": "int64"
			}
		}
	}
`))
	assert.NoError(t, err)
	adapted, err := serving.AutoAdapt(source, mantikHeader)
	assert.NoError(t, err)
	casted, ok := adapted.(serving.ExecutableDataSet)
	assert.True(t, ok)
	assert.Equal(t, casted.Type(), mantikHeader.(*serving.DataSetMantikHeader).Type)
	elements, err := element.ReadAllFromStreamReader(casted.Get())
	assert.NoError(t, err)
	assert.Equal(t, 2, len(elements))
	assert.Equal(t, "Hello", elements[0].(*element.TabularRow).Columns[0].(element.Primitive).X)
	assert.Equal(t, int64(1), elements[0].(*element.TabularRow).Columns[1].(element.Primitive).X)
	assert.Equal(t, int64(2), elements[1].(*element.TabularRow).Columns[1].(element.Primitive).X)
}

func TestAutoAdaptTransformer(t *testing.T) {
	transformer := NewTransformer()
	mantikHeader1, err := serving.ParseMantikHeader([]byte(`
kind: combiner
input:
  - columns:
      x: int32
  - float32
output:
  - float64
`,
	))
	assert.NoError(t, err)

	// Test1, it should return the value if there is no data type change
	adapted, err := serving.AutoAdapt(transformer, mantikHeader1)
	assert.NoError(t, err)
	assert.Equal(t, transformer, adapted)

	mantikHeader2, err := serving.ParseMantikHeader([]byte(`
kind: combiner
input:
  - columns:
      x: int8
  - float32
output:
  - string
`,
	))
	adapted2, err := serving.AutoAdapt(transformer, mantikHeader2)
	assert.NoError(t, err)

	adapted2t := adapted2.(serving.ExecutableTransformer)
	assert.Equal(t, []ds.TypeReference{ds.Ref(ds.String)}, adapted2t.Outputs())
	assert.Equal(t, []ds.TypeReference{
		ds.Ref(ds.BuildTabular().Add("x", ds.Int8).Result()),
		ds.Ref(ds.Float32),
	}, adapted2t.Inputs())

	input1 := element.NewElementBuffer(
		[]element.Element{
			builder.PrimitiveRow(int8(2)),
			builder.PrimitiveRow(int8(4)),
		},
	)
	input2 := element.NewElementBuffer([]element.Element{element.Primitive{float32(2.5)}})

	result := element.ElementBuffer{}
	err = adapted2t.Run(
		[]element.StreamReader{input1, input2}, []element.StreamWriter{&result},
	)
	assert.NoError(t, err)
	assert.Equal(t, []element.Element{
		element.Primitive{"15"},
	}, result.Elements())
}
