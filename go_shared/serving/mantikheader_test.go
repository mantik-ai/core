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
package serving

import (
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/mantik-ai/core/go_shared/util/yaml"
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestParseMantikHeader(t *testing.T) {
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
`
	parsed, err := ParseMantikHeader([]byte(file))
	assert.NoError(t, err)
	assert.Equal(t, parsed.Kind(), "algorithm")
	expectedIn := ds.FromJsonStringOrPanic(`{"columns":{"a": "int32", "b": "int32"}}`)
	expectedOut := ds.FromJsonStringOrPanic(`{"columns":{"z": {"type": "tensor", "shape": [2,3], "componentType": "float32"}}}`)

	json, err := yaml.YamlToJson([]byte(file))
	assert.NoError(t, err)

	assert.Equal(t, json, parsed.Json())

	algoKind := AlgorithmKind
	expected := &AlgorithmMantikHeader{
		Type: &AlgorithmType{
			ds.Ref(expectedIn),
			ds.Ref(expectedOut),
		},
		json: json,
		header: &MantikHeaderMeta{
			Kind:                &algoKind,
			Name:                nil,
			Version:             nil,
			Account:             nil,
			ParsedMetaVariables: nil,
		},
	}

	assert.Equal(t,
		expected, parsed)
}

func TestTrainableMantikHeader(t *testing.T) {
	file :=
		`
name: kmeans
stack: sklearn.simple_learn
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
	parsed, err := ParseMantikHeader([]byte(file))

	json, err := yaml.YamlToJson([]byte(file))
	assert.NoError(t, err)

	assert.Equal(t, json, parsed.Json())

	assert.NoError(t, err)
	assert.Equal(t, "trainable", parsed.Kind())
	casted := parsed.(*TrainableMantikHeader)
	assert.Equal(t, *casted.Name(), "kmeans")
	assert.Equal(t, *casted.TrainingType,
		ds.Ref(ds.FromJsonStringOrPanic(`{"columns":{"coordinates":{"type":"tensor","shape":[2],"componentType":"float64"}}}`)))
	assert.Equal(t, *casted.StatType,
		ds.Ref(ds.FromJsonStringOrPanic(`"void"`)))
	assert.Equal(t, casted.Type.Input,
		ds.Ref(ds.FromJsonStringOrPanic(`{"columns":{"coordinates":{"type":"tensor","shape":[2],"componentType":"float64"}}}`)))
	assert.Equal(t, casted.Type.Output,
		ds.Ref(ds.FromJsonStringOrPanic(`{"columns":{"label":"int32"}}`)))
	assert.Equal(t, "kmeans", *casted.header.NamedMantikId())
}

func TestDataSetMantikHeader(t *testing.T) {
	file := `
kind: dataset
type:
  columns:
    x: int32
`
	parsed, err := ParseMantikHeader([]byte(file))
	assert.NoError(t, err)
	assert.Equal(t, "dataset", parsed.Kind())
	casted := parsed.(*DataSetMantikHeader)
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
	parsed, err := ParseMantikHeader([]byte(file))
	assert.NoError(t, err)

	json, err := DecodeMetaYaml([]byte(file))
	assert.NoError(t, err)

	assert.Equal(t, json, parsed.Json())

	assert.NoError(t, err)
	assert.Equal(t, "dataset", parsed.Kind())
	casted := parsed.(*DataSetMantikHeader)
	assert.Equal(t, ds.FromJsonStringOrPanic(`{"columns":{"x":{"type":"tensor","shape":[100],"componentType":"float32"}}}`), casted.Type.Underlying)
	assert.Equal(t, "foo", parsed.MetaVariables()[0].Name)
}

func TestCombinerMantikHeader(t *testing.T) {
	file := `
kind: combiner
bridge: foo
input:
  - int32
  - float32
output:
  - string
  - columns:
      x: int32
`
	parsed, err := ParseMantikHeader([]byte(file))
	assert.NoError(t, err)

	json, err := DecodeMetaYaml([]byte(file))
	assert.NoError(t, err)
	assert.Equal(t, json, parsed.Json())
	assert.Equal(t, CombinerKind, parsed.Kind())

	casted := parsed.(*CombinerMantikHeader)
	assert.Equal(t, 2, len(casted.Input))
	assert.Equal(t, 2, len(casted.Output))
	assert.Equal(t, ds.Int32, casted.Input[0].Underlying)
	assert.Equal(t, ds.String, casted.Output[0].Underlying)
}
