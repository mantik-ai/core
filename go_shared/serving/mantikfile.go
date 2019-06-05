package serving

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/util/yaml"
	"io/ioutil"
	"os"
)

type Mantikfile interface {
	Kind() string
	Name() *string
	Directory() *string
	MetaVariables() MetaVariables
}

type mantikFileHeader struct {
	Kind *string `json:"kind"`
}

type DataSetMantikfile struct {
	ParsedName          *string          `json:"name"`
	Type                ds.TypeReference `json:"type"`
	ParsedDirectory     *string          `json:"directory"`
	ParsedMetaVariables MetaVariables    `json:"metaVariables"`
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

func (d *DataSetMantikfile) MetaVariables() MetaVariables {
	return d.ParsedMetaVariables
}

/* The mantik file needed for serving algorithms. */
type AlgorithmMantikfile struct {
	/* Name of the algorithm */
	ParsedName *string `json:"name"`
	/* Type, For Algorithms and Trainables. */
	Type *AlgorithmType `json:"type"`
	/* The directory where the algorithm besides. */
	ParsedDirectory     *string        `json:"directory"`
	ParsedMetaVariables []MetaVariable `json:"metaVariables"`
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

func (d *AlgorithmMantikfile) MetaVariables() MetaVariables {
	return d.ParsedMetaVariables
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
	ParsedDirectory     *string        `json:"directory"`
	ParsedMetaVariables []MetaVariable `json:"metaVariables"`
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

func (d *TrainableMantikfile) MetaVariables() MetaVariables {
	return d.ParsedMetaVariables
}

func ParseMantikFile(bytes []byte) (Mantikfile, error) {
	var file mantikFileHeader
	// no meta, kind should not utilize meta variables...
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
		err := UnmarshallMetaYaml(bytes, &df)
		return &df, err
	case "algorithm":
		var af AlgorithmMantikfile
		err := UnmarshallMetaYaml(bytes, &af)
		return &af, err
	case "trainable":
		var tf TrainableMantikfile
		err := UnmarshallMetaYaml(bytes, &tf)
		return &tf, err
	}
	return nil, errors.Errorf("Unsupported kind %s", kind)
}

func LoadMantikfile(filePath string) (Mantikfile, error) {
	f, err := os.Open(filePath)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	content, err := ioutil.ReadAll(f)
	if err != nil {
		return nil, err
	}
	return ParseMantikFile(content)
}
