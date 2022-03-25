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
package element

import (
	"github.com/mantik-ai/core/go_shared/ds"
	"math"
)

/* Returns some type samples (for tests). */
func FundamentalTypeSamples() []Bundle {
	return []Bundle{
		makeSample(ds.Uint8, uint8(200)),
		makeSample(ds.Int8, int8(-100)),
		makeSample(ds.Uint32, uint32(4000000000)),
		makeSample(ds.Int32, int32(-2000000000)),
		makeSample(ds.Uint64, uint64(math.MaxUint64)),
		makeSample(ds.Int64, int64(math.MinInt64)),
		makeSample(ds.Float32, float32(2.5)),
		makeSample(ds.Float64, float64(3435346345.32434324)),
		makeSample(ds.Bool, true),
		makeSample(ds.Void, nil),
		makeSample(ds.String, "Hello World"),
	}
}

func makeSample(dataType ds.DataType, i interface{}) Bundle {
	return Bundle{dataType, []Element{Primitive{i}}}
}
