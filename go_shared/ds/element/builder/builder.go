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
package builder

import (
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
)

func Bundle(dataType ds.DataType, tabularRows ...element.Element) element.Bundle {
	return element.Bundle{dataType, tabularRows}
}

func PrimitiveBundle(dataType ds.DataType, e element.Element) element.Bundle {
	return element.Bundle{dataType, []element.Element{e}}
}

func Rows(tabularRows ...*element.TabularRow) []*element.TabularRow {
	return tabularRows
}

func RowsAsElements(tabularRows ...*element.TabularRow) []element.Element {
	result := make([]element.Element, len(tabularRows))
	for i, v := range tabularRows {
		result[i] = v
	}
	return result
}

func Row(elements ...element.Element) *element.TabularRow {
	return &element.TabularRow{elements}
}

/* A row which consits of primitives only. */
func PrimitiveRow(elements ...interface{}) *element.TabularRow {
	return &element.TabularRow{PrimitiveElements(elements...)}
}

/** An array consisting of primitive values. */
func PrimitiveArray(elements ...interface{}) *element.ArrayElement {
	return &element.ArrayElement{PrimitiveElements(elements...)}
}

// Wraps multiple primitives into elements.
func PrimitiveElements(elements ...interface{}) []element.Element {
	converted := make([]element.Element, len(elements))
	for i := 0; i < len(elements); i++ {
		converted[i] = element.Primitive{elements[i]}
	}
	return converted
}

func Tensor(values interface{}) *element.TensorElement {
	return &element.TensorElement{values}
}

func Embedded(rows ...*element.TabularRow) *element.EmbeddedTabularElement {
	return &element.EmbeddedTabularElement{rows}
}
