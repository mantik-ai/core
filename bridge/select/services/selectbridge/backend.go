package selectbridge

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/serving"
	"io"
	"path"
	"select/services/selectbridge/runner"
)

type SelectBackend struct {
}

func (s *SelectBackend) LoadModel(payloadDirectory *string, mantikHeader serving.MantikHeader) (serving.Executable, error) {
	return LoadModelFromMantikHeader(mantikHeader)
}

func LoadModel(directory string) (*SelectBackendModel, error) {
	mfPath := path.Join(directory, "MantikHeader")
	mf, err := serving.LoadMantikHeader(mfPath)
	if err != nil {
		return nil, errors.Wrap(err, "Could not read MantikHeader")
	}
	return LoadModelFromMantikHeader(mf)
}

func LoadModelFromMantikHeader(mantikHeader serving.MantikHeader) (*SelectBackendModel, error) {
	sm, err := ParseSelectMantikHeader(mantikHeader.Json())
	if err != nil {
		return nil, errors.Wrap(err, "Could not decode MantikHeader")
	}
	runner, err := runner.NewGeneratorRunner(sm.Program)
	if err != nil {
		return nil, err
	}
	return &SelectBackendModel{
		header: sm,
		runner: runner,
	}, nil
}

type SelectBackendModel struct {
	header *SelectMantikHeader
	runner runner.GeneratorRunner
}

func (s *SelectBackendModel) Cleanup() {
	// nothing to do
}

func (s *SelectBackendModel) ExtensionInfo() interface{} {
	// nothing to do
	return nil
}

func (s *SelectBackendModel) Inputs() []ds.TypeReference {
	return s.header.Input
}

func (s *SelectBackendModel) Outputs() []ds.TypeReference {
	return s.header.Output
}

func (s *SelectBackendModel) Run(input []element.StreamReader, output []element.StreamWriter) error {
	if len(output) != 1 {
		return errors.New("Only one output supported")
	}
	singleOutput := output[0]
	result := s.runner.Run(input)
	for {
		element, err := result.Read()
		if err == io.EOF {
			// Done
			return nil
		}
		if err != nil {
			return err
		}
		err = singleOutput.Write(element)
		if err != nil {
			return err
		}
	}
}

// Run a single n:1 Model
func (s *SelectBackendModel) Execute(inputs ...[]element.Element) ([]element.Element, error) {
	if len(s.header.Output) != 1 {
		return nil, errors.New("This execute method works only with n:1 models")
	}
	inputReaders := make([]element.StreamReader, len(inputs), len(inputs))
	for i, input := range inputs {
		inputReaders[i] = element.NewElementBuffer(input)
	}
	output := element.ElementBuffer{}
	err := s.Run(inputReaders, []element.StreamWriter{&output})
	if err != nil {
		return nil, err
	}
	return output.Elements(), nil
}
