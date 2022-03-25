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
	"bytes"
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/mantik-ai/core/go_shared/ds/element"
	"github.com/pkg/errors"
	"github.com/tensorflow/tensorflow/tensorflow/go"
	"reflect"
)

// Helper for building tensor flow tensors
// TODO: Optimize, probably slow due the use of reflection during array building
// also there are to many array allocations, however the Interface doesn't give us
// much room for different approaches.
// A better approach is to use the C-API directly

/** Convert a full qualified tensor. */
func ConvertToTensorFlowTensor(tensor *ds.Tensor, tensorElement *element.TensorElement) (*tensorflow.Tensor, error) {
	componentType, ok := tensor.ComponentType.Underlying.(*ds.FundamentalType)
	if !ok {
		return nil, errors.New("Tensors must be based on fundamental types")
	}
	if isEmptyDataShape(tensor.Shape) {
		return buildEmptyTensorFlowTensor(componentType, tensor.Shape)
	}
	if len(tensor.Shape) == 0 {
		return buildScalarTensor(reflect.ValueOf(tensorElement.Values).Index(0).Interface())
	}
	slice := AllocateMultiDimensionalSlice(componentType.GoType, tensor.Shape)
	FillSliceFromDelinearized(tensor.Shape, slice, tensorElement.Values)
	return tensorflow.NewTensor(slice.Interface())
}

/* Build a Tensor from multiple rows, indexing always one element. */
func ConvertToTensorFlowFromTabularValues(tensor *ds.Tensor, rows element.TabularLikeElement, columnIndex int) (*tensorflow.Tensor, error) {
	fullShape := append([]int{rows.RowCount()}, tensor.Shape...)
	componentType, ok := tensor.ComponentType.Underlying.(*ds.FundamentalType)
	if !ok {
		return nil, errors.New("Tensors must be based on fundamental types")
	}
	if isEmptyDataShape(fullShape) {
		return buildEmptyTensorFlowTensor(componentType, fullShape)
	}
	slice := AllocateMultiDimensionalSlice(componentType.GoType, fullShape)
	for i := 0; i < rows.RowCount(); i++ {
		subElement, ok := rows.Get(i, columnIndex).(*element.TensorElement)
		if !ok {
			return nil, errors.New("Selected column is not a tensor element")
		}
		FillSliceFromDelinearized(tensor.Shape, slice.Index(i), subElement.Values)
	}
	return tensorflow.NewTensor(slice.Interface())
}

func buildEmptyTensorFlowTensor(componentType *ds.FundamentalType, shape []int) (*tensorflow.Tensor, error) {
	tfType, err := convertToTensorFlow(componentType)
	if err != nil {
		return nil, err
	}
	// Bug here: Tensorflow doesn't allow us to pass in strings here.
	emptyReader := bytes.Buffer{}
	return tensorflow.ReadTensor(tfType, shapeToInt64(shape), &emptyReader)
}

func buildScalarTensor(valueSlice interface{}) (*tensorflow.Tensor, error) {
	return tensorflow.NewTensor(valueSlice)
}

// Convert a TensorFlow tensor into DS-Element for each row.
func ConvertFromTensorFlow(tensor *tensorflow.Tensor, format *ds.Tensor) (*element.TensorElement, error) {
	plainType := format.ComponentType.Underlying.(*ds.FundamentalType).GoType
	size := format.PackedElementCount()
	value := reflect.ValueOf(tensor.Value())
	resultSlice := reflect.MakeSlice(reflect.SliceOf(plainType), size, size)
	if len(format.Shape) == 0 {
		if len(tensor.Shape()) == 0 {
			resultSlice.Index(0).Set(value)
		} else if len(tensor.Shape()) == 1 && tensor.Shape()[0] == 1 {
			resultSlice.Index(0).Set(value.Index(0))
		} else {
			return nil, errors.New("Tensor shapes do not match")
		}

	} else {
		ReadSliceFromDelinearized(format.Shape, resultSlice, value)
	}
	result := element.TensorElement{
		resultSlice.Interface(),
	}
	return &result, nil
}

