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
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/mantik-ai/core/go_shared/ds/element/builder"
	"github.com/stretchr/testify/assert"
	"testing"
)

var unionTestInput1 = builder.RowsAsElements(
	builder.PrimitiveRow(int32(1), "Hello"),
	builder.PrimitiveRow(int32(2), "!"),
)

var unionTestInput2 = builder.RowsAsElements(
	builder.PrimitiveRow(int32(1), "Hello"),
	builder.PrimitiveRow(int32(3), "World"),
)

func TestUnion1(t *testing.T) {
	model, err := LoadModel("../../examples/union1")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(int32(1), "Hello"),
		builder.PrimitiveRow(int32(1), "Hello"),
		builder.PrimitiveRow(int32(2), "!"),
		builder.PrimitiveRow(int32(3), "World"),
	)

	transformed, err := model.Execute(unionTestInput1, unionTestInput2)
	sortByElement(transformed, 0, ds.Int32)

	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}

func TestUnionInOrder(t *testing.T) {
	model, err := LoadModel("../../examples/union1_order")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(int32(1), "Hello"),
		builder.PrimitiveRow(int32(2), "!"),
		builder.PrimitiveRow(int32(1), "Hello"),
		builder.PrimitiveRow(int32(3), "World"),
	)

	transformed, err := model.Execute(unionTestInput1, unionTestInput2)

	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}

func TestUnionDistinct(t *testing.T) {
	model, err := LoadModel("../../examples/union1_distinct")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())

	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow(int32(1), "Hello"),
		builder.PrimitiveRow(int32(2), "!"),
		builder.PrimitiveRow(int32(3), "World"),
	)

	transformed, err := model.Execute(unionTestInput1, unionTestInput2)
	sortByElement(transformed, 0, ds.Int32)

	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}

func TestComplexUnion(t *testing.T) {
	model, err := LoadModel("../../examples/union2")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())
	assert.NotNil(t, model.header.Query)

	input1 := builder.RowsAsElements(
		builder.PrimitiveRow(int32(1), "Hello"), // selected
		builder.PrimitiveRow(int32(2), "Foo"),
		builder.PrimitiveRow(int32(1), "World"), // selected
	)
	input2 := builder.RowsAsElements(
		builder.PrimitiveRow(int32(1), "Bam"),
		builder.PrimitiveRow(int32(2), "Bim"), // selected
		builder.PrimitiveRow(int32(3), "Bom"),
	)
	expectedOutput := builder.RowsAsElements(
		builder.PrimitiveRow("Hello"),
		builder.PrimitiveRow("World"),
		builder.PrimitiveRow("Bim"),
	)

	transformed, err := model.Execute(input1, input2)

	assert.NoError(t, err)
	assert.Equal(t, expectedOutput, transformed)
}
