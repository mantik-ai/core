/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package runner

import (
	"encoding/json"
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/pkg/errors"
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

type JoinProgram struct {
	Left      TableGeneratorProgramRef `json:"left"`
	Right     TableGeneratorProgramRef `json:"right"`
	GroupSize int                      `json:"groupSize"`
	JoinType  string                   `json:"joinType"`
	Filter    *Program                 `json:"filter"`
	Selector  []int                    `json:"selector"`
	Result    *ds.TabularData          `json:"result"`
}

func (j *JoinProgram) Type() string {
	return "join"
}

func (j *JoinProgram) TabularResult() *ds.TabularData {
	return j.Result
}

type discriminator struct {
	Type string `json:"type"`
}

type SplitProgram struct {
	Input       TableGeneratorProgramRef `json:"Input"`
	Fractions   []float64                `json:"fractions"`
	ShuffleSeed *int64                   `json:"shuffleSeed"`
}

func (s *SplitProgram) Type() string {
	return "split"
}

func (s *SplitProgram) TabularResult() *ds.TabularData {
	return s.Input.Underlying.TabularResult()
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
	case "join":
		var joinProgram JoinProgram
		err = json.Unmarshal(bytes, &joinProgram)
		p.Underlying = &joinProgram
	case "split":
		var splitProgram SplitProgram
		err = json.Unmarshal(bytes, &splitProgram)
		p.Underlying = &splitProgram
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