// Convert a TensorFlow tensor (tabular) into DS-Element for each row
// Format is given for each row.
func ConvertFromTensorFlowTabularValues(tensor *tensorflow.Tensor, format *ds.Tensor) ([]*element.TensorElement, error) {
	plainType := format.ComponentType.Underlying.(*ds.FundamentalType).GoType
	sliceType := reflect.SliceOf(plainType)
	values := tensor.Value()
	rows := reflect.ValueOf(values)
	rowCount := rows.Len()
	results := make([]*element.TensorElement, rowCount)
	blockLen := format.PackedElementCount()
	if len(format.Shape) == 0 {
		// Scalar Values
		for i := 0; i < rowCount; i++ {
			buf := reflect.MakeSlice(sliceType, 1, 1)
			buf.Index(0).Set(rows.Index(i))
			results[i] = &element.TensorElement{buf.Interface()}
		}
	} else {
		for i := 0; i < rowCount; i++ {
			block := reflect.MakeSlice(sliceType, blockLen, blockLen)
			ReadSliceFromDelinearized(format.Shape, block, rows.Index(i))
			results[i] = &element.TensorElement{block.Interface()}
		}
	}
	return results, nil
}

// Returns true if there is a `0` in the shape
func isEmptyDataShape(shape []int) bool {
	for _, v := range shape {
		if v == 0 {
			return true
		}
	}
	return false
}

func shapeToInt64(shape []int) []int64 {
	result := make([]int64, len(shape))
	for i, v := range shape {
		result[i] = int64(v)
	}
	return result
}

func AllocateMultiDimensionalSlice(t reflect.Type, shape []int) reflect.Value {
	sliceType := multiDimensionalSliceType(t, shape)
	return allocateMultiDimensionalSliceValue(sliceType, shape)
}

func FillSliceFromDelinearized(shape []int, target reflect.Value, source interface{}) {
	sourceAsValue := reflect.ValueOf(source)
	funcSubFill(shape, target, sourceAsValue, 0)
}

func funcSubFill(shape []int, target reflect.Value, source reflect.Value, idx int) int {
	if len(shape) <= 0 {
		panic("0 or negative shape length?!")
	}
	if len(shape) == 1 {
		count := shape[0]
		for i := 0; i < count; i++ {
			target.Index(i).Set(source.Index(idx + i))
		}
		return idx + count
	} else {
		first := shape[0]
		for i := 0; i < first; i++ {
			idx = funcSubFill(shape[1:], target.Index(i), source, idx)
		}
		return idx
	}
}

func ReadSliceFromDelinearized(shape []int, target reflect.Value, source reflect.Value) {
	ReadSliceSub(shape, target, source, 0)
}

func ReadSliceSub(shape []int, target reflect.Value, source reflect.Value, idx int) int {
	if len(shape) <= 0 {
		panic("0 or negative shape length?!")
	}
	if len(shape) == 1 {
		count := shape[0]
		for i := 0; i < count; i++ {
			target.Index(idx + i).Set(source.Index(i))
		}
		return idx + count
	} else {
		first := shape[0]
		for i := 0; i < first; i++ {
			idx = ReadSliceSub(shape[1:], target, source.Index(i), idx)
		}
		return idx
	}
}

func multiDimensionalSliceType(t reflect.Type, shape []int) []reflect.Type {
	sliceTypes := make([]reflect.Type, len(shape))
	currentType := t
	for i := 0; i < len(shape); i++ {
		reverseIdx := len(shape) - 1 - i
		currentType = reflect.SliceOf(currentType)
		sliceTypes[reverseIdx] = currentType
	}
	return sliceTypes
}

func allocateMultiDimensionalSliceValue(sliceTypes []reflect.Type, shape []int) reflect.Value {
	if len(sliceTypes) == 0 {
		panic("Require slice type")
	}
	firstType := sliceTypes[0]
	firstLength := shape[0]
	if firstLength < 0 {
		panic("Length must be >= 0")
	}
	result := reflect.MakeSlice(firstType, firstLength, firstLength)
	if len(sliceTypes) > 1 {
		for i := 0; i < firstLength; i++ {
			subValue := allocateMultiDimensionalSliceValue(sliceTypes[1:], shape[1:])
			result.Index(i).Set(
				subValue,
			)
		}
	}
	return result
}
