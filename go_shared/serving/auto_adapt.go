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
package serving

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/adapt"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
)

func AutoAdapt(executable Executable, mantikHeader MantikHeader) (Executable, error) {
	switch e := executable.(type) {
	case ExecutableAlgorithm:
		mf := mantikHeader.(*AlgorithmMantikHeader)
		return AutoAdaptExecutableAlgorithm(e, mf.Type.Input.Underlying, mf.Type.Output.Underlying)
	case TrainableAlgorithm:
		mf := mantikHeader.(*TrainableMantikHeader)
		return autoAdaptTrainableAlgorithm(e, mf)
	case ExecutableDataSet:
		mf := mantikHeader.(*DataSetMantikHeader)
		return autoAdaptDataSet(e, mf)
	case ExecutableTransformer:
		return autoadaptTransformer(e, mantikHeader)
	default:
		return nil, errors.New("Executable type not supported")
	}
}

/* Auto adapt an executable algorithm, looking for matching conversion functions.
   Can also adapt learnable algorithms. */
func AutoAdaptExecutableAlgorithm(algorithm ExecutableAlgorithm, newInput ds.DataType, newOutput ds.DataType) (ExecutableAlgorithm, error) {
	_, newInputTabularOk := newInput.(*ds.TabularData)
	_, algorithmInIsStruct := algorithm.Type().Input.Underlying.(*ds.Struct)
	_, algorithmOutIsStruct := algorithm.Type().Output.Underlying.(*ds.Struct)

	if newInputTabularOk && algorithmInIsStruct && algorithmOutIsStruct {
		// Build a special adapter which calls an algorithm multiple times
		premodified := buildSingleIntoMultipleAlgorithmAdapter(algorithm)
		return AutoAdaptExecutableAlgorithm(premodified, newInput, newOutput)
	}

	inputAdapter, err := adapt.LookupAutoAdapter(newInput, algorithm.Type().Input.Underlying)
	if err != nil {
		return nil, errors.Wrap(err, "Could not match input type")
	}
	outputAdapter, err := adapt.LookupAutoAdapter(algorithm.Type().Output.Underlying, newOutput)
	if err != nil {
		return nil, errors.Wrap(err, "Could not match output type")
	}
	var result ExecutableAlgorithm = &adaptedExecutableAlgorithm{
		algorithm,
		AlgorithmType{ds.Ref(newInput), ds.Ref(newOutput)},
		inputAdapter,
		outputAdapter,
	}
	return result, nil
}

// Wraps algorithms by putting in type converters before and after them
type adaptedExecutableAlgorithm struct {
	algorithm     ExecutableAlgorithm
	algorithmType AlgorithmType
	inputAdapter  adapt.Adapter
	outputAdapter adapt.Adapter
}

func (a *adaptedExecutableAlgorithm) Type() *AlgorithmType {
	return &a.algorithmType
}

func (a *adaptedExecutableAlgorithm) NativeType() *AlgorithmType {
	return a.algorithm.NativeType()
}

func (a *adaptedExecutableAlgorithm) Execute(rows []element.Element) ([]element.Element, error) {
	return adapt.ApplyChain(rows, a.inputAdapter, a.algorithm.Execute, a.outputAdapter)
}

func (a *adaptedExecutableAlgorithm) Cleanup() {
	a.algorithm.Cleanup()
}

func (a *adaptedExecutableAlgorithm) ExtensionInfo() interface{} {
	return a.algorithm.ExtensionInfo()
}

// Wraps algorithms, who expect 1 element count by calling them multiple times
type singleIntoMultipleAlgorithmAdapter struct {
	algorithmType AlgorithmType
	algorithm     ExecutableAlgorithm
}

func buildSingleIntoMultipleAlgorithmAdapter(inner ExecutableAlgorithm) ExecutableAlgorithm {
	inType := inner.Type().Input.Underlying.(*ds.Struct)
	outType := inner.Type().Output.Underlying.(*ds.Struct)
	updatedIn := ds.TabularData{inType.Fields}
	updatedOut := ds.TabularData{outType.Fields}
	updatedAlgorithmType := AlgorithmType{ds.Ref(&updatedIn), ds.Ref(&updatedOut)}
	return &singleIntoMultipleAlgorithmAdapter{
		updatedAlgorithmType,
		inner,
	}
}

func (s *singleIntoMultipleAlgorithmAdapter) Type() *AlgorithmType {
	return &s.algorithmType
}

func (s *singleIntoMultipleAlgorithmAdapter) NativeType() *AlgorithmType {
	return s.algorithm.NativeType()
}

