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
	"encoding/json"
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/mantik-ai/core/go_shared/serving"
	"github.com/pkg/errors"
	"log"
	"sort"
	"strconv"
	"strings"
	"tfbridge/pb_vendor/github.com/tensorflow/tensorflow/tensorflow/go/core/protobuf"
)

// The result of Algorithm analysis
// This is an 'extension' field of AnalyzeResult
type TensorFlowAnalyzeResult struct {
	MetaGraphVersion     string                     `json:"metaGraphVersion"`
	TensorFlowVersion    string                     `json:"tensorFlowVersion"`
	TensorFlowGitVersion string                     `json:"tensorFlowGitVersion"`
	Tags                 []string                   `json:"tags"`
	NodeCount            int                        `json:"nodeCount"`
	SignatureKey         string                     `json:"signatureKey"`
	Input                map[string]TensorReference `json:"input"`
	Output               map[string]TensorReference `json:"output"`
	// A Defined order for input values
	InputOrder []string `json:"inputOrder"`
	// A Defined order for output values
	OutputOrder []string `json:"outputOrder"`
	// Input Data can be represented as tabular data
	InputTabular bool `json:"inputTabular"`
	// Output Data can be represented as tabular data
	OutputTabular bool `json:"outputTabular"`
}

type TensorReference struct {
	Type          string  `json:"type"`
	Name          string  `json:"name"`
	Shape         []int64 `json:"shape"`
	OperationName string  `json:"operationName"`
	OutputIndex   int     `json:"outputIndex"`
}

func (a *TensorFlowAnalyzeResult) AsJson() string {
	bytes, _ := json.MarshalIndent(a, "", "  ")
	return string(bytes)
}

func convertTensorInfo(info *protobuf.TensorInfo) TensorReference {
	dimensions := make([]int64, len(info.TensorShape.Dim))

	for i, v := range info.TensorShape.Dim {
		dimensions[i] = v.Size
	}
	parts := strings.Split(info.GetName(), ":")
	var operationName string
	var outputId int

	if len(parts) == 2 {
		operationName = parts[0]
		outputId, _ = strconv.Atoi(parts[1])
	} else {
		operationName = info.GetName()
		outputId = 0
	}

	return TensorReference{
		Type:          info.Dtype.String(),
		Name:          info.GetName(),
		Shape:         dimensions,
		OperationName: operationName,
		OutputIndex:   outputId,
	}
}

func analyze(saved_model *protobuf.SavedModel) (*TensorFlowAnalyzeResult, error) {
	if len(saved_model.MetaGraphs) == 0 {
		return nil, errors.New("No meta graph found")
	}

	if len(saved_model.MetaGraphs) > 1 {
		log.Printf("More than one %d meta graph found", len(saved_model.MetaGraphs))
		// TODO: Look for the one with name "serve"
	}

	metaGraph := saved_model.MetaGraphs[0]

	if len(metaGraph.SignatureDef) == 0 {
		return nil, errors.New("No signature def found")
	}

	var signature *protobuf.SignatureDef
	signatureKey := "serving_default"
	signature = metaGraph.SignatureDef["serving_default"]

	if signature == nil {
		log.Printf("Taking first signature")
		// TODO: Is there a simpler way to get just the first element?!
		v := make([]string, len(metaGraph.SignatureDef))
		idx := 0
		for key, _ := range metaGraph.SignatureDef {

			v[idx] = key
			idx += 1
		}
		signatureKey = v[0]
		signature = metaGraph.SignatureDef[signatureKey]
		log.Printf("Signature key %s", signatureKey)
	}

	defaultSignature := metaGraph.SignatureDef[signatureKey]
	println(defaultSignature)

	inputs := make(map[string](TensorReference))
	var inputOrder []string
	for key, value := range defaultSignature.Inputs {
		inputs[key] = convertTensorInfo(value)
		inputOrder = append(inputOrder, key)
	}
	outputs := make(map[string](TensorReference))
	var outputOrder []string
	for key, value := range defaultSignature.Outputs {
		outputs[key] = convertTensorInfo(value)
		outputOrder = append(outputOrder, key)
	}
	// input, output order is not stable on loading
	// sometimes it reports this, sometimes that
	// also the python notation is not stable (as it's a map)
	// so we sort it to make it stable.
	sort.Strings(inputOrder)
	sort.Strings(outputOrder)

	inputTabular, err := isTabularBlock(inputs)
	if err != nil {
		return nil, err
	}
	outputTabular, err := isTabularBlock(outputs)
	if err != nil {
		return nil, err
	}

	result := TensorFlowAnalyzeResult{
		MetaGraphVersion:     metaGraph.MetaInfoDef.MetaGraphVersion,
		TensorFlowVersion:    metaGraph.MetaInfoDef.TensorflowVersion,
		TensorFlowGitVersion: metaGraph.MetaInfoDef.TensorflowGitVersion,
		NodeCount:            len(metaGraph.GraphDef.Node),
		Tags:                 metaGraph.MetaInfoDef.Tags,
		SignatureKey:         signatureKey,
		Input:                inputs,
		Output:               outputs,
		InputOrder:           inputOrder,
		OutputOrder:          outputOrder,
		InputTabular:         inputTabular,
		OutputTabular:        outputTabular,
	}

	return &result, nil
}

