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

var splitTestInput1 = builder.RowsAsElements(
	builder.PrimitiveRow(int32(1)),
	builder.PrimitiveRow(int32(2)),
	builder.PrimitiveRow(int32(3)),
	builder.PrimitiveRow(int32(4)),
	builder.PrimitiveRow(int32(5)),
	builder.PrimitiveRow(int32(6)),
	builder.PrimitiveRow(int32(7)),
	builder.PrimitiveRow(int32(8)),
	builder.PrimitiveRow(int32(9)),
	builder.PrimitiveRow(int32(10)),
)

func TestSplit1(t *testing.T) {
	model, err := LoadModel("../../examples/split1")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())

	expectedOutput1 := splitTestInput1[0:5]

	expectedOutput2 := splitTestInput1[5:7]

	expectedOutput3 := splitTestInput1[7:10]

	transformed, err := model.ExecuteNM(splitTestInput1)
	assert.NoError(t, err)
	assert.Equal(t, 3, len(transformed))

	assert.Equal(t, expectedOutput1, transformed[0])
	assert.Equal(t, expectedOutput2, transformed[1])
	assert.Equal(t, expectedOutput3, transformed[2])
}

func TestSplit2WithShuffle(t *testing.T) {
	model, err := LoadModel("../../examples/split2_shuffled")
	assert.NoError(t, err)
	assert.Nil(t, model.ExtensionInfo())

	transformed, err := model.ExecuteNM(splitTestInput1)
	assert.NoError(t, err)
	assert.Equal(t, 2, len(transformed))

	assert.Equal(t, 6, len(transformed[0]))
	assert.Equal(t, 4, len(transformed[1]))

	transformed2, err := model.ExecuteNM(splitTestInput1)
	assert.NoError(t, err)
	// Shuffle Seed value should make it stable
	assert.Equal(t, transformed, transformed2)

	// Test shuffling
	plainRows := make([]element.Element, 10, 10)
	for i := 0; i < 10; i++ {
		if i > 5 {
			plainRows[i] = transformed[1][i-6]
		} else {
			plainRows[i] = transformed[0][i]
		}
	}
	assert.NotEqual(t, splitTestInput1, plainRows)
}
