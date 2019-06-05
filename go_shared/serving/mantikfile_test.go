package serving

import (
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"testing"
)

func TestParseMantikFile(t *testing.T) {
	file := `
type:
  input:
    columns: 
      a: int32
      b: int32
  output:
    columns:
      z: 
        type: tensor
        shape: [2,3]
        componentType: float32
directory: my_directory
`
	parsed, err := ParseMantikFile([]byte(file))
	assert.NoError(t, err)
	assert.Equal(t, parsed.Kind(), "algorithm")
	expectedIn := ds.FromJsonStringOrPanic(`{"columns":{"a": "int32", "b": "int32"}}`)
	expectedOut := ds.FromJsonStringOrPanic(`{"columns":{"z": {"type": "tensor", "shape": [2,3], "componentType": "float32"}}}`)

	dir := "my_directory"
	expected := &AlgorithmMantikfile{
		ParsedName: nil,
		Type: &AlgorithmType{
			ds.Ref(expectedIn),
			ds.Ref(expectedOut),
		},
		ParsedDirectory: &dir,
	}

	assert.Equal(t,
		expected, parsed)
}

func TestTrainableMantikFile(t *testing.T) {
	file :=
		`
name: kmeans
stack: sklearn.simple_learn
directory: code
kind: trainable
trainingType:
  columns:
    coordinates:
      type: tensor
      shape: [2]
      componentType: float64
statType: void
type:
  input:
    columns:
      coordinates:
        type: tensor
        shape: [2]
        componentType: float64
  output:
    columns:
      label: int32	
`
	parsed, err := ParseMantikFile([]byte(file))
	assert.NoError(t, err)
	assert.Equal(t, "trainable", parsed.Kind())
	casted := parsed.(*TrainableMantikfile)
	assert.Equal(t, *casted.ParsedName, "kmeans")
	assert.Equal(t, *casted.TrainingType,
		ds.Ref(ds.FromJsonStringOrPanic(`{"columns":{"coordinates":{"type":"tensor","shape":[2],"componentType":"float64"}}}`)))
	assert.Equal(t, *casted.StatType,
		ds.Ref(ds.FromJsonStringOrPanic(`"void"`)))
	assert.Equal(t, casted.Type.Input,
		ds.Ref(ds.FromJsonStringOrPanic(`{"columns":{"coordinates":{"type":"tensor","shape":[2],"componentType":"float64"}}}`)))
	assert.Equal(t, casted.Type.Output,
		ds.Ref(ds.FromJsonStringOrPanic(`{"columns":{"label":"int32"}}`)))
}

func TestDataSetMantikfile(t *testing.T) {
	file := `
kind: dataset
type:
  columns:
    x: int32
`
	parsed, err := ParseMantikFile([]byte(file))
	assert.NoError(t, err)
	assert.Equal(t, "dataset", parsed.Kind())
	casted := parsed.(*DataSetMantikfile)
	assert.Equal(t, ds.FromJsonStringOrPanic(`{"columns":{"x":"int32"}}`), casted.Type.Underlying)
}

func TestMetaVariables(t *testing.T) {
	file := `
kind: dataset
metaVariables:
 - name: foo
   type: int32
   value: 100
type:
  columns:
    x:
      type: tensor
      shape: ["${foo}"]
      componentType: float32
`
	parsed, err := ParseMantikFile([]byte(file))
	assert.NoError(t, err)
	assert.Equal(t, "dataset", parsed.Kind())
	casted := parsed.(*DataSetMantikfile)
	assert.Equal(t, ds.FromJsonStringOrPanic(`{"columns":{"x":{"type":"tensor","shape":[100],"componentType":"float32"}}}`), casted.Type.Underlying)
	assert.Equal(t, "foo", parsed.MetaVariables()[0].Name)
}
