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
package element

import "gl.ambrosys.de/mantik/go_shared/ds"

// Tabular DataRows together with their type.
type Bundle struct {
	Type ds.DataType
	Rows []Element
}

// Get a Primitive, assuming that the bundle is tabular, will panic if not existing or wrong type.
func (b *Bundle) GetTabularPrimitive(row int, column int) interface{} {
	return b.Rows[row].(*TabularRow).Columns[column].(Primitive).X
}

// Get a Primitive, assuming that  the bundle is a single element.
func (b *Bundle) GetSinglePrimitive() interface{} {
	return b.Rows[0].(Primitive).X
}

func (b *Bundle) IsTabular() bool {
	_, ok := b.Type.(*ds.TabularData)
	return ok
}

// Return the single value of a bundle. Tabular Bundles are wrapped into an Embedded One.
func (b *Bundle) SingleValue() Element {
	if b.IsTabular() {
		rows := make([]*TabularRow, 0, len(b.Rows))
		for i, row := range b.Rows {
			rows[i] = row.(*TabularRow)
		}
		return &EmbeddedTabularElement{rows}
	} else {
		return b.Rows[0]
	}
}
