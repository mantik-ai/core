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
	"fmt"
	"github.com/golang/protobuf/proto"
	"github.com/pkg/errors"
	"github.com/tensorflow/tensorflow/tensorflow/go"
	tf "github.com/tensorflow/tensorflow/tensorflow/go"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/serving"
	"io/ioutil"
	"os"
	"tfbridge/pb_vendor/github.com/tensorflow/tensorflow/tensorflow/go/core/protobuf"
)

// Represents a saved Tensorflow Model
type LoadedModel struct {
	tfModel       *tensorflow.SavedModel
	AnalyzeResult *TensorFlowAnalyzeResult
	AlgorithmType *serving.AlgorithmType
}

func (l *LoadedModel) Type() *serving.AlgorithmType {
	return l.AlgorithmType
}

func (l *LoadedModel) NativeType() *serving.AlgorithmType {
	return l.AlgorithmType
}

func (l *LoadedModel) Execute(rows []element.Element) ([]element.Element, error) {
	// TODO: Is this casts expensive, golang lacks covariance?
	inputAsTabularRows := make([]*element.TabularRow, len(rows))
	for i, v := range rows {
		casted, ok := v.(*element.TabularRow)
		if !ok {
			return nil, errors.New("Expected tabular rows")
		}
		inputAsTabularRows[i] = casted
	}
	resultRows, err := ExecuteData(l, inputAsTabularRows)
	if err != nil {
		return nil, err
	}
	convertedRows := make([]element.Element, len(resultRows))
	for i, r := range resultRows {
		convertedRows[i] = r
	}
	return convertedRows, nil
}

func (l *LoadedModel) ExtensionInfo() interface{} {
	return l.AnalyzeResult
}

func (l *LoadedModel) lookupOutput(operationName string, outputIndex int) (*tf.Output, error) {
	op := l.tfModel.Graph.Operation(operationName)
	if op == nil {
		return nil, errors.Errorf("Operation %s not found", operationName)
	}
	if outputIndex < 0 || outputIndex >= op.NumOutputs() {
		return nil, errors.Errorf("Operation %s has no output %d, num outputs %d", operationName, outputIndex, op.NumOutputs())
	}
	result := op.Output(outputIndex)
	return &result, nil
}

func (l *LoadedModel) Cleanup() {
	err := l.tfModel.Session.Close()
	if err != nil {
		fmt.Fprintf(os.Stderr, "Failed to close session %s", err.Error())
	}
}

func LoadModel(directory string) (*LoadedModel, error) {
	protobufModel, err := loadProtobufModel(directory)
	if err != nil {
		return nil, errors.Wrap(err, "Could not load Protobuf model")
	}
	tfAnalyzeResult, err := analyze(protobufModel)
	if err != nil {
		return nil, errors.Wrap(err, "Could not analyze protobuf model")
	}
	algorithmType, err := determineAlgorithmType(tfAnalyzeResult)
	if err != nil {
		return nil, errors.Wrap(err, "Could not determine algorithm type")
	}
	tags := []string{"serve"}
	tfSavedModel, err := tf.LoadSavedModel(directory, tags, &tf.SessionOptions{})
	if err != nil {
		return nil, errors.Wrap(err, "Could not load SavedModel")
	}
	result := LoadedModel{
		tfSavedModel,
		tfAnalyzeResult,
		algorithmType,
	}
	return &result, nil
}

func loadProtobufModel(directory string) (*protobuf.SavedModel, error) {
	content, err := ioutil.ReadFile(directory + "/saved_model.pb")
	if err != nil {
		return nil, err
	}
	savedModel := &protobuf.SavedModel{}
	err = proto.Unmarshal(content, savedModel)
	if err != nil {
		return nil, errors.Wrap(err, "Could not read protobuf definition")
	}
	return savedModel, nil
}
