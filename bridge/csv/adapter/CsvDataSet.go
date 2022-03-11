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
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"io"
	"net/http"
	"os"
)

type CsvDataSet struct {
	header  CsvMantikHeader
	file    *string
	adapter *RowAdapter
}

func (c *CsvDataSet) Cleanup() {
	// Nothing
}

func (c *CsvDataSet) ExtensionInfo() interface{} {
	return nil
}

func (c *CsvDataSet) Type() ds.TypeReference {
	return c.header.Type
}

func (c *CsvDataSet) Get() element.StreamReader {
	is, err := c.openInputStream()
	if err != nil {
		return element.NewFailedReader(err)
	}
	reader, err := NewCsvStreamReader(c.adapter, &c.header.Options, is)
	if err != nil {
		return element.NewFailedReader(err)
	}
	return reader
}

func (c *CsvDataSet) openInputStream() (io.ReadCloser, error) {
	if c.header.Url != nil {
		client := http.Client{}
		response, err := client.Get(*c.header.Url)
		if err != nil {
			return nil, errors.Wrapf(err, "Could not get URL %s", *c.header.Url)
		}

		if response.StatusCode != 200 {
			return nil, errors.Errorf("Unexpected Status code %d on loading URL %s", response.StatusCode, *c.header.Url)
		}

		return response.Body, nil
	}
	if c.file != nil {
		return os.Open(*c.file)
	}
	return nil, errors.New("Neither URL nor file given")
}
