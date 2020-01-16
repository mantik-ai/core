package serving

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/adapt"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
)

func AutoAdapt(executable Executable, mantikHeader MantikHeader) (Executable, error) {
	algorithm, ok := executable.(ExecutableAlgorithm)
	if ok {
		mf := mantikHeader.(*AlgorithmMantikHeader)
		return AutoAdaptExecutableAlgorithm(algorithm, mf.Type.Input.Underlying, mf.Type.Output.Underlying)
	}
	trainable, ok := executable.(TrainableAlgorithm)
	if ok {
		mf := mantikHeader.(*TrainableMantikHeader)
		return autoAdaptTrainableAlgorithm(trainable, mf)
	}
	dataset, ok := executable.(ExecutableDataSet)
	if ok {
		mf := mantikHeader.(*DataSetMantikHeader)
		return autoAdaptDataSet(dataset, mf)
	}
	panic("Implement me")
}

/* Auto adapt an executable algorithm, looking for matching conversion functions.
   Can also adapt learnable algorithms. */
func AutoAdaptExecutableAlgorithm(algorithm ExecutableAlgorithm, newInput ds.DataType, newOutput ds.DataType) (ExecutableAlgorithm, error) {
	newInputTabular, newInputTabularOk := newInput.(*ds.TabularData)
	algorithmFixedCount := algorithm.Type().FixedElementCount()

	if newInputTabularOk && algorithmFixedCount != nil && *algorithmFixedCount == 1 && newInputTabular.RowCount == nil {
		// Otherwise adaption will fail immediately
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
	updatedAlgorithmType := *inner.Type().Input.Underlying.(*ds.TabularData)
	updatedAlgorithmType.RowCount = nil
	return &singleIntoMultipleAlgorithmAdapter{
		AlgorithmType{ds.Ref(&updatedAlgorithmType), inner.Type().Output},
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
		asSingle := []element.Element{row}
		subResult, err := s.algorithm.Execute(asSingle)
		if err != nil {
			return nil, err
		}
		resultBuilder = append(resultBuilder, subResult...)
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
