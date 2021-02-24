package selectbridge

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/serving"
	"select/services/selectbridge/runner"
)

type SelectMantikHeader struct {
	serving.CombinerMantikHeader
	Program runner.TableGeneratorProgramRef `json:"program"`
	// Human readable statement
	Query *string
}

func ParseSelectMantikHeader(data []byte) (*SelectMantikHeader, error) {
	var mf SelectMantikHeader
	err := serving.UnmarshallMetaYaml(data, &mf)
	if err != nil {
		return nil, err
	}
	if (len(mf.Input) == 0) || (len(mf.Output) < 1) {
		return nil, errors.New("Invalid type")
	}
	if mf.Program.Underlying == nil {
		return nil, errors.New("No program found")
	}
	return &mf, err
}
