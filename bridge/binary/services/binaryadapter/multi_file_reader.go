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
package binaryadapter

import (
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/pkg/errors"
	"io"
)

// Combines the single file adapters into a full one

func CreateMultiFileAdapter(directory string, mantikHeader *BinaryMantikHeader) (ElementExtractor, error) {
	tabularData, ok := mantikHeader.Type.Underlying.(*ds.TabularData)
	if !ok {
		return nil, errors.New("Only tabular data supported")
	}
	err := checkColumns(tabularData, mantikHeader)
	if err != nil {
		return nil, err
	}
	if len(mantikHeader.Files) == 0 {
		// can this happen?
		return nil, errors.New("No files given")
	}
	var fileReaders []io.ReadCloser
	var elementExtractors []ElementExtractor
	isError := true
	defer func() {
		if isError {
			for _, f := range fileReaders {
				f.Close()
			}
		}
	}()
	for _, fileEntry := range mantikHeader.Files {
		reader, err := OpenFileReader(directory, &fileEntry)
		if err != nil {
			return nil, err
		}
		fileReaders = append(fileReaders, reader)
		extractor, err := CreateSingleFileExtractor(reader, tabularData, &fileEntry)
		if err != nil {
			return nil, err
		}
		elementExtractors = append(elementExtractors, extractor)
	}
	isError = false
	return CombineFileExtractors(tabularData.Columns.Arity(), elementExtractors), nil
}

// Check that all columns are in use
func checkColumns(tabularData *ds.TabularData, mantikHeader *BinaryMantikHeader) error {
	usedColumn := make([]bool, tabularData.Columns.Arity())
	for _, file := range mantikHeader.Files {
		for _, entry := range file.Content {
			if entry.Element != nil {
				columnName := *entry.Element
				index := tabularData.Columns.IndexOf(columnName)
				if index < 0 {
					return errors.Errorf("Unresolved column %s", columnName)
				}
				if usedColumn[index] {
					return errors.Errorf("Double column %s", columnName)
				}
				usedColumn[index] = true
			}
		}
	}
	for idx, b := range usedColumn {
		if !b {
			columnName := tabularData.Columns.ByIdx(idx).Name
			return errors.Errorf("Column not given in file %s", columnName)
		}
	}
	return nil
}
