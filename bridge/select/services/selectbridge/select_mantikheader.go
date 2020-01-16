package selectbridge

import (
	"errors"
	"gl.ambrosys.de/mantik/go_shared/serving"
	"select/services/selectbridge/runner"
)

type SelectProgram struct {
	// Optional selector (when false, all elements go through)
	Selector *runner.Program `json:"selector"`
	// Optional projector (when f alse, all elements go through)
	Projector *runner.Program `json:"projector"`
}

type SelectMantikHeader struct {
	Type    serving.AlgorithmType `json:"type"`
	Program SelectProgram         `json:"selectProgram"`
}

func ParseSelectMantikHeader(data []byte) (*SelectMantikHeader, error) {
	var mf SelectMantikHeader
	err := serving.UnmarshallMetaYaml(data, &mf)
	if err != nil {
		return nil, err
	}
	if mf.Type.Input.Underlying == nil || mf.Type.Output.Underlying == nil {
		return nil, errors.New("Invalid type")
	}
	return &mf, err
}