func (s *singleIntoMultipleAlgorithmAdapter) Execute(rows []element.Element) ([]element.Element, error) {
	if len(rows) == 1 {
		return s.algorithm.Execute(rows)
	}
	var resultBuilder []element.Element = nil
	for _, row := range rows {
		rowAsTabularRow := row.(*element.TabularRow)
		rowAsRecord := &element.StructElement{rowAsTabularRow.Columns}
		asSingle := []element.Element{rowAsRecord}
		subResult, err := s.algorithm.Execute(asSingle)
		if err != nil {
			return nil, err
		}
		subResultUnpacked := subResult[0].(*element.StructElement)
		resultBuilder = append(resultBuilder, &element.TabularRow{subResultUnpacked.Elements})
	}
	return resultBuilder, nil
}

func (s *singleIntoMultipleAlgorithmAdapter) Cleanup() {
	s.algorithm.Cleanup()
}

func (s *singleIntoMultipleAlgorithmAdapter) ExtensionInfo() interface{} {
	return s.algorithm.ExtensionInfo()
}

func autoAdaptTrainableAlgorithm(algorithm TrainableAlgorithm, mantikHeader *TrainableMantikHeader) (TrainableAlgorithm, error) {
	trainingAdapter, err := adapt.LookupAutoAdapter(mantikHeader.TrainingType.Underlying, algorithm.TrainingType().Underlying)
	if err != nil {
		return nil, errors.Wrap(err, "Could not adapter training type")
	}
	statAdapter, err := adapt.LookupAutoAdapter(algorithm.StatType().Underlying, mantikHeader.StatType.Underlying)
	if err != nil {
		return nil, errors.Wrap(err, "Could not adapt stat type")
	}
	// For the embedded algorithm type, we just look if it matches.
	_, err = adapt.LookupAutoAdapter(mantikHeader.Type.Input.Underlying, algorithm.Type().Input.Underlying)
	if err != nil {
		return nil, errors.Wrap(err, "Could not adapt input type")
	}
	_, err = adapt.LookupAutoAdapter(algorithm.Type().Output.Underlying, mantikHeader.Type.Output.Underlying)
	if err != nil {
		return nil, errors.Wrap(err, "COuld not adapt output type")
	}
	return &adaptedTrainableAlgorithm{
		original:            algorithm,
		trainingAdapter:     trainingAdapter,
		adaptedTrainingType: *mantikHeader.TrainingType,
		statAdapter:         statAdapter,
		adaptedStatType:     *mantikHeader.StatType,
		adaptedType:         mantikHeader.Type,
	}, nil
}

type adaptedTrainableAlgorithm struct {
	original            TrainableAlgorithm
	trainingAdapter     adapt.Adapter
	adaptedTrainingType ds.TypeReference
	statAdapter         adapt.Adapter
	adaptedStatType     ds.TypeReference
	adaptedType         *AlgorithmType
}

func (a *adaptedTrainableAlgorithm) Cleanup() {
	a.original.Cleanup()
}

func (a *adaptedTrainableAlgorithm) ExtensionInfo() interface{} {
	return a.original.ExtensionInfo()
}

func (a *adaptedTrainableAlgorithm) TrainingType() ds.TypeReference {
	return a.adaptedTrainingType
}

func (a *adaptedTrainableAlgorithm) StatType() ds.TypeReference {
	return a.adaptedStatType
}

func (a *adaptedTrainableAlgorithm) Train(rows []element.Element) ([]element.Element, error) {
	return adapt.ApplyChain(rows, a.trainingAdapter, a.original.Train, a.statAdapter)
}

func (a *adaptedTrainableAlgorithm) Type() *AlgorithmType {
	return a.adaptedType
}

func (a *adaptedTrainableAlgorithm) LearnResultDirectory() (string, error) {
	return a.original.LearnResultDirectory()
}

func autoAdaptDataSet(set ExecutableDataSet, mantikHeader *DataSetMantikHeader) (ExecutableDataSet, error) {
	adapter, err := adapt.LookupAutoAdapter(set.Type().Underlying, mantikHeader.Type.Underlying)
	if err != nil {
		return nil, err
	}
	return &adaptedDataSet{
		set,
		mantikHeader.Type,
		adapter,
	}, nil
}

type adaptedDataSet struct {
	original ExecutableDataSet
	newType  ds.TypeReference
	adapter  adapt.Adapter
}

func (a *adaptedDataSet) Cleanup() {
	a.original.Cleanup()
}

func (a *adaptedDataSet) ExtensionInfo() interface{} {
	return a.original.ExtensionInfo()
}

func (a *adaptedDataSet) Type() ds.TypeReference {
	return a.newType
}

