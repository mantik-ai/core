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
	"encoding/json"
	"fmt"
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/mantik-ai/core/go_shared/ds/element"
	"github.com/mantik-ai/core/go_shared/ds/element/builder"
	"github.com/mantik-ai/core/go_shared/serving"
	"github.com/pkg/errors"
	"io"
	"io/ioutil"
	"os"
	"path"
)

var threeTimesTypeJson = `
{
	"input": {
		"columns": {
			"x": "int32"
		}
	},
	"output": {
		"columns": {
			"y": "int32"
		}
	}
}
`

type threeTimes struct {
	algorithmType *serving.AlgorithmType
}

func NewThreeTimes() serving.ExecutableAlgorithm {
	return &threeTimes{}
}

func (t *threeTimes) Type() *serving.AlgorithmType {
	if t.algorithmType == nil {
		var x serving.AlgorithmType
		json.Unmarshal([]byte(threeTimesTypeJson), &x)
		t.algorithmType = &x
	}
	return t.algorithmType
}

func (t *threeTimes) NativeType() *serving.AlgorithmType {
	return t.Type()
}

func (t *threeTimes) Execute(rows []element.Element) ([]element.Element, error) {
	var result []element.Element
	for _, v := range rows {
		i := v.(*element.TabularRow).Columns[0].(element.Primitive).X.(int32)
		r := builder.PrimitiveRow(int32(i * 3))
		result = append(result, r)
	}
	return result, nil
}

func (t *threeTimes) Cleanup() {
	// nothing
}

func (t *threeTimes) ExtensionInfo() interface{} {
	panic("implement me")
}

// Training: Return the sum of all Elements
type trainAlgorithm struct {
	learned bool
	sum     int32
	tempDir string
}

func (l *trainAlgorithm) TrainingType() ds.TypeReference {
	return ds.FromJsonStringOrPanicRef(
		`
	{
			"columns": {
				"x": "int32"
			}
	}`)
}

func (l *trainAlgorithm) StatType() ds.TypeReference {
	return ds.FromJsonStringOrPanicRef(`
		{
			"columns": {
				"sum": "int32"
			}
		}
`)
}

func (l *trainAlgorithm) Train(rows []element.Element) ([]element.Element, error) {
	var summer int32 = 0
	for _, v := range rows {
		i := v.(*element.TabularRow).Columns[0].(element.Primitive).X.(int32)
		summer += i
	}
	var result []element.Element
	result = append(result, builder.PrimitiveRow(summer))
	l.learned = true
	l.sum = summer
	return result, nil
}

func NewLearnAlgorithm() *trainAlgorithm {
	dir, err := ioutil.TempDir("", "test")
	if err != nil {
		panic(err.Error())
	}
	return &trainAlgorithm{
		tempDir: dir,
	}
}

func (l *trainAlgorithm) Type() *serving.AlgorithmType {
	t, e := serving.ParseAlgorithmType(threeTimesTypeJson)
	if e != nil {
		panic(e.Error())
	}
	return t
}

func (l *trainAlgorithm) NativeType() *serving.AlgorithmType {
	return l.Type()
}

func (l *trainAlgorithm) Cleanup() {
	os.RemoveAll(l.tempDir)
}

func (l *trainAlgorithm) ExtensionInfo() interface{} {
	panic("implement me")
}

func (l *trainAlgorithm) LearnResultDirectory() (string, error) {
	if !l.learned {
		return "", errors.New("Not learned yet!")
	}
	file := path.Join(l.tempDir, "result")
	output, err := os.OpenFile(file, os.O_WRONLY|os.O_CREATE, 0644)
	defer output.Close()
	if err != nil {
		return "", err
	}
	_, err = fmt.Fprintf(output, "Train result: %d", l.sum)
	if err != nil {
		return "", err
	}
	return l.tempDir, nil
}

type datasetExample struct {
}

func (d *datasetExample) Cleanup() {
	// empty
}

func (d *datasetExample) ExtensionInfo() interface{} {
	panic("implement me")
}

func (d *datasetExample) Type() ds.TypeReference {
	return ds.FromJsonStringOrPanicRef(`
		{
			"columns": {
				"x": "string",
				"y": "int32"
			}
		}
`)
}

func (d *datasetExample) Get() element.StreamReader {
	bundle := builder.Bundle(
		d.Type().Underlying,
		builder.PrimitiveRow("Hello", int32(1)),
		builder.PrimitiveRow("World", int32(2)),
	)
	// stupid cast, TODO
	elements := make([]element.Element, len(bundle.Rows))
	for i, r := range bundle.Rows {
		elements[i] = r
	}
	return element.NewElementBuffer(elements)
}

func NewDataSet() *datasetExample {
	return &datasetExample{}
}

// multiples all inputs with a factor and returns the sum
type transformerExample struct {
}

func (t *transformerExample) Cleanup() {
	// empty
}

func (t *transformerExample) ExtensionInfo() interface{} {
	return nil
}

func (t *transformerExample) Inputs() []ds.TypeReference {
	a := ds.FromJsonStringOrPanicRef(`
		{
			"columns": {
				"x": "int32"
			}
		}
	`)
	b := ds.Ref(ds.Float32)
	return []ds.TypeReference{a, b}
}

func (t *transformerExample) Outputs() []ds.TypeReference {
	return []ds.TypeReference{ds.Ref(ds.Float64)}
}

func (t *transformerExample) Run(input []element.StreamReader, output []element.StreamWriter) error {
	factorElement, err := input[1].Read()
	if err != nil {
		return err
	}
	_, err = input[1].Read()
	if err != io.EOF {
		return errors.New("Expected EOF after one element")
	}
	factor := factorElement.(element.Primitive).X.(float32)
	result := 0.0
	for {
		row, err := input[0].Read()
		if err == io.EOF {
			break
		}
		if err != nil {
			return errors.Wrap(err, "Unexpected read error")
		}
		number := row.(*element.TabularRow).Columns[0].(element.Primitive).X.(int32)
		result = result + ((float64)(number) * (float64)(factor))
	}
	output[0].Write(element.Primitive{result})
	return nil
}

func NewTransformer() *transformerExample {
	return &transformerExample{}
}
