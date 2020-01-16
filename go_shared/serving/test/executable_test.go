package test

import (
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/element/builder"
	"gl.ambrosys.de/mantik/go_shared/serving"
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
