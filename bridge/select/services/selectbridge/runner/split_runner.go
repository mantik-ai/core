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
	"github.com/mantik-ai/core/go_shared/ds/element"
	"github.com/pkg/errors"
	"golang.org/x/sync/errgroup"
	"math/rand"
)

type SplitRunner struct {
	Input   GeneratorRunner
	Program *SplitProgram
}

func newSplitRunner(p *SplitProgram) (*SplitRunner, error) {
	input, err := NewGeneratorRunner(p.Input)
	if err != nil {
		return nil, err
	}
	return &SplitRunner{
		input,
		p,
	}, nil
}

func (s SplitRunner) Run(input []element.StreamReader, output []element.StreamWriter) error {
	if len(output) != len(s.Program.Fractions)+1 {
		return errors.New("Invalid output count")
	}

	incoming := s.Input.Run(input)
	all, err := element.ReadAllFromStreamReader(incoming)
	if err != nil {
		return err
	}

	elementCount := len(all)

	if s.Program.ShuffleSeed != nil {
		r := rand.New(rand.NewSource(*s.Program.ShuffleSeed))
		r.Shuffle(elementCount, func(i, j int) {
			t := all[i]
			all[i] = all[j]
			all[j] = t
		})
	}

	splitBorders := buildSplitBorders(elementCount, s.Program.Fractions)

	eg := errgroup.Group{}
	for i := 0; i < len(output); i++ {
		func(outIdx int) {
			eg.Go(func() error {
				for eIdx := splitBorders[outIdx]; eIdx < splitBorders[outIdx+1]; eIdx += 1 {
					row := all[eIdx]
					err := output[outIdx].Write(row)
					if err != nil {
						return err
					}
				}
				return nil
			})
		}(i)
	}
	return eg.Wait()
}

func buildSplitBorders(elementCount int, fractions []float64) []int {
	borders := []int{0}

	var current float64 = 0.0
	for _, fraction := range fractions {
		current = current + fraction
		var border = (int)(current * float64(elementCount))
		if border < 0 {
			border = 0
		} else if border > elementCount {
			border = elementCount
		}
		borders = append(borders, border)
	}
	borders = append(borders, elementCount)
	return borders
}
