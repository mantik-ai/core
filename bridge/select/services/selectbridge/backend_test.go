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
package selectbridge

import (
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/element/builder"
	"testing"
)

func TestFlip(t *testing.T) {
	model, err := LoadModel("../../examples/flip")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())

	input := builder.RowsAsElements(
		builder.PrimitiveRow(int32(1), "Hello"),
		builder.PrimitiveRow(int32(2), "World"),
	)

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow("Hello", int32(1)),
		builder.PrimitiveRow("World", int32(2)),
	)

	transformed, err := model.Execute(input)
	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}

func TestFilter1(t *testing.T) {
	model, err := LoadModel("../../examples/filter1")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())

	input := builder.RowsAsElements(
		builder.PrimitiveRow(int32(1), "Hello"),
		builder.PrimitiveRow(int32(2), "World"),
		builder.PrimitiveRow(int32(1), "!!!"),
	)

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(int32(1), "Hello"),
		builder.PrimitiveRow(int32(1), "!!!"),
	)

	transformed, err := model.Execute(input)
	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}

func TestCalculation(t *testing.T) {
	model, err := LoadModel("../../examples/calculation1")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())

	input := builder.RowsAsElements(
		builder.PrimitiveRow(int32(4), int32(5)),
		builder.PrimitiveRow(int32(10), int32(3)),
	)

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(int32(9), int32(-1)),
		builder.PrimitiveRow(int32(13), int32(7)),
	)

	transformed, err := model.Execute(input)
	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}

func TestCalculation2(t *testing.T) {
	model, err := LoadModel("../../examples/calculation2")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())

	input := builder.RowsAsElements(
		builder.PrimitiveRow(int32(1), "a"),
		builder.PrimitiveRow(int32(2), "b"),
		builder.PrimitiveRow(int32(3), "c"),
	)

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(int32(2), "a"),
		builder.PrimitiveRow(int32(4), "c"),
	)

	transformed, err := model.Execute(input)
	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}

func TestFallThrough(t *testing.T) {
	model, err := LoadModel("../../examples/empty1")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())

	input := builder.RowsAsElements(
		builder.PrimitiveRow(int32(4), "Hello"),
		builder.PrimitiveRow(int32(10), "World"),
	)

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(int32(4), "Hello"),
		builder.PrimitiveRow(int32(10), "World"),
	)

	transformed, err := model.Execute(input)
	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}

func TestCast(t *testing.T) {
	model, err := LoadModel("../../examples/cast1")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())

	input := builder.RowsAsElements(
		builder.PrimitiveRow(int32(4), "100"),
		builder.PrimitiveRow(int32(8), "-200"),
	)

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(int64(4), int32(100)),
		builder.PrimitiveRow(int64(8), int32(-200)),
	)

	transformed, err := model.Execute(input)
	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}

func TestComplex1(t *testing.T) {
	model, err := LoadModel("../../examples/complex1")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())

	input := builder.RowsAsElements(
		builder.PrimitiveRow(int32(100), int32(200), "Hello"),
		builder.PrimitiveRow(int32(100), int32(300), "World"),
		builder.PrimitiveRow(int32(200), int32(400), "World"),
	)
	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(int32(300), "Hello"),
		builder.PrimitiveRow(int32(400), "World"),
	)
	transformed, err := model.Execute(input)
	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}

func TestNullHandling(t *testing.T) {
	model, err := LoadModel("../../examples/null")
	assert.NoError(t, err)

	input := builder.RowsAsElements(
		builder.PrimitiveRow(int32(100), "Hello"),
		builder.PrimitiveRow(nil, "??"),
		builder.PrimitiveRow(int32(200), "World"),
	)

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(int32(100), "Hello"),
		builder.PrimitiveRow(int32(200), "World"),
	)

	transformed, err := model.Execute(input)
	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}

func TestArray1(t *testing.T) {
	model, err := LoadModel("../../examples/array1")
	assert.NoError(t, err)

	input := builder.RowsAsElements(
		builder.Row(
			builder.PrimitiveArray(int32(4), int32(5), int32(6), int32(7)),
		),
		builder.Row(
			builder.PrimitiveArray(),
		),
	)

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(int32(5), int32(4)),
		builder.PrimitiveRow(nil, int32(0)),
	)

	transformed, err := model.Execute(input)
	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}

func TestArray2Nullables(t *testing.T) {
	model, err := LoadModel("../../examples/array2_nullables")
	assert.NoError(t, err)

	input := builder.RowsAsElements(
		builder.Row(
			element.Primitive{nil}, builder.PrimitiveArray(), element.Primitive{nil},
		),
		builder.Row(
			builder.PrimitiveArray("Hello", "World"),
			builder.PrimitiveArray(nil, nil),
			builder.PrimitiveArray("How", "Are", "You?"),
		),
		builder.Row(
			builder.PrimitiveArray(),
			builder.PrimitiveArray("You", "Are", "Nice"),
			builder.PrimitiveArray(nil, nil, nil),
		),
	)

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(nil, nil, nil, int32(0), nil, nil),
		builder.PrimitiveRow("World", int32(2), nil, int32(2), "Are", int32(3)),
		builder.PrimitiveRow(nil, int32(0), "Are", int32(3), nil, int32(3)),
	)

	transformed, err := model.Execute(input)
	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}

func TestStruct1(t *testing.T) {
	model, err := LoadModel("../../examples/struct1")
	assert.NoError(t, err)

	input := builder.RowsAsElements(
		builder.Row(
			element.Primitive{int32(1)},
			&element.StructElement{
				builder.PrimitiveElements("Alice", int32(42)),
			},
		),
		builder.Row(
			element.Primitive{int32(2)},
			&element.StructElement{
				builder.PrimitiveElements("Bob", nil),
			},
		),
	)

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(int32(1), "Alice", int32(42)),
		builder.PrimitiveRow(int32(2), "Bob", nil),
	)

	transformed, err := model.Execute(input)
	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}

func TestStruct2Nullable(t *testing.T) {
	model, err := LoadModel("../../examples/struct2_nullable")
	assert.NoError(t, err)

	input := builder.RowsAsElements(
		builder.Row(
			element.Primitive{int32(1)},
			&element.StructElement{
				builder.PrimitiveElements("Alice", int32(42)),
			},
		),
		builder.Row(
			element.Primitive{int32(2)},
			&element.StructElement{
				builder.PrimitiveElements("Bob", nil),
			},
		),
		builder.Row(
			element.Primitive{int32(3)},
			element.Primitive{nil},
		),
	)

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(int32(1), "Alice", int32(42)),
		builder.PrimitiveRow(int32(2), "Bob", nil),
		builder.PrimitiveRow(int32(3), nil, nil),
	)

	transformed, err := model.Execute(input)
	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}
