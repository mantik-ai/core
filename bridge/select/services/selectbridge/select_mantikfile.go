package selectbridge

import (
	"errors"
	"gl.ambrosys.de/mantik/go_shared/serving"
	"gl.ambrosys.de/mantik/go_shared/util/yaml"
	"select/services/selectbridge/runner"
)

type SelectProgram struct {
	// Optional selector (when false, all elements go through)
	Selector *runner.Program `json:"selector"`
	// Optional projector (when f alse, all elements go through)
	Projector *runner.Program `json:"projector"`
}

type SelectMantikfile struct {
	Type    serving.AlgorithmType `json:"type"`
	Program SelectProgram         `json:"selectProgram"`
}

func ParseSelectMantikfile(data []byte) (*SelectMantikfile, error) {
	var mf SelectMantikfile
	err := yaml.Unmarshal(data, &mf)
	if err != nil {
		return nil, err
	}
	if mf.Type.Input.Underlying == nil || mf.Type.Output.Underlying == nil {
		return nil, errors.New("Invalid type")
	}
	return &mf, err
}
