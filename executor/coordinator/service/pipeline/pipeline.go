package pipeline

import (
	"gl.ambrosys.de/mantik/go_shared/ds"
)

type Pipeline struct {
	Steps     []PiplineStep    `json:"steps"`
	InputType ds.TypeReference `json:"inputType"`
	Name      string           `json:"name"`
}

/** Returns the output type of the Pipeline. */
func (p *Pipeline) OutputType() ds.TypeReference {
	if len(p.Steps) == 0 {
		return p.InputType
	}
	return p.Steps[len(p.Steps)-1].OutputType
}

type PiplineStep struct {
	Url        string           `json:"url"`
	OutputType ds.TypeReference `json:"outputType"`
}
