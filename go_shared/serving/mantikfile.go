package serving

import (
	"github.com/ghodss/yaml"
	"gl.ambrosys.de/mantik/go_shared/ds"
)

/* The mantik file needed for serving algorithms. */
type Mantikfile struct {
	/* Name of the algorithm */
	Name *string `json:"name"`
	/* Kind (default is algorithm). */
	Kind *string `json:"kind"`
	/* Expected Type. */
	Type *AlgorithmType `json:"type"`
	/* Training Type (for trainable). */
	TrainingType *ds.TypeReference `json:"trainingType"`
	/* Statistic Type (for trainable). */
	StatType *ds.TypeReference `json:"statType"`
	/* The directory where the algorithm besides. */
	Directory *string `json:"directory"`
}

func (m *Mantikfile) KindToUse() string {
	if m.Kind == nil {
		return "algorithm"
	} else {
		return *m.Kind
	}
}

func ParseMantikFile(bytes []byte) (*Mantikfile, error) {
	var file Mantikfile
	err := yaml.Unmarshal(bytes, &file)
	if err != nil {
		return nil, err
	}
	return &file, nil
}