func (a *adaptedDataSet) Get() element.StreamReader {
	return &adaptedStreamReader{
		a.adapter,
		a.original.Get(),
	}
}

type adaptedStreamReader struct {
	adapter  adapt.Adapter
	original element.StreamReader
}

func (a *adaptedStreamReader) Read() (element.Element, error) {
	e, err := a.original.Read()
	if err != nil {
		return nil, err
	}
	adapted, err := a.adapter(e)
	return adapted, err
}

type adaptedStreamWriter struct {
	adapter  adapt.Adapter
	original element.StreamWriter
}

func (a *adaptedStreamWriter) Write(row element.Element) error {
	adapted, err := a.adapter(row)
	if err != nil {
		return err
	}
	return a.original.Write(adapted)
}

func (a *adaptedStreamWriter) Close() error {
	return a.original.Close()
}

func autoadaptTransformer(transformer ExecutableTransformer, header MantikHeader) (ExecutableTransformer, error) {
	expectedInputs, expectedOutputs, err := mantikHeaderInputOutputs(header)
	if err != nil {
		return nil, err
	}

	if dataTypesEqual(expectedInputs, transformer.Inputs()) && dataTypesEqual(expectedOutputs, transformer.Outputs()) {
		// Nothing to do
		return transformer, nil
	}

	if len(expectedInputs) != len(transformer.Inputs()) {
		return nil, errors.New("Cannot adapt, input slot count mismatch")
	}
	if len(expectedOutputs) != len(transformer.Outputs()) {
		return nil, errors.New("Cannot adapt, output slot count mismatch")
	}

	inputAdapters := make([]adapt.Adapter, len(transformer.Inputs()), len(transformer.Inputs()))
	outputAdapters := make([]adapt.Adapter, len(transformer.Outputs()), len(transformer.Outputs()))
	for i, inputType := range expectedInputs {
		adapter, err := adapt.LookupAutoAdapter(inputType.Underlying, transformer.Inputs()[i].Underlying)
		if err != nil {
			return nil, err
		}
		inputAdapters[i] = adapter
	}
	for i, outputType := range expectedOutputs {
		adapter, err := adapt.LookupAutoAdapter(transformer.Outputs()[i].Underlying, outputType.Underlying)
		if err != nil {
			return nil, err
		}
		outputAdapters[i] = adapter
	}
	return &adaptedTransformer{
		underlying:     transformer,
		inputs:         expectedInputs,
		outputs:        expectedOutputs,
		inputAdapters:  inputAdapters,
		outputAdapters: outputAdapters,
	}, nil
}

func mantikHeaderInputOutputs(header MantikHeader) ([]ds.TypeReference, []ds.TypeReference, error) {
	switch e := header.(type) {
	case *DataSetMantikHeader:
		return nil, []ds.TypeReference{e.Type}, nil
	case *AlgorithmMantikHeader:
		return []ds.TypeReference{e.Type.Input}, []ds.TypeReference{e.Type.Output}, nil
	case *CombinerMantikHeader:
		return e.Input, e.Output, nil
	default:
		return nil, nil, errors.New("Unsupported header type for a transformer")
	}
}

func dataTypesEqual(left []ds.TypeReference, right []ds.TypeReference) bool {
	if len(left) != len(right) {
		return false
	}
	for i, l := range left {
		if !ds.DataTypeEquality(l.Underlying, right[i].Underlying) {
			return false
		}
	}
	return true
}

type adaptedTransformer struct {
	underlying     ExecutableTransformer
	inputs         []ds.TypeReference
	outputs        []ds.TypeReference
	inputAdapters  []adapt.Adapter
	outputAdapters []adapt.Adapter
}

func (a adaptedTransformer) Cleanup() {
	a.underlying.Cleanup()
}

func (a adaptedTransformer) ExtensionInfo() interface{} {
	return a.underlying.ExtensionInfo()
}

func (a adaptedTransformer) Inputs() []ds.TypeReference {
	return a.inputs
}

func (a adaptedTransformer) Outputs() []ds.TypeReference {
	return a.outputs
}

func (a adaptedTransformer) Run(input []element.StreamReader, output []element.StreamWriter) error {
	adaptedInput := make([]element.StreamReader, len(input), len(input))
	for i, input := range input {
		adaptedInput[i] = &adaptedStreamReader{a.inputAdapters[i], input}
	}
	adaptedOutput := make([]element.StreamWriter, len(output), len(output))
	for i, output := range output {
		adaptedOutput[i] = &adaptedStreamWriter{a.outputAdapters[i], output}
	}
	return a.underlying.Run(adaptedInput, adaptedOutput)
}
