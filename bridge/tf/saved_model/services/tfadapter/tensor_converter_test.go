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
package tfadapter

import (
	"github.com/stretchr/testify/assert"
	"github.com/tensorflow/tensorflow/tensorflow/go"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/element/builder"
	"testing"
)

func TestAllocateMultiDimensionalSliceSimple(t *testing.T) {
	r := AllocateMultiDimensionalSlice(ds.Int32.GoType, []int{1})
	rawValue := r.Interface()
	casted, ok := rawValue.([]int32)
	assert.True(t, ok)
	assert.Equal(t, 1, len(casted))
	assert.Equal(t, int32(0), casted[0])
}

func TestAllocateMultiDimensionalSlice2d(t *testing.T) {
	r := AllocateMultiDimensionalSlice(ds.String.GoType, []int{2, 3})
	rawValue := r.Interface()
	casted, ok := rawValue.([][]string)
	assert.True(t, ok)
	assert.Equal(t, 2, len(casted))
	for i := 0; i < 2; i++ {
		assert.Equal(t, len(casted[i]), 3)
		for j := 0; j < 3; j++ {
			assert.Equal(t, "", casted[i][j])
		}
	}
}

func TestAllocateMultDimensionalSlice3d(t *testing.T) {
	r := AllocateMultiDimensionalSlice(ds.Float64.GoType, []int{2, 3, 4})
	rawValue := r.Interface()
	casted, ok := rawValue.([][][]float64)
	assert.True(t, ok)
	assert.Equal(t, 2, len(casted))
	for i := 0; i < 2; i++ {
		assert.Equal(t, len(casted[i]), 3)
		for j := 0; j < 3; j++ {
			assert.Equal(t, len(casted[i][j]), 4)
			for k := 0; k < 4; k++ {
				assert.Equal(t, float64(0), casted[i][j][k])
			}
		}
	}
}

func TestAllocateMultDimensionalSliceWithEmptySlices(t *testing.T) {
	r := AllocateMultiDimensionalSlice(ds.Float64.GoType, []int{2, 0, 4})
	rawValue := r.Interface()
	casted, ok := rawValue.([][][]float64)
	assert.True(t, ok)
	assert.Equal(t, 2, len(casted))
	for i := 0; i < 2; i++ {
		assert.Equal(t, len(casted[i]), 0)
	}
}

func TestFillSliceFromDelinearized(t *testing.T) {
	r := AllocateMultiDimensionalSlice(ds.Int8.GoType, []int{2})
	FillSliceFromDelinearized([]int{2}, r, []int8{1, 2})
	casted, ok := r.Interface().([]int8)
	assert.True(t, ok)
	assert.Equal(t, 2, len(casted))
	assert.Equal(t, int8(1), casted[0])
	assert.Equal(t, int8(2), casted[1])
}

func TestFillSliceFromDelinearized2d(t *testing.T) {
	r := AllocateMultiDimensionalSlice(ds.Int8.GoType, []int{2, 3})
	FillSliceFromDelinearized([]int{2, 3}, r, []int8{1, 2, 3, 4, 5, 6})
	casted, ok := r.Interface().([][]int8)
	assert.True(t, ok)
	assert.Equal(t, 2, len(casted))
	assert.Equal(t, 3, len(casted[0]))
	assert.Equal(t, 3, len(casted[1]))
	x := 1
	for i := 0; i < 2; i++ {
		for j := 0; j < 3; j++ {
			assert.Equal(t, int8(x), casted[i][j])
			x += 1
		}
	}
}

func TestFillSliceFromDelinearized3d(t *testing.T) {
	r := AllocateMultiDimensionalSlice(ds.Int32.GoType, []int{2, 2, 4})
	FillSliceFromDelinearized([]int{2, 2, 4}, r, []int32{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16})
	casted, ok := r.Interface().([][][]int32)
	assert.True(t, ok)
	assert.Equal(t, 2, len(casted))
	assert.Equal(t, 2, len(casted[0]))
	assert.Equal(t, 2, len(casted[1]))
	assert.Equal(t, 4, len(casted[0][1]))
	assert.Equal(t, 4, len(casted[1][0]))
	x := 1
	for i := 0; i < 2; i++ {
		for j := 0; j < 2; j++ {
			for k := 0; k < 4; k++ {
				assert.Equal(t, int32(x), casted[i][j][k])
				x += 1
			}
		}
	}
}

func TestBuildSimpleTensor(t *testing.T) {
	tensor, err := ConvertToTensorFlowTensor(
		&ds.Tensor{
			ds.Ref(ds.Int32),
			[]int{2, 3},
		},
		&element.TensorElement{
			[]int32{1, 2, 3, 4, 5, 6},
		},
	)
	assert.NoError(t, err)
	assert.Equal(t, tensorflow.Int32, tensor.DataType())
	assert.Equal(t, []int64{2, 3}, tensor.Shape())
	expected := [][]int32{
		{1, 2, 3}, {4, 5, 6},
	}
	assert.Equal(t, expected, tensor.Value())
}

