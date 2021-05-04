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
package ops

import (
	"encoding/json"
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/element/builder"
	"gl.ambrosys.de/mantik/go_shared/ds/formats/natural"
	"gl.ambrosys.de/mantik/go_shared/ds/operations"
	"testing"
)

func TestOpList_UnmarshalJSON_EmptyList(t *testing.T) {
	var l OpList
	err := json.Unmarshal([]byte("[]"), &l)
	assert.NoError(t, err)
	assert.Equal(t, OpList{}, l)
}

func TestOpList_UnmarshalJSON(t *testing.T) {
	allOps := `
		[
			"get",
			1,
			"pop",
			"cnt",
			{
	  			"type" : "string",
	  			"value" : "Hello World"
			},
			"cast",
			"int32",
			"int64",
			"neg",
			"and",
			"or",
			"eq",
			"int32",
			"retf",
			"bn",
			"int32",
			"add",
			"bn",
			"float32",
			"sub",
			"bn",
			"float64",
			"mul",
			"bn",
			"int8",
			"div",
			"isn",
			"unj",
			1,
			2,
			"pn",
			"arrayget",
			"arraysize",
			"structget",
			1
		]
	`
	var l OpList
	err := json.Unmarshal([]byte(allOps), &l)
	assert.NoError(t, err)
	assert.Equal(t, OpList{
		&GetOp{1},
		&PopOp{},
		&ConstantOp{natural.BundleRef{builder.PrimitiveBundle(ds.String, element.Primitive{"Hello World"})}},
		&CastOp{ds.Ref(ds.Int32), ds.Ref(ds.Int64)},
		&NegOp{},
		&AndOp{},
		&OrOp{},
		&EqualsOp{ds.Ref(ds.Int32)},
		&ReturnOnFalseOp{},
		&BinaryOp{ds.Ref(ds.Int32), operations.AddCode},
		&BinaryOp{ds.Ref(ds.Float32), operations.SubCode},
		&BinaryOp{ds.Ref(ds.Float64), operations.MulCode},
		&BinaryOp{ds.Ref(ds.Int8), operations.DivCode},
		&IsNullOp{},
		&UnpackNullableJump{Offset: 1, Drop: 2},
		&PackNullable{},
		&ArrayGet{},
		&ArraySize{},
		&StructGet{1},
	}, l)
}
