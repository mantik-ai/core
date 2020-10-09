package runner

import (
	"encoding/json"
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
)

// A Program which can be executed by the select bridge
// Note: this is part of the API
type TableGeneratorProgram interface {
	Type() string
	TabularResult() *ds.TabularData
}

// Helper for parsing for referring to multiple TableGeneratorPrograms
type TableGeneratorProgramRef struct {
	Underlying TableGeneratorProgram
}

// Incoming data on a given slot
type DataSource struct {
	Port   int             `json:"port"`
	Result *ds.TabularData `json:"result"`
}

func (d *DataSource) Type() string {
	return "source"
}

func (d *DataSource) TabularResult() *ds.TabularData {
	return d.Result
}

// Combines multiple inputs using UNION
type UnionProgram struct {
	Inputs []TableGeneratorProgramRef `json:"inputs"`
	// If true, there won't be a check for duplicates
	All    bool            `json:"all"`
	Result *ds.TabularData `json:"result"`
	// Extension: if true, emit elements in inOrder (input0, input 1 etc.)
	InOrder bool `json:"inOrder"`
}

func (u *UnionProgram) Type() string {
	return "union"
}

func (d *UnionProgram) TabularResult() *ds.TabularData {
	return d.Result
}

type SelectProgram struct {
	// Optional input, if not given use data on port 0
	Input TableGeneratorProgramRef `json:"input"`
	// Optional selector (when false, all elements go through)
	Selector *Program `json:"selector"`
	// Optional projector (when f alse, all elements go through)
	Projector *Program        `json:"projector"`
	Result    *ds.TabularData `json:"result"`
}

func (s *SelectProgram) Type() string {
	return "select"
}

func (d *SelectProgram) TabularResult() *ds.TabularData {
	return d.Result
}

type discriminator struct {
	Type string `json:"type"`
}

func (p *TableGeneratorProgramRef) UnmarshalJSON(bytes []byte) error {
	d := discriminator{}
	err := json.Unmarshal(bytes, &d)
	if err != nil {
		return err
	}
	switch d.Type {
	case "union":
		var unionProgram UnionProgram
		err = json.Unmarshal(bytes, &unionProgram)
		p.Underlying = &unionProgram
	case "select":
		var selectProgram SelectProgram
		err = json.Unmarshal(bytes, &selectProgram)
		p.Underlying = &selectProgram
	case "source":
		var source DataSource
		err = json.Unmarshal(bytes, &source)
		p.Underlying = &source
	default:
		err = errors.Errorf("Unknown program type %s", d.Type)
	}
	if err == nil {
		if p.Underlying.TabularResult() == nil {
			return errors.New("Undefined result type")
		}
	}
	return err
}
