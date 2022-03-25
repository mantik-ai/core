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
	"github.com/mantik-ai/core/go_shared/ds/element/builder"
	"github.com/stretchr/testify/assert"
	"select/services/selectbridge/ops"
	"testing"
)

func TestSimpleRun(t *testing.T) {
	// returns the 2nd argument
	p := Program{
		2,
		1,
		1,
		ops.OpList{
			&ops.GetOp{1},
			&ops.GetOp{0},
		},
	}
	r, err := CreateRunner(&p)
	assert.NoError(t, err)
	result, err := r.Run(builder.PrimitiveElements(int32(1), int32(2)))
	assert.NoError(t, err)
	assert.Equal(t, builder.PrimitiveElements(int32(2), int32(1)), result)
}
