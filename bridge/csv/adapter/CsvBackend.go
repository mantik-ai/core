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
package adapter

import (
	"encoding/json"
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/mantik-ai/core/go_shared/serving"
	"github.com/mantik-ai/core/go_shared/util/osext"
	"github.com/pkg/errors"
	"path"
)

type CsvBackend struct {
}

func LoadModelFromDirectory(directory string) (*CsvDataSet, error) {
	mfPath := path.Join(directory, "MantikHeader")
	mf, err := serving.LoadMantikHeader(mfPath)
	if err != nil {
		return nil, err
	}
	var payload *string
	payloadFile := path.Join(directory, "payload")
	if osext.FileExists(payloadFile) {
		payload = &payloadFile
	} else {
		payload = nil
	}
	return LoadModel(payload, mf)
}

func (c *CsvBackend) LoadModel(payload *string, mantikHeader serving.MantikHeader) (serving.Executable, error) {
	return LoadModel(payload, mantikHeader)
}

func LoadModel(payloadFile *string, mantikHeader serving.MantikHeader) (*CsvDataSet, error) {
	var header CsvMantikHeader
	err := json.Unmarshal(mantikHeader.Json(), &header)
	if err != nil {
		return nil, errors.Wrap(err, "Could not parse Header")
	}

	if header.Url == nil && payloadFile == nil {
		return nil, errors.Wrap(err, "No URL and no payload file given")
	}
	if header.Url != nil && payloadFile != nil {
		return nil, errors.Wrap(err, "URL and Payload file given")
	}

	tabular, isTabular := header.Type.Underlying.(*ds.TabularData)
	if !isTabular {
		return nil, errors.New("Expected Tabular data output")
	}

	adapter, err := NewRowAdapter(tabular)
	if err != nil {
		return nil, errors.Wrap(err, "Could not wrap datatype")
	}

	return &CsvDataSet{header, payloadFile, adapter}, nil
}
