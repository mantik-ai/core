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
package serving

import (
	"encoding/json"
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/pkg/errors"
	"io/ioutil"
	"os"
)

type MantikHeader interface {
	Kind() string
	Meta() *MantikHeaderMeta
	Name() *string
	MetaVariables() MetaVariables
	// Returns the decoded JSON (after applying meta variables)
	Json() []byte
}

const AlgorithmKind = "algorithm"
const DatasetKind = "dataset"
const TrainableAlgorithmKind = "trainable"
const CombinerKind = "combiner"

// The name of the MantikHeader (File)
const MantikHeaderName = "MantikHeader"

// The name of the optional payload file/dir inside a Mantik Bundle
const PayloadPathElement = "payload"

type MantikHeaderMeta struct {
	Kind                *string       `json:"kind"`
	Name                *string       `json:"name"`
	Version             *string       `json:"version"`
	Account             *string       `json:"account"`
	ParsedMetaVariables MetaVariables `json:"metaVariables"`
}

// Returns a MantikId if there is one encoded in the header
func (h *MantikHeaderMeta) NamedMantikId() *string {
	if h.Name == nil {
		return nil
	} else {
		res := FormatNamedMantikId(*h.Name, h.Account, h.Version)
		return &res
	}
}

type DataSetMantikHeader struct {
	Type   ds.TypeReference `json:"type"`
	json   []byte
	header *MantikHeaderMeta
}

func (d *DataSetMantikHeader) Meta() *MantikHeaderMeta {
	return d.header
}

func (d *DataSetMantikHeader) Kind() string {
	return DatasetKind
}

func (d *DataSetMantikHeader) Name() *string {
	return d.header.Name
}

func (d *DataSetMantikHeader) MetaVariables() MetaVariables {
	return d.header.ParsedMetaVariables
}

func (d *DataSetMantikHeader) Json() []byte {
	return d.json
}

/* The mantik header needed for serving algorithms. */
type AlgorithmMantikHeader struct {
	header *MantikHeaderMeta
	/* Type, For Algorithms and Trainables. */
	Type *AlgorithmType `json:"type"`
	json []byte
}

func (a *AlgorithmMantikHeader) Meta() *MantikHeaderMeta {
	return a.header
}

func (a *AlgorithmMantikHeader) Kind() string {
	return AlgorithmKind
}

func (a *AlgorithmMantikHeader) Name() *string {
	return a.header.Name
}

func (d *AlgorithmMantikHeader) MetaVariables() MetaVariables {
	return d.header.ParsedMetaVariables
}

func (d *AlgorithmMantikHeader) Json() []byte {
	return d.json
}

type TrainableMantikHeader struct {
	/* Type, For Algorithms and Trainables. */
	Type *AlgorithmType `json:"type"`

	/* Training Type (for trainable). */
	TrainingType *ds.TypeReference `json:"trainingType"`
	/* Statistic Type (for trainable). */
	StatType *ds.TypeReference `json:"statType"`
	json     []byte
	header   *MantikHeaderMeta
}

func (t *TrainableMantikHeader) Meta() *MantikHeaderMeta {
	return t.header
}

func (t *TrainableMantikHeader) Kind() string {
	return TrainableAlgorithmKind
}

func (t *TrainableMantikHeader) Name() *string {
	return t.header.Name
}

func (d *TrainableMantikHeader) MetaVariables() MetaVariables {
	return d.header.ParsedMetaVariables
}

func (d *TrainableMantikHeader) Json() []byte {
	return d.json
}

type CombinerMantikHeader struct {
	Input  []ds.TypeReference `json:"input"`
	Output []ds.TypeReference `json:"output"`
	json   []byte
	header *MantikHeaderMeta
}

func (c *CombinerMantikHeader) Kind() string {
	return CombinerKind
}

func (c *CombinerMantikHeader) Meta() *MantikHeaderMeta {
	return c.header
}

func (c *CombinerMantikHeader) Name() *string {
	return c.header.Name
}

func (c *CombinerMantikHeader) MetaVariables() MetaVariables {
	return c.header.ParsedMetaVariables
}

func (c *CombinerMantikHeader) Json() []byte {
	return c.json
}

func ParseMantikHeader(bytes []byte) (MantikHeader, error) {
	var header MantikHeaderMeta
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
		var df DataSetMantikHeader
		err := json.Unmarshal(plainJson, &df)
		df.json = plainJson
		df.header = &header
		return &df, err
	case AlgorithmKind:
		var af AlgorithmMantikHeader
		err := json.Unmarshal(plainJson, &af)
		af.json = plainJson
		af.header = &header
		return &af, err
	case TrainableAlgorithmKind:
		var tf TrainableMantikHeader
		err := json.Unmarshal(plainJson, &tf)
		tf.json = plainJson
		tf.header = &header
		return &tf, err
	case CombinerKind:
		var cf CombinerMantikHeader
		err := json.Unmarshal(plainJson, &cf)
		cf.json = plainJson
		cf.header = &header
		return &cf, err
	}
	return nil, errors.Errorf("Unsupported kind %s", *header.Kind)
}

func LoadMantikHeader(filePath string) (MantikHeader, error) {
	f, err := os.Open(filePath)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	content, err := ioutil.ReadAll(f)
	if err != nil {
		return nil, err
	}
	return ParseMantikHeader(content)
}
