package runner

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
)

/** A Runner for Table Generators */
type GeneratorRunner interface {
	// Run the Generator
	// Inputs: the main input data
	Run(inputs []element.StreamReader) element.StreamReader
}

func NewGeneratorRunner(ref TableGeneratorProgramRef) (GeneratorRunner, error) {
	// TODO: Check that there are no duplicate inputs, as they won't work with the StreamReader
	switch u := ref.Underlying.(type) {
	case *DataSource:
		return &dataSourceRunner{dataSource: u}, nil
	case *SelectProgram:
		return newSelectRunner(u)
	case *UnionProgram:
		return newUnionRunner(u)
	case *JoinProgram:
		return newJoinRunner(u)
	default:
		return nil, errors.Errorf("Unsupported program type %s, FIXME", u.Type())
	}
}

// Runs a data source
type dataSourceRunner struct {
	dataSource *DataSource
}

func (d *dataSourceRunner) Run(inputs []element.StreamReader) element.StreamReader {
	return inputs[d.dataSource.Port]
}
