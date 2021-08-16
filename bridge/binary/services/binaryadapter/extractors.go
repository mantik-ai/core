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
	"binary/services/binaryadapter/binreader"
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"io"
)

// Currently we only support Big Endian
var BigEndianEnabled = true

type ElementExtractor interface {
	ReadRow() ([]element.Element, error)
	Close()
}

// Combines multiple file extractors into one
func CombineFileExtractors(columnCount int, extractors []ElementExtractor) ElementExtractor {
	return &combinedExtractor{columnCount, extractors}
}

type combinedExtractor struct {
	columnCount   int
	subExtractors []ElementExtractor
}

func (c *combinedExtractor) ReadRow() ([]element.Element, error) {
	row := make([]element.Element, c.columnCount)
	for _, e := range c.subExtractors {
		addRow, err := e.ReadRow()
		if err != nil {
			return nil, err
		}
		for i, v := range addRow {
			if v != nil {
				row[i] = v
			}
		}
	}
	return row, nil
}

func (c *combinedExtractor) Close() {
	for _, e := range c.subExtractors {
		e.Close()
	}
}

// Create a single file extractor
func CreateSingleFileExtractor(reader io.ReadCloser, fullDataType *ds.TabularData, entry *FileEntry) (ElementExtractor, error) {
	stride, err := getStride(fullDataType, entry)
	if err != nil {
		return nil, err
	}
	subExtractors, err := buildSubExtractors(fullDataType, entry)
	if err != nil {
		return nil, err
	}
	buf := make([]byte, stride)
	return &singleFileExtractor{
		buf,
		reader,
		fullDataType.Columns.Arity(),
		subExtractors,
	}, nil
}

type singleFileExtractor struct {
	buf           []byte
	reader        io.ReadCloser
	columnCount   int
	subExtractors []offsetExtractor
}

func (s *singleFileExtractor) ReadRow() ([]element.Element, error) {
	_, err := io.ReadFull(s.reader, s.buf)
	if err != nil {
		return nil, err
	}
	result := make([]element.Element, s.columnCount)
	for _, e := range s.subExtractors {
		upper := e.offset + e.byteLen
		subBytes := s.buf[e.offset:upper]
		result[e.columnIndex] = e.binReader(subBytes)
	}
	return result, nil
}

func (s *singleFileExtractor) Close() {
	s.reader.Close()
}

type offsetExtractor struct {
	offset      int
	byteLen     int
	columnIndex int
	binReader   binreader.BinaryReader
}

// Returns the stride for this file. Makes sure that all types are valid.
func getStride(fullDataType *ds.TabularData, entry *FileEntry) (int, error) {
	var stride = 0    // read stride
	var strideSum = 0 // sum of all elements
	for _, c := range entry.Content {
		if c.Stride != nil {
			if stride > 0 {
				return 0, errors.New("Multiple stride definitions")
			}
			if *c.Stride <= 0 {
				return 0, errors.Errorf("Invalid stride %d", *c.Stride)
			}
			stride = *c.Stride
		}
		if c.Skip != nil {
			if *c.Skip <= 0 {
				return 0, errors.Errorf("invalid skip %d", *c.Skip)
			}
			strideSum += *c.Skip
		}
		if c.Element != nil {
			dataType := fullDataType.Columns.Get(*c.Element)
			if dataType == nil {
				return 0, errors.Errorf("Unresolved column %s", *c.Element)
			}
			len, _, err := binreader.LookupReader(dataType, BigEndianEnabled)
			if err != nil {
				return 0, err
			}
			strideSum += len
		}
	}
	if stride != 0 && stride < strideSum {
		return 0, errors.Errorf("Stride %d is smaller than calculated stride %d", stride, strideSum)
	}
	if strideSum == 0 {
		return 0, errors.New("Empty definition")
	}
	if stride != 0 {
		return stride, nil
	} else {
		return strideSum, nil
	}
}

func buildSubExtractors(fullDataType *ds.TabularData, entry *FileEntry) ([]offsetExtractor, error) {
	offset := 0
	var result []offsetExtractor
	for _, c := range entry.Content {
		if c.Element != nil {
			idx := fullDataType.Columns.IndexOf(*c.Element)
			if idx < 0 {
				return nil, errors.Errorf("Unresolved column %s", *c.Element)
			}
			dataType := fullDataType.Columns.Values[idx].SubType.Underlying
			byteLen, reader, err := binreader.LookupReader(dataType, BigEndianEnabled)
			if err != nil {
				return nil, err
			}

			result = append(result, offsetExtractor{
				offset:      offset,
				byteLen:     byteLen,
				columnIndex: idx,
				binReader:   reader,
			})
			offset += byteLen
		}
		if c.Skip != nil {
			if *c.Skip <= 0 {
				return nil, errors.Errorf("invalid skip %d", *c.Skip)
			}
			offset += *c.Skip
		}
	}
	return result, nil
}
