package runner

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
)

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
