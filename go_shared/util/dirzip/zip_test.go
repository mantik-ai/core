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
package dirzip

import (
	"archive/zip"
	"github.com/stretchr/testify/assert"
	"io/ioutil"
	"os"
	"path/filepath"
	"testing"
)

func TestZipDirectory(t *testing.T) {
	testDir := "ziptestdir"
	zipFile, err := ZipDirectory(testDir, true)
	assert.NoError(t, err)
	assert.FileExists(t, zipFile)

	reader, err := zip.OpenReader(zipFile)
	defer reader.Close()
	assert.NoError(t, err)

	listing := make([]string, 0)
	for _, f := range reader.File {
		listing = append(listing, f.Name)
	}
	assert.Contains(t, listing, "file0.txt")
	assert.Contains(t, listing, "subdir1/file1.txt")
	assert.Contains(t, listing, "subdir1/file2.txt")
	assert.Contains(t, listing, "subdir1/subsubdir1/file4.txt")
	assert.Contains(t, listing, "subdir2/file3.txt")
	assert.NotContains(t, listing, "subdir2/.hiddenfile") // hidden file
	assert.NotContains(t, listing, "subdir1/.hiddendir/subhiddenfile.txt")
}

func TestUnzipDiectory(t *testing.T) {
	testDir := "ziptestdir"
	zipFile, err := ZipDirectory(testDir, true)
	assert.NoError(t, err)

	tempDir, err := ioutil.TempDir("", "mantik_cli_test_unzip_directory")
	assert.NoError(t, err)
	assert.DirExists(t, tempDir)

	err = UnzipDiectory(zipFile, tempDir, true)
	assert.NoError(t, err)
	assert.FileExists(t, filepath.Join(tempDir, "file0.txt"))
	assert.FileExists(t, filepath.Join(tempDir, "subdir1/subsubdir1/file4.txt"))
	os.RemoveAll(tempDir)
}
