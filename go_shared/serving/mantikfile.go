package serving

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/util/yaml"
)

type Mantikfile interface {
	Kind() string
	Name() *string
	Directory() *string
}

type mantikFileHeader struct {
	Kind *string `json:"kind"`
}

type DataSetMantikfile struct {
	ParsedName      *string          `json:"name"`
	Type            ds.TypeReference `json:"type"`
	ParsedDirectory *string          `json:"directory"`
}

func (d *DataSetMantikfile) Kind() string {
	return "dataset"
}

func (d *DataSetMantikfile) Name() *string {
	return d.ParsedName
}

func (d *DataSetMantikfile) Directory() *string {
	return d.ParsedDirectory
}

/* The mantik file needed for serving algorithms. */
type AlgorithmMantikfile struct {
	/* Name of the algorithm */
	ParsedName *string `json:"name"`
	/* Type, For Algorithms and Trainables. */
	Type *AlgorithmType `json:"type"`
	/* The directory where the algorithm besides. */
	ParsedDirectory *string `json:"directory"`
}

func (a *AlgorithmMantikfile) Kind() string {
	return "algorithm"
}

func (a *AlgorithmMantikfile) Name() *string {
	return a.ParsedName
}

func (a *AlgorithmMantikfile) Directory() *string {
	return a.ParsedDirectory
}

type TrainableMantikfile struct {
	/* Name of the algorithm */
	ParsedName *string `json:"name"`
	/* Type, For Algorithms and Trainables. */
	Type *AlgorithmType `json:"type"`

	/* Training Type (for trainable). */
	TrainingType *ds.TypeReference `json:"trainingType"`
	/* Statistic Type (for trainable). */
	StatType *ds.TypeReference `json:"statType"`
	/* The directory where the algorithm besides. */
	ParsedDirectory *string `json:"directory"`
}

func (t *TrainableMantikfile) Kind() string {
	return "trainable"
}

func (t *TrainableMantikfile) Name() *string {
	return t.ParsedName
}

func (t *TrainableMantikfile) Directory() *string {
	return t.ParsedDirectory
}

func ParseMantikFile(bytes []byte) (Mantikfile, error) {
	var file mantikFileHeader
	err := yaml.Unmarshal(bytes, &file)
	if err != nil {
		return nil, err
	}

	var kind string
	if file.Kind == nil {
		kind = "algorithm" // default
	} else {
		kind = *file.Kind
	}

	switch kind {
	case "dataset":
		var df DataSetMantikfile
		err := yaml.Unmarshal(bytes, &df)
		return &df, err
	case "algorithm":
		var af AlgorithmMantikfile
		err := yaml.Unmarshal(bytes, &af)
		return &af, err
	case "trainable":
		var tf TrainableMantikfile
		err := yaml.Unmarshal(bytes, &tf)
		return &tf, err
	}
	return nil, errors.Errorf("Unsupported kind %s", kind)
}
