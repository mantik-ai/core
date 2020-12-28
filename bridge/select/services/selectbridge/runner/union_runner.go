package runner

import (
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"golang.org/x/sync/errgroup"
	"io"
)

// Executes unionAll
type allUnionRunner struct {
	inputs  []GeneratorRunner
	inOrder bool
}

func (a *allUnionRunner) Run(inputs []element.StreamReader) element.StreamReader {
	pipe := element.NewPipeReaderWriter(16)
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
					pipe.Write(e)
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
						pipe.Write(e)
					}
				})
			}(input)
		}
	}

	var finalResult error
	go func() {
		finalResult = eg.Wait()
		if finalResult == nil {
			pipe.Close()
		} else {
			pipe.Fail(finalResult)
		}
	}()

	return pipe
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
		return element.NewFailedReader(err)
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
