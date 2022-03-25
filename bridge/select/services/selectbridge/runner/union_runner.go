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
package runner

import (
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/mantik-ai/core/go_shared/ds/element"
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
