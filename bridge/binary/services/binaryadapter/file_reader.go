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
	"compress/gzip"
	"github.com/pkg/errors"
	"io"
	"log"
	"os"
	"path"
)

// Opens a single binary file
func OpenFileReader(dataDirectory string, entry *FileEntry) (io.ReadCloser, error) {
	fullFile := path.Join(dataDirectory, entry.File)
	var result io.ReadCloser
	result, err := os.Open(fullFile)
	if err != nil {
		return nil, err
	}
	log.Printf("Opened %s\n", fullFile)
	if entry.Compression != nil {
		switch *entry.Compression {
		case "gzip":
			r, err := gzip.NewReader(result)
			if err != nil {
				result.Close()
				return nil, err
			}
			result = &gzipReader{r, result}
		default:
			result.Close()
			return nil, errors.Errorf("Unsupported compression %s", *entry.Compression)
		}
	}

	err = skip(entry.Skip, result)
	if err != nil {
		result.Close()
		return nil, err
	}

	return result, err
}

func skip(bytes int, r io.ReadCloser) error {
	if bytes < 0 {
		return errors.Errorf("Invalid skip value %d", bytes)
	}
	if bytes > 1000000 {
		// Avoid crash
		return errors.Errorf("Really high skip value %d", bytes)
	}
	buf := make([]byte, bytes)
	_, err := r.Read(buf)
	return err
}

// Enhance gzip.Reader to have a Close method which also closes the underlying file
type gzipReader struct {
	*gzip.Reader
	underlying io.ReadCloser
}

func (g *gzipReader) Close() error {
	defer g.underlying.Close()
	return g.Reader.Close()
}
