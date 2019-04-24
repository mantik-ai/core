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
