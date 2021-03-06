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
	"bytes"
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/mantik-ai/core/go_shared/ds/element"
	"github.com/stretchr/testify/assert"
	"io"
	"testing"
)

type closeableBuffer struct {
	*bytes.Buffer
	closed bool
}

func (c *closeableBuffer) Close() error {
	c.closed = true
	return nil
}

func MakeCloseableBuffer(content []byte) *closeableBuffer {
	return &closeableBuffer{
		bytes.NewBuffer(content),
		false,
	}
}

func TestCreateSingleFileExtractor(t *testing.T) {
	file := []byte{00, 01, 02, 03}
	tabularData := ds.FromJsonStringOrPanic(
		`{
	"columns": {
		"x": "uint8",
		"y": "uint8"
	}
}
`).(*ds.TabularData)

	// Note: x and y flipped!
	entry := ParseFileEntryFromJsonOrPanic(`{"content": [{"element":"y"},{"element":"x"}]}`)
	buf := MakeCloseableBuffer(file)
	extractor, err := CreateSingleFileExtractor(buf, tabularData, entry)
	assert.NoError(t, err)
	row, err := extractor.ReadRow()
	assert.NoError(t, err)
	assert.Equal(t, 2, len(row))
	assert.Equal(t, uint8(1), row[0].(element.Primitive).X)
	assert.Equal(t, uint8(0), row[1].(element.Primitive).X)
	row, err = extractor.ReadRow()
	assert.NoError(t, err)
	assert.Equal(t, 2, len(row))
	assert.Equal(t, uint8(3), row[0].(element.Primitive).X)
	assert.Equal(t, uint8(2), row[1].(element.Primitive).X)
	_, err = extractor.ReadRow()
	assert.Equal(t, io.EOF, err)

	assert.False(t, buf.closed)
	extractor.Close()
	assert.True(t, buf.closed)
}

func TestCreateSingleFileExtractor_Stride(t *testing.T) {
	file := []byte{00, 01, 02, 03}
	tabularData := ds.FromJsonStringOrPanic(
		`{
	"columns": {
		"x": "uint8"
	}
}
`).(*ds.TabularData)

	// Note: x and y flipped!
	entry := ParseFileEntryFromJsonOrPanic(`{"content": [{"element":"x"},{"stride":2}]}`)
	buf := MakeCloseableBuffer(file)
	extractor, err := CreateSingleFileExtractor(buf, tabularData, entry)
	assert.NoError(t, err)

	row, err := extractor.ReadRow()
	assert.NoError(t, err)
	assert.Equal(t, 1, len(row))
	assert.Equal(t, uint8(0), row[0].(element.Primitive).X)

	row, err = extractor.ReadRow()
	assert.NoError(t, err)
	assert.Equal(t, 1, len(row))
	assert.Equal(t, uint8(2), row[0].(element.Primitive).X)

	row, err = extractor.ReadRow()
	assert.Equal(t, io.EOF, err)

	assert.False(t, buf.closed)
	extractor.Close()
	assert.True(t, buf.closed)
}

func TestCombineFileExtractors(t *testing.T) {
	file1 := []byte{00, 01, 02, 03}
	file2 := []byte{03, 02, 01, 00}

	tabularData := ds.FromJsonStringOrPanic(
		`{
	"columns": {
		"a": "int8",
		"b": "int8",
		"c": "int8",
		"d": "int8"
	}
}
`).(*ds.TabularData)

	// Note: x and y flipped!
	entry1 := ParseFileEntryFromJsonOrPanic(`{"content": [{"element":"a"},{"element":"b"}]}`)
	entry2 := ParseFileEntryFromJsonOrPanic(`{"content": [{"element":"c"},{"element":"d"}]}`)

	buf1 := MakeCloseableBuffer(file1)
	buf2 := MakeCloseableBuffer(file2)

	extractor1, err := CreateSingleFileExtractor(buf1, tabularData, entry1)
	assert.NoError(t, err)
	extractor2, err := CreateSingleFileExtractor(buf2, tabularData, entry2)
	assert.NoError(t, err)

	combined := CombineFileExtractors(4, []ElementExtractor{extractor1, extractor2})

	row, err := combined.ReadRow()
	assert.NoError(t, err)
	assert.Equal(t, 4, len(row))
	assert.Equal(t, int8(0), row[0].(element.Primitive).X)
	assert.Equal(t, int8(1), row[1].(element.Primitive).X)
	assert.Equal(t, int8(3), row[2].(element.Primitive).X)
	assert.Equal(t, int8(2), row[3].(element.Primitive).X)

	row, err = combined.ReadRow()
	assert.NoError(t, err)
	assert.Equal(t, 4, len(row))
	assert.Equal(t, int8(2), row[0].(element.Primitive).X)
	assert.Equal(t, int8(3), row[1].(element.Primitive).X)
	assert.Equal(t, int8(1), row[2].(element.Primitive).X)
	assert.Equal(t, int8(0), row[3].(element.Primitive).X)

	_, err = combined.ReadRow()
	assert.Equal(t, io.EOF, err)

	assert.False(t, buf1.closed)
	assert.False(t, buf2.closed)
	combined.Close()
	assert.True(t, buf1.closed)
	assert.True(t, buf2.closed)
}
