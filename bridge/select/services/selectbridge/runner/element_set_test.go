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
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/mantik-ai/core/go_shared/ds/element/builder"
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestElementSet_Add(t *testing.T) {
	format := ds.FromJsonStringOrPanic(`{"columns":{"x":"int32", "y": "float32"}}`)

	set, err := NewElementSet(format)
	assert.NoError(t, err)

	added, err := set.Add(builder.PrimitiveRow(int32(5), float32(1.4)))
	assert.NoError(t, err)
	assert.True(t, added)

	added, err = set.Add(builder.PrimitiveRow(int32(5), float32(1.5)))
	assert.NoError(t, err)
	assert.True(t, added)

	assert.Equal(t, 2, set.Size())

	added, err = set.Add(builder.PrimitiveRow(int32(5), float32(1.4)))
	assert.NoError(t, err)
	assert.False(t, added)

	assert.Equal(t, 2, set.Size())

	added, err = set.Add(builder.PrimitiveRow(int32(4), float32(1.4)))
	assert.NoError(t, err)
	assert.True(t, added)

	added, err = set.Add(builder.PrimitiveRow(int32(4), float32(1.4)))
	assert.NoError(t, err)
	assert.False(t, added)

	assert.Equal(t, 3, set.Size())

	set.Clear()
	assert.Equal(t, 0, set.Size())

	added, err = set.Add(builder.PrimitiveRow(int32(4), float32(1.4)))
	assert.NoError(t, err)
	assert.True(t, added)

	assert.Equal(t, 1, set.Size())
}
