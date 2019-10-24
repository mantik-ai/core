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
	Header() * MantikFileHeader
	Name() *string
	MetaVariables() MetaVariables
	// Returns the decoded JSON (after applying meta variables)
	Json() []byte
}

const AlgorithmKind = "algorithm"
const DatasetKind = "dataset"
const TrainableAlgorithmKind = "trainable"

// The name of the Mantikfile
const MantikfileName = "Mantikfile"
// The name of the optional payload file/dir inside a Mantik Bundle
const PayloadPathElement = "payload"


type MantikFileHeader struct {
	Kind *string `json:"kind"`
	Name *string `json:"name"`
	Version *string `json:"version"`
	Account *string `json:"account"`
	ParsedMetaVariables MetaVariables    `json:"metaVariables"`
}

// Returns a MantikId if there is one encoded in the header
func (h * MantikFileHeader) NamedMantikId() * string {
	if h.Name == nil {
		return nil
	} else {
		res := FormatNamedMantikId(*h.Name, h.Account, h.Version)
		return &res
	}
}

type DataSetMantikfile struct {
	Type                ds.TypeReference `json:"type"`
	json                []byte
	header * MantikFileHeader
}

func (d* DataSetMantikfile) Header() * MantikFileHeader {
	return d.header
}

func (d *DataSetMantikfile) Kind() string {
	return "dataset"
}

func (d *DataSetMantikfile) Name() *string {
	return d.header.Name
}

func (d *DataSetMantikfile) MetaVariables() MetaVariables {
	return d.header.ParsedMetaVariables
}

func (d *DataSetMantikfile) Json() []byte {
	return d.json
}

/* The mantik file needed for serving algorithms. */
type AlgorithmMantikfile struct {
	header * MantikFileHeader
	/* Type, For Algorithms and Trainables. */
	Type                *AlgorithmType `json:"type"`
	json                []byte
}

func (a* AlgorithmMantikfile) Header() * MantikFileHeader {
	return a.header
}

func (a *AlgorithmMantikfile) Kind() string {
	return "algorithm"
}

func (a *AlgorithmMantikfile) Name() *string {
	return a.header.Name
}

func (d *AlgorithmMantikfile) MetaVariables() MetaVariables {
	return d.header.ParsedMetaVariables
}

func (d *AlgorithmMantikfile) Json() []byte {
	return d.json
}

type TrainableMantikfile struct {
	/* Type, For Algorithms and Trainables. */
	Type *AlgorithmType `json:"type"`

	/* Training Type (for trainable). */
	TrainingType *ds.TypeReference `json:"trainingType"`
	/* Statistic Type (for trainable). */
	StatType            *ds.TypeReference `json:"statType"`
	json                []byte
	header * MantikFileHeader
}

func (t* TrainableMantikfile) Header() * MantikFileHeader {
	return t.header
}

func (t *TrainableMantikfile) Kind() string {
	return "trainable"
}

func (t *TrainableMantikfile) Name() *string {
	return t.header.Name
}

func (d *TrainableMantikfile) MetaVariables() MetaVariables {
	return d.header.ParsedMetaVariables
}

func (d *TrainableMantikfile) Json() []byte {
	return d.json
}

func ParseMantikFile(bytes []byte) (Mantikfile, error) {
	var header MantikFileHeader
	plainJson, err := DecodeMetaYaml(bytes)
	if err != nil {
		return nil, err
	}
	err = json.Unmarshal(plainJson, &header)
	if err != nil {
		return nil, err
	}

	if header.Kind == nil {
		a := AlgorithmKind
		header.Kind = &a
	}

	switch *header.Kind {
	case DatasetKind:
		var df DataSetMantikfile
		err := json.Unmarshal(plainJson, &df)
		df.json = plainJson
		df.header = &header
		return &df, err
	case AlgorithmKind:
		var af AlgorithmMantikfile
		err := json.Unmarshal(plainJson, &af)
		af.json = plainJson
		af.header = &header
		return &af, err
	case TrainableAlgorithmKind:
		var tf TrainableMantikfile
		err := json.Unmarshal(plainJson, &tf)
		tf.json = plainJson
		tf.header = &header
		return &tf, err
	}
	return nil, errors.Errorf("Unsupported kind %s", *header.Kind)
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
