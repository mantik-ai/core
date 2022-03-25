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
	"encoding/json"
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/mantik-ai/core/go_shared/serving"
)

type FileEntry struct {
	// Which file to open
	File string `json:"file"`
	// How the file is compressed, valid empty or gzip
	Compression *string `json:"compression"`
	// How many bytes are skipped at the beginning
	Skip    int                `json:"skip"`
	Content []FileEntryContent `json:"content"`
}

// For testing purposes, parse a FileEntry from JSON
func ParseFileEntryFromJsonOrPanic(jsonString string) *FileEntry {
	var f FileEntry
	err := json.Unmarshal([]byte(jsonString), &f)
	if err != nil {
		panic(err.Error())
	}
	return &f
}

type FileEntryContent struct {
	// Column which is resolved by this file
	Element *string `json:"element"`
	// Stride how long a row is in this file
	Stride *int `json:"stride"`
	// Skip some bytes
	Skip *int `json:"skip"`
}

type BinaryMantikHeader struct {
	Type  ds.TypeReference `json:"type"`
	Files []FileEntry      `json:"files"`
}

func ParseBinaryMantikHeader(bytes []byte) (*BinaryMantikHeader, error) {
	var file BinaryMantikHeader
	err := serving.UnmarshallMetaYaml(bytes, &file)
	return &file, err
}
