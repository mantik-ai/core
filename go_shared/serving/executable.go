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
	"encoding/json"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
)

/* Defines input and output of an algorithm. */
type AlgorithmType struct {
	Input  ds.TypeReference `json:"input"`
	Output ds.TypeReference `json:"output"`
}

func (at *AlgorithmType) AsJson() string {
	bytes, _ := json.MarshalIndent(at, "", "  ")
	return string(bytes)
}

// Returns a pointer, if input value may only contain a specific number of elements.
func (at *AlgorithmType) FixedElementCount() *int {
	td, ok := at.Input.Underlying.(*ds.TabularData)
	if !ok {
		// not tabular, must be single
		one := 1
		return &one
	}
	if td.RowCount == nil {
		return nil
	}
	return td.RowCount
}

func ParseAlgorithmType(jsonString string) (*AlgorithmType, error) {
	var result AlgorithmType
	err := json.Unmarshal([]byte(jsonString), &result)
	return &result, err
}

/* Base interface for executable things. */
type Executable interface {
	Cleanup()
	// Backend specific information.
	ExtensionInfo() interface{}
}

/* The interface a executable stack must implement */
type ExecutableAlgorithm interface {
	Executable

	Type() *AlgorithmType
	NativeType() *AlgorithmType
	Execute(rows []element.Element) ([]element.Element, error)
}

/** Interface for a trainable algorithm.*/
type TrainableAlgorithm interface {
	Executable

	TrainingType() ds.TypeReference
	StatType() ds.TypeReference
	// Returns the stat type
	Train(rows []element.Element) ([]element.Element, error)

	// Returns the Algorithm Type
	Type() *AlgorithmType

	// The directory where learn results are placed, which can then be imported again
	// (Into what it is imported is the decision of Mantik itself).
	LearnResultDirectory() (string, error)
}

/** Interface for a DataSet. */
type ExecutableDataSet interface {
	Executable
	// Returns the generated type
	Type() ds.TypeReference
	// Returns generated elements
	Get() element.StreamReader
}

/** Interface for a modern transformer from n input to m output data sets.
  Can be used for algorithms, datasets and combiners.
  Can stream data. */
type ExecutableTransformer interface {
	Executable

	// Type of input data slots
	Inputs() []ds.TypeReference
	// Type of output data slots
	Outputs() []ds.TypeReference

	// Execute a single Task
	// Closing is not necessary, done by the caller
	Run(input []element.StreamReader, output []element.StreamWriter) error
}
