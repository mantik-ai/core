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
	"github.com/stretchr/testify/assert"
	"io/ioutil"
	"testing"
)

func TestParseBinaryMantikHeader(t *testing.T) {
	// Note: the file makes no sense, but tests all elements
	simple := []byte(
		`
type:
  columns:
    x: int32
directory: data
files:
  - file: sample1
    compression: gzip
    skip: 10
    content:
      - element: x
      - stride: 1
      - skip: 5
`)
	parsed, err := ParseBinaryMantikHeader(simple)
	assert.NoError(t, err)
	assert.Equal(t, ds.FromJsonStringOrPanicRef(`{"columns": {"x": "int32"}}`), parsed.Type)
	assert.Equal(t, 1, len(parsed.Files))
	file1 := parsed.Files[0]
	assert.Equal(t, "sample1", file1.File)
	assert.Equal(t, "gzip", *file1.Compression)
	assert.Equal(t, 3, len(file1.Content))
	x := "x"
	assert.Equal(t, FileEntryContent{Element: &x}, file1.Content[0])
	one := 1
	assert.Equal(t, FileEntryContent{Stride: &one}, file1.Content[1])
	five := 5
	assert.Equal(t, FileEntryContent{Skip: &five}, file1.Content[2])
}

func TestParseMnist(t *testing.T) {
	content, err := ioutil.ReadFile("../../test/mnist/MantikHeader")
	assert.NoError(t, err)
	parsed, err := ParseBinaryMantikHeader(content)
	assert.Equal(t, 2, len(parsed.Files))
	assert.Equal(t, 2, len(parsed.Files[0].Content))
	assert.Equal(t, 2, len(parsed.Files[1].Content))
}
