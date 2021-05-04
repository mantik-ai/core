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
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/element/builder"
	"testing"
)

func TestSimpleTensorAdapter(t *testing.T) {
	from := ds.FromJsonStringOrPanic(`{"type":"tensor", "shape": [2,3], "componentType": "uint8"}`)
	to := ds.FromJsonStringOrPanic(`{"type":"tensor", "shape": [2,3], "componentType": "uint32"}`)
	adapter, err := LookupAutoAdapter(from, to)
	assert.NoError(t, err)
	result, err := adapter(builder.Tensor([]uint8{1, 2, 3, 4, 5, 6}))
	assert.NoError(t, err)
	resultCasted := result.(*element.TensorElement)
	assert.Equal(t, []uint32{1, 2, 3, 4, 5, 6}, resultCasted.Values)
}

func TestSimpleTensorUnpack(t *testing.T) {
	from := ds.FromJsonStringOrPanic(`{"type":"tensor", "shape": [1, 2,3], "componentType": "uint8"}`)
	to := ds.FromJsonStringOrPanic(`{"type":"tensor", "shape": [2,3], "componentType": "uint32"}`)
	adapter, err := LookupAutoAdapter(from, to)
	assert.NoError(t, err)
	result, err := adapter(builder.Tensor([]uint8{1, 2, 3, 4, 5, 6}))
	assert.NoError(t, err)
	resultCasted := result.(*element.TensorElement)
	assert.Equal(t, []uint32{1, 2, 3, 4, 5, 6}, resultCasted.Values)
}

func TestSimpleTensorPackIn(t *testing.T) {
	from := ds.FromJsonStringOrPanic(`{"type":"tensor", "shape": [2,3], "componentType": "uint8"}`)
	to := ds.FromJsonStringOrPanic(`{"type":"tensor", "shape": [1,2,3], "componentType": "uint32"}`)
	adapter, err := LookupAutoAdapter(from, to)
	assert.NoError(t, err)
	result, err := adapter(builder.Tensor([]uint8{1, 2, 3, 4, 5, 6}))
	assert.NoError(t, err)
	resultCasted := result.(*element.TensorElement)
	assert.Equal(t, []uint32{1, 2, 3, 4, 5, 6}, resultCasted.Values)
}

func TestTensorPrimitivePackOut(t *testing.T) {
	from := ds.FromJsonStringOrPanic(`{"type":"tensor", "shape": [1], "componentType": "uint8"}`)
	to := ds.FromJsonStringOrPanic(`"uint32"`)
	adapter, err := LookupAutoAdapter(from, to)
	assert.NoError(t, err)
	result, err := adapter(builder.Tensor([]uint8{6}))
	assert.NoError(t, err)
	resultCasted := result.(element.Primitive)
	assert.Equal(t, uint32(6), resultCasted.X)
}

func TestTensorPrimitivePackIn(t *testing.T) {
	from := ds.FromJsonStringOrPanic(`"float32"`)
	to := ds.FromJsonStringOrPanic(`{"type":"tensor", "shape": [1], "componentType": "float64"}`)
	adapter, err := LookupAutoAdapter(from, to)
	assert.NoError(t, err)
	result, err := adapter(element.Primitive{float32(5)})
	assert.NoError(t, err)
	resultCasted := result.(*element.TensorElement)
	assert.Equal(t, []float64{5.0}, resultCasted.Values)
}
