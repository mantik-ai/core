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
	"encoding/json"
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/mantik-ai/core/go_shared/ds/element"
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestBinaryJson(t *testing.T) {
	x, err := json.Marshal(AddCode)
	assert.NoError(t, err)
	assert.Equal(t, []byte("\"add\""), x)

	codes := []BinaryOperation{AddCode, SubCode, MulCode, DivCode}
	for _, c := range codes {
		data, err := json.Marshal(c)
		assert.NoError(t, err)
		var back BinaryOperation
		err = json.Unmarshal(data, &back)
		assert.NoError(t, err)
		assert.Equal(t, c, back)
	}
}

func TestFindBinaryFunction(t *testing.T) {
	add, err := FindBinaryFunction(AddCode, ds.Int32)
	assert.NoError(t, err)
	assert.Equal(t, element.Primitive{int32(11)}, add(element.Primitive{int32(5)}, element.Primitive{int32(6)}))

	sub, err := FindBinaryFunction(SubCode, ds.Int64)
	assert.NoError(t, err)
	assert.Equal(t, element.Primitive{int64(243574330118)}, sub(element.Primitive{int64(243574573542)}, element.Primitive{int64(243424)}))

	mul, err := FindBinaryFunction(MulCode, ds.Float64)
	assert.NoError(t, err)
	assert.Equal(t, element.Primitive{float64(1.1456661516367202e+17)}, mul(element.Primitive{float64(53454534.2442)}, element.Primitive{float64(2143253454.24)}))

	div, err := FindBinaryFunction(DivCode, ds.Uint32)
	assert.NoError(t, err)
	assert.Equal(t, element.Primitive{uint32(2000000000)}, div(element.Primitive{uint32(4000000000)}, element.Primitive{uint32(2)}))
}
