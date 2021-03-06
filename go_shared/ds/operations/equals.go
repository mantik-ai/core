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
package operations

import (
	"bytes"
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/mantik-ai/core/go_shared/ds/element"
)

/* Compares two elements and returns true, if they are equal. */
type EqualsOperation func(e1 element.Element, e2 element.Element) bool

/* Returns an equal operation for a data type. */
func FindEqualsOperation(dataType ds.DataType) EqualsOperation {
	ft, isFt := dataType.(*ds.FundamentalType)
	if isFt {
		if ft == ds.Void {
			return func(e1 element.Element, e2 element.Element) bool {
				return true
			}
		}
		lookuped, err := lookupFundamentalEquals(ft)
		if err != nil {
			// This should not happen
			panic(err.Error())
		}
		return lookuped
	}
	tabular, isTabular := dataType.(*ds.TabularData)
	if isTabular {
		return buildTabularComparison(tabular)
	}
	image, isImage := dataType.(*ds.Image)
	if isImage {
		return buildImageComparison(image)
	}
	tensor, isTensor := dataType.(*ds.Tensor)
	if isTensor {
		return buildTensorComparison(tensor)
	}
	panic("Not implemented fundamental compare")
}

func buildTabularComparison(data *ds.TabularData) EqualsOperation {
	rowComparer := buildTableRowComparer(data)
	return func(e1 element.Element, e2 element.Element) bool {
		tab1 := e1.(*element.EmbeddedTabularElement)
		tab2 := e2.(*element.EmbeddedTabularElement)
		if len(tab1.Rows) != len(tab2.Rows) {
			return false
		}
		for rowIdx, row1 := range tab1.Rows {
			row2 := tab2.Rows[rowIdx]
			if !rowComparer(row1, row2) {
				return false
			}
		}
		return true
	}
}

func buildTableRowComparer(data *ds.TabularData) func(*element.TabularRow, *element.TabularRow) bool {
	subCompares := make([]EqualsOperation, data.Columns.Arity())
	for i, c := range data.Columns.Values {
		subCompares[i] = FindEqualsOperation(c.SubType.Underlying)
	}
	return func(row1 *element.TabularRow, row2 *element.TabularRow) bool {
		for i, c := range subCompares {
			if !c(row1.Columns[i], row2.Columns[i]) {
				return false
			}
		}
		return true
	}
}

func buildImageComparison(data *ds.Image) EqualsOperation {
	return func(e1 element.Element, e2 element.Element) bool {
		return bytes.Compare(e1.(*element.ImageElement).Bytes, e2.(*element.ImageElement).Bytes) == 0
	}
}

func buildTensorComparison(data *ds.Tensor) EqualsOperation {
	ft := data.ComponentType.Underlying.(*ds.FundamentalType) // only fundamental types allowed in tensors.
	c, err := lookupTensorEquals(ft)
	if err != nil {
		panic("No tensor comparison found")
	}
	return c
}