func TestBuildScalarTensor(t *testing.T) {
	tensor, err := ConvertToTensorFlowTensor(
		&ds.Tensor{
			ds.Ref(ds.Uint8),
			[]int{},
		},
		&element.TensorElement{
			[]uint8{5},
		},
	)
	assert.NoError(t, err)
	assert.Equal(t, tensorflow.Uint8, tensor.DataType())
	assert.Equal(t, 0, len(tensor.Shape()))
	assert.Equal(t, uint8(5), tensor.Value())
}

func TestBuildTensorFromTabular(t *testing.T) {
	rows := builder.Embedded(
		&element.TabularRow{[]element.Element{element.Primitive{2}, &element.TensorElement{[]int32{1, 2, 3, 4, 5, 6}}}},
		&element.TabularRow{[]element.Element{element.Primitive{5}, &element.TensorElement{[]int32{7, 8, 9, 10, 11, 12}}}},
	)
	tensor, err := ConvertToTensorFlowFromTabularValues(
		&ds.Tensor{
			ds.Ref(ds.Int32),
			[]int{2, 3},
		},
		rows,
		1,
	)
	assert.NoError(t, err)
	assert.Equal(t, tensor.DataType(), tensorflow.Int32)
	assert.Equal(t, tensor.Shape(), []int64{2, 2, 3})
	expected := [][][]int32{
		{
			{1, 2, 3}, {4, 5, 6},
		},
		{
			{7, 8, 9}, {10, 11, 12},
		},
	}
	assert.Equal(t, tensor.Value(), expected)
}

func TestBuildEmptyTensor(t *testing.T) {
	tensor, err := ConvertToTensorFlowTensor(
		&ds.Tensor{
			ds.Ref(ds.Float32),
			[]int{0, 768},
		},
		&element.TensorElement{
			[]float32{},
		},
	)
	assert.NoError(t, err)
	assert.Equal(t, []int64{0, 768}, tensor.Shape())
}

func TestBuildEmptyRowsTensor(t *testing.T) {
	rows := builder.Embedded()
	tensor, err := ConvertToTensorFlowFromTabularValues(
		&ds.Tensor{
			ds.Ref(ds.Int32),
			[]int{768},
		},
		rows,
		1,
	)
	assert.NoError(t, err)
	assert.Equal(t, []int64{0, 768}, tensor.Shape())
}

func TestConvertFromTensorFlow(t *testing.T) {
	values := [][]int32{{1, 2, 3}, {4, 5, 6}}
	tfTensor, err := tensorflow.NewTensor(
		values,
	)
	assert.NoError(t, err)
	tensorType := ds.Tensor{
		ds.Ref(ds.Int32),
		[]int{2, 3},
	}
	converted, err := ConvertFromTensorFlow(tfTensor, &tensorType)
	assert.NoError(t, err)
	back := converted.Values.([]int32)
	assert.Equal(t, []int32{1, 2, 3, 4, 5, 6}, back)
}

func TestConvertFromTensorFlowTabularValues(t *testing.T) {
	values := [][][]int32{{{1, 2, 3}, {4, 5, 6}}, {{7, 8, 9}, {10, 11, 12}}} // 2,2,3
	tfTensor, err := tensorflow.NewTensor(
		values,
	)
	assert.NoError(t, err)
	tensorType := ds.Tensor{
		ds.Ref(ds.Int32),
		[]int{2, 3},
	}
	converted, err := ConvertFromTensorFlowTabularValues(tfTensor, &tensorType)
	assert.NoError(t, err)
	assert.Equal(t, 2, len(converted))
	assert.Equal(t, []int32{1, 2, 3, 4, 5, 6}, converted[0].Values.([]int32))
	assert.Equal(t, []int32{7, 8, 9, 10, 11, 12}, converted[1].Values.([]int32))
}

func TestConvertFromTensorFlowScalarValues(t *testing.T) {
	values := []int64{1, 2, 3}
	tfTensor, err := tensorflow.NewTensor(
		values,
	)
	assert.NoError(t, err)
	tensorType := ds.Tensor{
		ds.Ref(ds.Int64),
		[]int{},
	}
	converted, err := ConvertFromTensorFlowTabularValues(tfTensor, &tensorType)
	assert.NoError(t, err)
	assert.Equal(t, 3, len(converted))
	assert.Equal(t, []int64{1}, converted[0].Values.([]int64))
	assert.Equal(t, []int64{2}, converted[1].Values.([]int64))
	assert.Equal(t, []int64{3}, converted[2].Values.([]int64))
}
