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
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestOpenFileReader(t *testing.T) {
	// Test dat contains 16 bytes 0..15
	t.Run("Open with offset 0", func(t *testing.T) {
		entry := FileEntry{
			File: "test_file.dat",
		}
		file, err := OpenFileReader("../../test", &entry)
		assert.NoError(t, err)
		defer file.Close()
		buf := make([]byte, 2)
		_, err = file.Read(buf)
		assert.NoError(t, err)
		assert.Equal(t, []byte{00, 01}, buf)
	})
	t.Run("Open with offset", func(t *testing.T) {
		entry := FileEntry{
			File: "test_file.dat",
			Skip: 2,
		}
		file, err := OpenFileReader("../../test", &entry)
		assert.NoError(t, err)
		defer file.Close()
		buf := make([]byte, 2)
		_, err = file.Read(buf)
		assert.NoError(t, err)
		assert.Equal(t, []byte{02, 03}, buf)
	})
	t.Run("Open with gzip enabled", func(t *testing.T) {
		gzip := "gzip"
		entry := FileEntry{
			File:        "test_file.dat.gz",
			Skip:        2,
			Compression: &gzip,
		}
		file, err := OpenFileReader("../../test", &entry)
		assert.NoError(t, err)
		defer file.Close()
		buf := make([]byte, 2)
		_, err = file.Read(buf)
		assert.NoError(t, err)
		assert.Equal(t, []byte{02, 03}, buf)
	})
}
