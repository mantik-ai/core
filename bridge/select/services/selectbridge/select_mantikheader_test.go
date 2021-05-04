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
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/element/builder"
	"gl.ambrosys.de/mantik/go_shared/ds/formats/natural"
	"select/services/selectbridge/ops"
	"select/services/selectbridge/runner"
	"testing"
)

// Note: the program doesn't make much sense
var sampleFile string = `
input:
  - int32
  - void
output:
  - columns:
      x: string
program:
  "type": "select"
  "result": {
    "columns": {
      "x": "int32"
    }
  }
  "selector" : {
    "args" : 2,
    "retStackDepth" : 1,
    "stackInitDepth" : 2,
    "ops" : [
      "get",
      1,
      "cnt",
      {
        "type" : "int8",
        "value" : 1
      },
      "cast",
      "int8",
      "int32",
      "eq",
      "int32"
    ]
  }	
  "projector" : {
    "args" : 1,
    "retStackDepth" : 1,
    "stackInitDepth" : 1,
    "ops" : [
      "get",
      0
    ]
  }
`

func TestParseSelectMantikHeader(t *testing.T) {
	mf, err := ParseSelectMantikHeader([]byte(sampleFile))
	assert.NoError(t, err)
	assert.Equal(t, 2, len(mf.Input))
	assert.Equal(t, 1, len(mf.Output))
	assert.Equal(t, ds.Int32, mf.Input[0].Underlying)
	assert.Equal(t, ds.Void, mf.Input[1].Underlying)
	assert.Equal(t, ds.BuildTabular().Add("x", ds.String).Result(), mf.Output[0].Underlying)

	selectProgram := mf.Program.Underlying.(*runner.SelectProgram)

	s := selectProgram.Selector
	assert.Equal(t, 2, s.Args)
	assert.Equal(t, 1, s.RetStackDepth)
	assert.Equal(t, 2, s.StackInitDepth)
	assert.Equal(t, ops.OpList{
		&ops.GetOp{1},
		&ops.ConstantOp{natural.BundleRef{builder.PrimitiveBundle(ds.Int8, element.Primitive{int8(1)})}},
		&ops.CastOp{ds.Ref(ds.Int8), ds.Ref(ds.Int32)},
		&ops.EqualsOp{ds.Ref(ds.Int32)},
	}, s.Ops)

	p := selectProgram.Projector
	assert.Equal(t, 1, p.Args)
	assert.Equal(t, 1, p.StackInitDepth)
	assert.Equal(t, 1, p.RetStackDepth)
	assert.Equal(t, ops.OpList{
		&ops.GetOp{0},
	}, p.Ops)
}
