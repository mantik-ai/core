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
package adapt

import (
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/mantik-ai/core/go_shared/ds/element"
	"github.com/pkg/errors"
)

type columnAdapter struct {
	sourcePos int
	cast      *Cast
}

func lookupTableAdapter(from *ds.TabularData, to *ds.TabularData) (*Cast, error) {
	columnCount := to.Columns.Arity()
	columnAdapters := make([]columnAdapter, columnCount)

	var loosing = false
	var canFail = false

	columnMapping, err := matchColumnNames(from, to)
	if err != nil {
		return nil, err
	}

	for toIndex, fromIndex := range columnMapping {
		fromColumnType := from.Columns.Values[fromIndex].SubType
		toColumnType := to.Columns.Values[toIndex].SubType
		subAdapter, err := LookupCast(fromColumnType.Underlying, toColumnType.Underlying)
		if err != nil {
			return nil, err
		}
		columnAdapters[toIndex] = columnAdapter{fromIndex, subAdapter}
		loosing = loosing || subAdapter.Loosing
		canFail = canFail || subAdapter.CanFail
	}

	var rowAdapter = func(in *element.TabularRow) (*element.TabularRow, error) {
		var result = make([]element.Element, columnCount)
		for i := 0; i < columnCount; i++ {
			converted, err := columnAdapters[i].cast.Adapter(in.Columns[columnAdapters[i].sourcePos])
			if err != nil {
				return nil, err
			}
			result[i] = converted
		}
		return &element.TabularRow{result}, nil
	}

	var result Adapter = func(in element.Element) (element.Element, error) {
		// We can adapt embedded tabular elements, but also plain rows
		// This is a design bug, see #53
		inRow, isRow := in.(*element.TabularRow)
		if isRow {
			return rowAdapter(inRow)
		}
		inTabular := in.(*element.EmbeddedTabularElement)
		resultRows := make([]*element.TabularRow, len(inTabular.Rows))
		for i, inRow := range inTabular.Rows {
			converted, err := rowAdapter(inRow)
			if err != nil {
				return nil, err
			}
			resultRows[i] = converted
		}
		return &element.EmbeddedTabularElement{resultRows}, nil
	}
	return &Cast{
		From:    from,
		To:      to,
		Loosing: loosing,
		CanFail: canFail,
		Adapter: result,
	}, nil
}

// Find matching column names when matching from to to, returns a list of indices into the from tabular data.
func matchColumnNames(from *ds.TabularData, to *ds.TabularData) ([]int, error) {
	if from.Columns.Arity() < to.Columns.Arity() {
		return nil, errors.New("Less than necessary columns provided")
	}
	result := make([]int, to.Columns.Arity())
	missingCount := 0
	var firstMissing int
	for idx, column := range to.Columns.Values {
		fromIdx := from.Columns.IndexOf(column.Name)
		if fromIdx >= 0 {
			// ok
			result[idx] = fromIdx
		} else {
			// missing
			result[idx] = -1
			missingCount += 1
			if missingCount == 1 {
				firstMissing = idx
			}
		}
	}
	if missingCount > 1 {
		return nil, errors.Errorf("%d columns not found in source tabular data (first = %s)", missingCount, to.Columns.Values[firstMissing].Name)
	}
	if missingCount == 0 {
		return result, nil
	}
	// special case, one is missing, if the count is the same, we can guess it.
	if from.Columns.Arity() > to.Columns.Arity() {
		return nil, errors.Errorf("Cannot resolve %s as source table has more elements", to.Columns.Values[firstMissing].Name)
	}
	fields := make([]bool, from.Columns.Arity())
	for _, v := range result {
		if v >= 0 {
			fields[v] = true
		}
	}
	for idx, v := range fields {
		if !v {
			// this is the one we are looking for
			result[firstMissing] = idx
		}
	}
	return result, nil
}
