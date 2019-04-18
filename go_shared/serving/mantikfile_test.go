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
	assert.Equal(t, parsed.KindToUse(), "algorithm")
	expectedIn := ds.FromJsonStringOrPanic(`{"columns":{"a": "int32", "b": "int32"}}`)
	expectedOut := ds.FromJsonStringOrPanic(`{"columns":{"z": {"type": "tensor", "shape": [2,3], "componentType": "float32"}}}`)

	dir := "my_directory"
	expected := &Mantikfile{
		Name: nil,
		Type: &AlgorithmType{
			ds.Ref(expectedIn),
			ds.Ref(expectedOut),
		},
		Directory: &dir,
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
	assert.Equal(t, *parsed.Name, "kmeans")
	assert.Equal(t, parsed.KindToUse(), "trainable")
	assert.Equal(t, *parsed.Kind, "trainable")
	assert.Equal(t, *parsed.TrainingType,
		ds.Ref(ds.FromJsonStringOrPanic(`{"columns":{"coordinates":{"type":"tensor","shape":[2],"componentType":"float64"}}}`)))
	assert.Equal(t, *parsed.StatType,
		ds.Ref(ds.FromJsonStringOrPanic(`"void"`)))
	assert.Equal(t, parsed.Type.Input,
		ds.Ref(ds.FromJsonStringOrPanic(`{"columns":{"coordinates":{"type":"tensor","shape":[2],"componentType":"float64"}}}`)))
	assert.Equal(t, parsed.Type.Output,
		ds.Ref(ds.FromJsonStringOrPanic(`{"columns":{"label":"int32"}}`)))
}
