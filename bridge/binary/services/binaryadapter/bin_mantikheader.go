package binaryadapter

import (
	"encoding/json"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/serving"
)

type FileEntry struct {
	// Which file to open
	File string `json:"file"`
	// How the file is compressed, valid empty or gzip
	Compression *string `json:"compression"`
	// How many bytes are skipped at the beginning
	Skip    int                `json:"skip"`
	Content []FileEntryContent `json:"content"`
}

// For testing purposes, parse a FileEntry from JSON
func ParseFileEntryFromJsonOrPanic(jsonString string) *FileEntry {
	var f FileEntry
	err := json.Unmarshal([]byte(jsonString), &f)
	if err != nil {
		panic(err.Error())
	}
	return &f
}

type FileEntryContent struct {
	// Column which is resolved by this file
	Element *string `json:"element"`
	// Stride how long a row is in this file
	Stride *int `json:"stride"`
	// Skip some bytes
	Skip *int `json:"skip"`
}

type BinaryMantikHeader struct {
	Type  ds.TypeReference `json:"type"`
	Files []FileEntry      `json:"files"`
}

func ParseBinaryMantikHeader(bytes []byte) (*BinaryMantikHeader, error) {
	var file BinaryMantikHeader
	err := serving.UnmarshallMetaYaml(bytes, &file)
	return &file, err
}
