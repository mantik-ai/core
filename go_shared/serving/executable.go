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
