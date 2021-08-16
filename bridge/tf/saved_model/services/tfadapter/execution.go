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
	"github.com/pkg/errors"
	"github.com/tensorflow/tensorflow/tensorflow/go"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
)

func ExecuteData(model *LoadedModel, inputRows element.TabularLikeElement) (element.TabularLikeElement, error) {
	inputFeed, err := buildInputFeed(model, inputRows)
	if err != nil {
		return nil, err
	}
	outputFetches, err := buildOutputFetches(model)
	if err != nil {
		return nil, err
	}
	result, err := model.tfModel.Session.Run(*inputFeed, outputFetches, nil)
	if err != nil {
		return nil, errors.Wrap(err, "Tensorflow execution failed")
	}
	decoded, err := decodeResult(model, result)
	if err != nil {
		return nil, errors.Wrap(err, "Could not decode result")
	}
	return decoded, nil
}

func buildInputFeed(model *LoadedModel, inputRows element.TabularLikeElement) (*map[tensorflow.Output](*tensorflow.Tensor), error) {
	tabularType, ok := model.AlgorithmType.Input.Underlying.(ds.TabularLike)
	if !ok {
		return nil, errors.New("Expected tabular data")
	}
	result := make(map[tensorflow.Output]*tensorflow.Tensor)

	for columnIndex, column := range tabularType.FieldTypes().Values {
		reference := model.AnalyzeResult.Input[column.Name]
		output, err := model.lookupOutput(reference.OperationName, reference.OutputIndex)
		if err != nil {
			return nil, err
		}
		tensor, err := buildInputTensor(model.AnalyzeResult.InputTabular, inputRows, column, columnIndex)
		if err != nil {
			return nil, errors.Wrapf(err, "Could not convert %s", column.Name)
		}

		result[*output] = tensor
	}
	return &result, nil
}

func buildInputTensor(isTabular bool, inputRows element.TabularLikeElement, column ds.NamedType, columnIndex int) (*tensorflow.Tensor, error) {
	tensorType, ok := column.SubType.Underlying.(*ds.Tensor)
	if !ok {
		return nil, errors.Errorf("Can only convert tensors, got %s", column.SubType.Underlying.TypeName())
	}
	if isTabular {
		return ConvertToTensorFlowFromTabularValues(tensorType, inputRows, columnIndex)
	} else {
		if inputRows.RowCount() != 1 {
			return nil, errors.Errorf("Non-Tabular data may only contain a single row")
		}
		tensorElement, ok := inputRows.Get(0, columnIndex).(*element.TensorElement)
		if !ok {
			return nil, errors.Errorf("Expected Tensor element")
		}
		return ConvertToTensorFlowTensor(tensorType, tensorElement)
	}
}

func buildOutputFetches(model *LoadedModel) ([]tensorflow.Output, error) {
	outputFetches := make([]tensorflow.Output, len(model.AnalyzeResult.Output))
	for i, outputName := range model.AnalyzeResult.OutputOrder {
		column := model.AnalyzeResult.Output[outputName]
		output, err := model.lookupOutput(column.OperationName, column.OutputIndex)
		if err != nil {
			return nil, err
		}
		outputFetches[i] = *output
	}
	return outputFetches, nil
}

func decodeResult(model *LoadedModel, tensors []*tensorflow.Tensor) (element.TabularLikeElement, error) {
	columns := make(map[string][]*element.TensorElement)
	var firstColumn *[]*element.TensorElement
	tabularOutputType := model.AlgorithmType.Output.Underlying.(ds.TabularLike)
	ft := tabularOutputType.FieldTypes()
	for i, name := range model.AnalyzeResult.OutputOrder {
		columnType := ft.Get(name)
		if columnType == nil {
			return nil, errors.Errorf("Unexpected output column %s", name)
		}
		tensorType := columnType.(*ds.Tensor)
		outputTensor, err := decodeOutputTensor(model.AnalyzeResult.OutputTabular, tensors[i], tensorType)
		if err != nil {
			return nil, err
		}
		columns[name] = outputTensor
		if firstColumn == nil {
			firstColumn = &outputTensor
		}
	}
	resultData, err := columnsToRows(tabularOutputType, columns)
	if err != nil {
		return nil, err
	}
	return resultData, nil
}

/** Convert multiple columns into single rows, assuming that data is matching. */
func columnsToRows(outputType ds.TabularLike, columns map[string][]*element.TensorElement) (element.TabularLikeElement, error) {
	if outputType.FieldTypes().Arity() != len(columns) {
		return nil, errors.Errorf("Expected %d columns, got %d", outputType.FieldTypes().Arity(), len(columns))
	}

	// Maps index from destination structure to source structure.
	columnCount := outputType.FieldTypes().Arity()
	reorderedSource := make([][]*element.TensorElement, columnCount)
	rowCount := -1
	for i, column := range outputType.FieldTypes().Values {
		resolved := columns[column.Name]
		if resolved == nil {
			return nil, errors.Errorf("Could not find %s", column.Name)
		}
		reorderedSource[i] = resolved
		if rowCount < 0 {
			rowCount = len(resolved)
		} else {
			if rowCount != len(resolved) {
				return nil, errors.Errorf("Inconsistent row count, got %d and %d", rowCount, len(resolved))
			}
		}
	}

	switch outputType.(type) {
	case *ds.TabularData:
		result := make([]*element.TabularRow, rowCount)
		for i := 0; i < rowCount; i++ {
			rowElements := make([]element.Element, columnCount)
			for j := 0; j < columnCount; j++ {
				rowElements[j] = reorderedSource[j][i]
			}
			result[i] = &element.TabularRow{rowElements}
		}
		return &element.EmbeddedTabularElement{result}, nil
	case *ds.Struct:
		rowElements := make([]element.Element, columnCount)
		for j := 0; j < columnCount; j++ {
			rowElements[j] = reorderedSource[j][0]
		}
		return &element.StructElement{rowElements}, nil
	default:
		return nil, errors.New("Unsupported type")
	}
}

func decodeOutputTensor(isTabular bool, tensor *tensorflow.Tensor, format *ds.Tensor) ([]*element.TensorElement, error) {
	if isTabular {
		return ConvertFromTensorFlowTabularValues(tensor, format)
	} else {
		tensorElement, err := ConvertFromTensorFlow(tensor, format)
		if err != nil {
			return nil, err
		}
		return []*element.TensorElement{tensorElement}, nil
	}
}
