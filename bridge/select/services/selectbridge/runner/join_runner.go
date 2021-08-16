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
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"golang.org/x/sync/errgroup"
	"io"
)

type joinRunner struct {
	left      GeneratorRunner
	right     GeneratorRunner
	filter    *Runner
	program   *JoinProgram
	groupType *ds.TabularData
}

func newJoinRunner(j *JoinProgram) (*joinRunner, error) {
	left, err := NewGeneratorRunner(j.Left)
	if err != nil {
		return nil, err
	}
	right, err := NewGeneratorRunner(j.Right)
	if err != nil {
		return nil, err
	}

	groupType := ds.TabularData{
		Columns: ds.NamedDataTypeMap{j.Left.Underlying.TabularResult().Columns.Values[0:j.GroupSize]},
	}

	var filter *Runner
	if j.Filter != nil {
		filter, err = CreateRunner(j.Filter)
		if err != nil {
			return nil, err
		}
	}

	result := joinRunner{
		left:      left,
		right:     right,
		filter:    filter,
		program:   j,
		groupType: &groupType,
	}

	return &result, nil
}

func (j *joinRunner) Run(inputs []element.StreamReader) element.StreamReader {
	ec := errgroup.Group{}
	var leftGrouped *ElementMultiHashMap
	var rightGrouped *ElementMultiHashMap
	ec.Go(func() error {
		lg, err := j.consumeSide(j.left, inputs)
		leftGrouped = lg
		return err
	})
	ec.Go(func() error {
		rg, err := j.consumeSide(j.right, inputs)
		rightGrouped = rg
		return err
	})

	err := ec.Wait()
	if err != nil {
		// Grouping failed
		element.NewFailedReader(err)
	}

	return j.executeJoin(leftGrouped, rightGrouped)
}

func (j *joinRunner) consumeSide(side GeneratorRunner, inputs []element.StreamReader) (*ElementMultiHashMap, error) {
	result, err := NewElementMultiHashMap(j.groupType)
	if err != nil {
		return nil, err
	}
	reader := side.Run(inputs)
	gs := j.program.GroupSize
	for {
		row, err := reader.Read()
		if err != nil {
			if err == io.EOF {
				return result, nil
			} else {
				return result, err
			}
		}
		tabRow := row.(*element.TabularRow)
		group := element.TabularRow{Columns: tabRow.Columns[0:gs]}
		result.Add(&group, tabRow)
	}
}

func (j *joinRunner) executeJoin(left *ElementMultiHashMap, right *ElementMultiHashMap) element.StreamReader {
	pipe := element.NewPipeReaderWriter(16)

	isLeftLike := j.program.JoinType == "left" || j.program.JoinType == "outer"
	isRightLike := j.program.JoinType == "right" || j.program.JoinType == "outer"
	gs := j.program.GroupSize
	ll := j.program.Left.Underlying.TabularResult().Columns.Arity()
	rl := j.program.Right.Underlying.TabularResult().Columns.Arity()
	emptyRightWithoutKey := makeNullRow(rl - gs)
	emptyLeftWithoutKey := makeNullRow(ll - gs)

	go func() {
		err := left.ForEach(func(key string, leftRows []*element.TabularRow) error {
			rightValues := right.Gets(key)

			for _, leftValue := range leftRows {
				var gotValues = false
				for _, rightValue := range rightValues {
					fullValue := &element.TabularRow{immutableAppend(leftValue.Columns, rightValue.Columns)}
					if j.accept(fullValue) {
						gotValues = true
						pipe.Write(j.transformToFinal(fullValue))
					}
				}
				if !gotValues && isLeftLike {
					// Got no matching right element, emitting empty right
					fullValue := &element.TabularRow{
						immutableAppend(leftValue.Columns, leftValue.Columns[0:gs], emptyRightWithoutKey),
					}
					pipe.Write(j.transformToFinal(fullValue))
				}
			}
			return nil
		})
		if err == nil && isRightLike {
			err = right.ForEach(func(key string, rightRows []*element.TabularRow) error {
				for _, rightValue := range rightRows {
					var gotValues = false
					leftValues := left.Gets(key)
					for _, leftValue := range leftValues {
						fullValue := &element.TabularRow{immutableAppend(leftValue.Columns, rightValue.Columns)}
						if j.accept(fullValue) {
							gotValues = true
							break
						}
					}
					if !gotValues {
						rightKey := rightValue.Columns[0:gs]
						fullValue := &element.TabularRow{
							immutableAppend(
								rightKey, emptyLeftWithoutKey, rightValue.Columns,
							),
						}
						pipe.Write(j.transformToFinal(fullValue))
					}
				}
				return nil
			})
		}
		if err != nil {
			pipe.Fail(err)
		} else {
			pipe.Close()
		}
	}()
	return pipe
}

func makeNullRow(len int) []element.Element {
	result := make([]element.Element, len, len)
	for i := 0; i < len; i++ {
		result[i] = element.Primitive{nil}
	}
	return result
}

func immutableAppend(values ...[]element.Element) []element.Element {
	sizeSum := 0
	for _, v := range values {
		sizeSum += len(v)
	}
	result := make([]element.Element, sizeSum, sizeSum)
	pos := 0
	for _, v := range values {
		copy(result[pos:], v)
		pos += len(v)
	}
	return result
}

func (j *joinRunner) accept(row *element.TabularRow) bool {
	if j.filter == nil {
		return true
	} else {
		result, err := j.filter.Run(row.Columns)
		if err != nil {
			panic("Error in filter " + err.Error())
		}
		return result[0].(element.Primitive).X.(bool)
	}
}

func (j *joinRunner) transformToFinal(row *element.TabularRow) *element.TabularRow {
	size := len(j.program.Selector)
	result := make([]element.Element, size, size)
	for idx, sourceIdx := range j.program.Selector {
		result[idx] = row.Columns[sourceIdx]
	}
	return &element.TabularRow{result}
}
