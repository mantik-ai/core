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
	"encoding/csv"
	"github.com/mantik-ai/core/go_shared/ds/element"
	"github.com/pkg/errors"
	"io"
)

type CsvStreamReader struct {
	rowAdapter *RowAdapter
	reader     *csv.Reader
	underlying io.ReadCloser
}

func NewCsvStreamReader(
	rowAdapter *RowAdapter,
	options *CsvOptions,
	input io.ReadCloser,
) (*CsvStreamReader, error) {

	reader := csv.NewReader(input)
	reader.Comma = runeFromString(options.Comma, ',')
	reader.Comment = runeFromString(options.Comment, 0)
	reader.LazyQuotes = true
	reader.TrimLeadingSpace = true
	if options.SkipHeader != nil && *options.SkipHeader {
		_, err := reader.Read()
		if err != nil {
			return nil, errors.Wrap(err, "Could not read first line")
		}
	}
	return &CsvStreamReader{rowAdapter, reader, input}, nil
}

func runeFromString(s *string, defaultValue rune) rune {
	if s != nil && len(*s) > 0 {
		return ([]rune(*s))[0]
	} else {
		return defaultValue
	}
}

func (c *CsvStreamReader) Read() (element.Element, error) {
	row, err := c.reader.Read()
	if err != nil {
		c.underlying.Close()
		if err != io.EOF {
			return nil, errors.Wrap(err, "Unexpected error during CSV Reading")
		}
		return nil, err
	}
	decoded, err := c.rowAdapter.Adapter(row)
	if err != nil {
		return nil, errors.Wrap(err, "Error on decoding row")
	}
	return decoded, nil
}
