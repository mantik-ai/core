package binaryadapter

import (
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds"
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
