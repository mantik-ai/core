package serving

import (
	"encoding/json"
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"io/ioutil"
	"os"
)

type Mantikfile interface {
	Kind() string
	Name() *string
	MetaVariables() MetaVariables
	// Returns the decoded JSON (after applying meta variables)
	Json() []byte
}

type mantikFileHeader struct {
	Kind *string `json:"kind"`
}

type DataSetMantikfile struct {
	ParsedName          *string          `json:"name"`
	Type                ds.TypeReference `json:"type"`
	ParsedMetaVariables MetaVariables    `json:"metaVariables"`
	json                []byte
}

func (d *DataSetMantikfile) Kind() string {
	return "dataset"
}

func (d *DataSetMantikfile) Name() *string {
	return d.ParsedName
}

func (d *DataSetMantikfile) MetaVariables() MetaVariables {
	return d.ParsedMetaVariables
}

func (d *DataSetMantikfile) Json() []byte {
	return d.json
}

/* The mantik file needed for serving algorithms. */
type AlgorithmMantikfile struct {
	/* Name of the algorithm */
	ParsedName *string `json:"name"`
	/* Type, For Algorithms and Trainables. */
	Type                *AlgorithmType `json:"type"`
	ParsedMetaVariables []MetaVariable `json:"metaVariables"`
	json                []byte
}

func (a *AlgorithmMantikfile) Kind() string {
	return "algorithm"
}

func (a *AlgorithmMantikfile) Name() *string {
	return a.ParsedName
}

func (d *AlgorithmMantikfile) MetaVariables() MetaVariables {
	return d.ParsedMetaVariables
}

func (d *AlgorithmMantikfile) Json() []byte {
	return d.json
}

type TrainableMantikfile struct {
	/* Name of the algorithm */
	ParsedName *string `json:"name"`
	/* Type, For Algorithms and Trainables. */
	Type *AlgorithmType `json:"type"`

	/* Training Type (for trainable). */
	TrainingType *ds.TypeReference `json:"trainingType"`
	/* Statistic Type (for trainable). */
	StatType            *ds.TypeReference `json:"statType"`
	ParsedMetaVariables []MetaVariable    `json:"metaVariables"`
	json                []byte
}

func (t *TrainableMantikfile) Kind() string {
	return "trainable"
}

func (t *TrainableMantikfile) Name() *string {
	return t.ParsedName
}

func (d *TrainableMantikfile) MetaVariables() MetaVariables {
	return d.ParsedMetaVariables
}

func (d *TrainableMantikfile) Json() []byte {
	return d.json
}

func ParseMantikFile(bytes []byte) (Mantikfile, error) {
	var file mantikFileHeader
	plainJson, err := DecodeMetaYaml(bytes)
	if err != nil {
		return nil, err
	}
	// no meta, kind should not utilize meta variables...
	err = json.Unmarshal(plainJson, &file)
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
		err := json.Unmarshal(plainJson, &df)
		df.json = plainJson
		return &df, err
	case "algorithm":
		var af AlgorithmMantikfile
		err := json.Unmarshal(plainJson, &af)
		af.json = plainJson
		return &af, err
	case "trainable":
		var tf TrainableMantikfile
		err := json.Unmarshal(plainJson, &tf)
		tf.json = plainJson
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
