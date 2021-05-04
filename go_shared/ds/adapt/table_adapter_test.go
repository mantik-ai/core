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
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element/builder"
	"testing"
)

// Two columns, exactly flipped
func TestSimpleTableChange(t *testing.T) {
	in := ds.FromJsonStringOrPanic(`{ "columns": { "x": "int32", "y": "string" } }`)
	out := ds.FromJsonStringOrPanic(`{ "columns": { "y": "string", "x": "int32" } }`)

	adapter, err := LookupAutoAdapter(in, out)
	assert.NoError(t, err)

	adapted, err := adapter(builder.PrimitiveRow(int32(5), "Hello World"))
	assert.NoError(t, err)
	assert.Equal(t, builder.PrimitiveRow("Hello World", int32(5)), adapted)
}

func TestEmbeddedTabularChange(t *testing.T) {
	in := ds.FromJsonStringOrPanic(`{ "columns": { "x": "int32", "y": "string" } }`)
	out := ds.FromJsonStringOrPanic(`{ "columns": { "y": "string", "x": "int32" } }`)

	adapter, err := LookupAutoAdapter(in, out)
	assert.NoError(t, err)

	input := builder.Embedded(
		builder.PrimitiveRow(int32(5), "Hello"),
		builder.PrimitiveRow(int32(6), "World"),
	)
	expected := builder.Embedded(
		builder.PrimitiveRow("Hello", int32(5)),
		builder.PrimitiveRow("World", int32(6)),
	)

	adapted, err := adapter(input)
	assert.NoError(t, err)
	assert.Equal(t, expected, adapted)
}

func TestTableRowCountMismatch(t *testing.T) {
	in := ds.FromJsonStringOrPanic(`{ "columns": { "x": "int32", "y": "string" }, "rowCount": 1 }`)
	out := ds.FromJsonStringOrPanic(`{ "columns": { "y": "string", "x": "int32" } }`)
	// This should work, we can convert something with rowCount = 1 into something wihtout row Count
	_, err := LookupAutoAdapter(in, out)
	assert.NoError(t, err)
	// the other way should not work, we cannot convert no row count into something with row count
	_, err = LookupAutoAdapter(out, in)
	assert.Error(t, err)
}

// Two columns, changed types
func TestSimpleTypeChange(t *testing.T) {
	in := ds.FromJsonStringOrPanic(`{ "columns": { "x": "int32", "y": "string" } }`)
	out := ds.FromJsonStringOrPanic(`{ "columns": { "x": "int64", "y": "string" } }`)

	adapter, err := LookupAutoAdapter(in, out)
	assert.NoError(t, err)

	adapted, err := adapter(builder.PrimitiveRow(int32(5), "Hello World"))
	assert.NoError(t, err)
	assert.Equal(t, builder.PrimitiveRow(int64(5), "Hello World"), adapted)
}

// A table with one column dropped
func TestTableReduce(t *testing.T) {
	in := ds.FromJsonStringOrPanic(`{ "columns": { "x": "int32", "y": "string", "z": "float32" } }`)
	out := ds.FromJsonStringOrPanic(`{ "columns": { "z": "float32", "x": "int64" } }`)

	adapter, err := LookupAutoAdapter(in, out)
	assert.NoError(t, err)

	adapted, err := adapter(builder.PrimitiveRow(int32(5), "Hello World", float32(0.5)))
	assert.NoError(t, err)
	assert.Equal(t, builder.PrimitiveRow(float32(0.5), int64(5)), adapted)
}

// One column, changed name and type
func TestSingleColumnTypeNameChange(t *testing.T) {
	in := ds.FromJsonStringOrPanic(`{ "columns": { "x": "int32" } }`)
	out := ds.FromJsonStringOrPanic(`{ "columns": { "y": "int64" } }`)

	adapter, err := LookupAutoAdapter(in, out)
	assert.NoError(t, err)

	adapted, err := adapter(builder.PrimitiveRow(int32(5)))
	assert.NoError(t, err)
	assert.Equal(t, builder.PrimitiveRow(int64(5)), adapted)
}

func TestSingleRemainingColumnTypeChange(t *testing.T) {
	// Really borderline, however label can be matched
	// and the remaining x and image are compatible
	in := ds.FromJsonStringOrPanic(`{"type":"tabular","columns":{"x":{"type":"image","width":28,"height":28,"components":{"black":{"componentType":"uint8"}}},"label":"uint8"}}`)
	out := ds.FromJsonStringOrPanic(`{"type":"tabular","columns":{"image":{"type":"tensor","componentType":"float32","shape":[28,28]},"label":"int32"}}`)

	_, err := LookupAutoAdapter(in, out)
	assert.NoError(t, err)
}
