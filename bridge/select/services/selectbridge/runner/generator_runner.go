package runner

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"golang.org/x/sync/errgroup"
	"io"
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

// Runs a select
type selectRunner struct {
	input     GeneratorRunner
	selector  *Runner
	projector *Runner
}

func newSelectRunner(s *SelectProgram) (*selectRunner, error) {
	input := s.Input
	// For compatibility reasons, the input is optional
	if input.Underlying == nil {
		input = TableGeneratorProgramRef{Underlying: &DataSource{Port: 0}}
	}
	inputRunner, err := NewGeneratorRunner(input)
	if err != nil {
		return nil, err
	}
	var selectorRunner *Runner
	if s.Selector != nil {
		selectorRunner, err = CreateRunner(s.Selector)
		if err != nil {
			return nil, errors.Wrap(err, "Could not create Runner for selector")
		}
	}
	var projectorRunner *Runner
	if s.Projector != nil {
		projectorRunner, err = CreateRunner(s.Projector)
		if err != nil {
			return nil, errors.Wrap(err, "Could not create Runner for Projector")
		}
	}
	return &selectRunner{
		input:     inputRunner,
		selector:  selectorRunner,
		projector: projectorRunner,
	}, nil
}

func (s *selectRunner) Run(inputs []element.StreamReader) element.StreamReader {
	inputReader := s.input.Run(inputs)
	return element.NewStreamReader(func() (element.Element, error) {
		for {
			e, err := inputReader.Read()
			if err != nil {
				// May also be EOF
				return nil, err
			}
			tabularRow := e.(*element.TabularRow)
			var isSelected = true
			if s.selector != nil { // if there is no selector, we select them all
				selectResult, err := s.selector.Run(tabularRow.Columns)
				if err != nil {
					return nil, err
				}
				isSelected = selectResult[0].(element.Primitive).X.(bool)
			}
			if isSelected {
				if s.projector == nil {
					return e, nil
				} else {
					projected, err := s.projector.Run(tabularRow.Columns)
					if err != nil {
						return nil, err
					}
					return &element.TabularRow{projected}, nil
				}
			}
		}
	})
}

// Executes unionAll
type allUnionRunner struct {
	inputs  []GeneratorRunner
	inOrder bool
}

func (a *allUnionRunner) Run(inputs []element.StreamReader) element.StreamReader {
	channel := make(chan element.Element, 1)
	eg := errgroup.Group{}

	if a.inOrder {
		eg.Go(func() error {
			for _, input := range a.inputs {
				subReader := input.Run(inputs)
				for {
					e, err := subReader.Read()
					if err == io.EOF {
						break
					}
					if err != nil {
						return err
					}
					channel <- e
				}
			}
			return nil
		})
	} else {
		for _, input := range a.inputs {
			func(input GeneratorRunner) {
				subReader := input.Run(inputs)

				eg.Go(func() error {
					for {
						e, err := subReader.Read()
						if err == io.EOF {
							return nil
						}
						if err != nil {
							return err
						}
						channel <- e
					}
				})
			}(input)
		}
	}

	var finalResult error
	go func() {
		finalResult = eg.Wait()
		channel <- nil
	}()

	return element.NewStreamReader(func() (element.Element, error) {
		e := <-channel
		if e == nil {
			close(channel)
			if finalResult == nil {
				return nil, io.EOF
			} else {
				return nil, finalResult
			}
		} else {
			return e, nil
		}
	})
}

func newUnionRunner(u *UnionProgram) (GeneratorRunner, error) {
	inputRunners := make([]GeneratorRunner, len(u.Inputs), len(u.Inputs))
	for idx, input := range u.Inputs {
		inputRunner, err := NewGeneratorRunner(input)
		if err != nil {
			return nil, err
		}
		inputRunners[idx] = inputRunner
	}
	allUnion := &allUnionRunner{inputRunners, u.InOrder}
	if u.All {
		return allUnion, nil
	} else {
		return &distinctRunner{
			u.Result,
			allUnion,
		}, nil
	}
}

// Executes DISTINCT (or union without all)
type distinctRunner struct {
	resultingType *ds.TabularData
	input         GeneratorRunner
}

func (d *distinctRunner) Run(inputs []element.StreamReader) element.StreamReader {
	inputReader := d.input.Run(inputs)
	elementSet, err := NewElementSet(d.resultingType)
	if err != nil {
		return element.NewStreamReader(func() (element.Element, error) {
			return nil, err
		})
	}
	return element.NewStreamReader(func() (element.Element, error) {
		for {
			e, err := inputReader.Read()
			if err != nil {
				elementSet.Clear()
				return nil, err
			}
			added, err := elementSet.Add(e)
			if err != nil {
				return nil, err
			}
			if added {
				return e, nil
			}
		}
	})
}