/* Determines the Algorithm Type. */
func determineAlgorithmType(result *TensorFlowAnalyzeResult) (*serving.AlgorithmType, error) {
	input, err := convertBlock(result.InputTabular, result.InputOrder, result.Input)
	if err != nil {
		return nil, errors.Wrap(err, "Could not convert input")
	}
	output, err := convertBlock(result.OutputTabular, result.OutputOrder, result.Output)
	if err != nil {
		return nil, errors.Wrap(err, "Could not convert output")
	}
	algorithmType := serving.AlgorithmType{
		Input:  *input,
		Output: *output,
	}
	return &algorithmType, err
}

func isTabularBlock(values map[string]TensorReference) (bool, error) {
	var allTabular = true
	var oneIsTabular = false
	for _, x := range values {
		// if the first component is variadic, it's assumed to be variadic
		var thisIsTabular = false
		if len(x.Shape) > 0 {
			thisIsTabular = x.Shape[0] == -1
		}
		allTabular = allTabular && thisIsTabular
		oneIsTabular = oneIsTabular || thisIsTabular
	}
	if !allTabular && oneIsTabular {
		// TODO: We could relax this a little bit, #9
		return false, errors.New("Not supported: either all tensors must have dynamic shape or no of them.")
	}
	return allTabular, nil
}

// Convert a block of tensor references, returns
func convertBlock(isTabular bool, order []string, values map[string]TensorReference) (*ds.TypeReference, error) {
	columns := []ds.NamedType{}
	for _, n := range order {
		x := values[n]
		convertedTensor, err := convertTensorReference(isTabular, x)
		if err != nil {
			return nil, err
		}
		columns = append(columns, ds.NamedType{Name: n, SubType: ds.Ref(convertedTensor)})
	}

	if isTabular {
		return ds.Refp(&ds.TabularData{Columns: ds.NamedDataTypeMap{columns}}), nil
	} else {
		return ds.Refp(&ds.Struct{Fields: ds.NamedDataTypeMap{columns}}), nil
	}
}

func convertTensorReference(stripFirst bool, reference TensorReference) (*ds.Tensor, error) {
	underlyingType, err := convertToDs(reference.Type)
	if err != nil {
		return nil, err
	}
	var shape []int
	if stripFirst {
		shape, err = convertAndCheckShape(reference.Shape[1:])
	} else {
		shape, err = convertAndCheckShape(reference.Shape)
	}
	if err != nil {
		return nil, err
	}

	return &ds.Tensor{
		ds.Ref(underlyingType),
		shape,
	}, nil
}

func convertAndCheckShape(in []int64) ([]int, error) {
	result := make([]int, len(in))
	for i, v := range in {
		if v < 0 {
			return result, errors.New("Dynamic shape not allowed, except for the very first")
		}
		result[i] = (int)(v)
	}
	return result, nil
}
