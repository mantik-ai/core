package mnp_pipeline

import (
	"gl.ambrosys.de/mantik/go_shared/ds"
)

type MnpPipeline struct {
	Steps     []MnpPipelineStep `json:"steps"`
	InputType ds.TypeReference  `json:"inputType"`
	Name      string            `json:"name"`
}

/** Returns the output type of the Pipeline. */
func (p *MnpPipeline) OutputType() ds.TypeReference {
	if len(p.Steps) == 0 {
		return p.InputType
	}
	return p.Steps[len(p.Steps)-1].OutputType
}

type MnpPipelineStep struct {
	Url        string           `json:"url"`
	OutputType ds.TypeReference `json:"outputType"`
}
