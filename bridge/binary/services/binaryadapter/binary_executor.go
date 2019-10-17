package binaryadapter

import (
	"encoding/json"
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/serving"
	"path"
)

type BinaryExecutor struct {
	mf            *BinaryMantikfile
	dataDirectory string
}

func CreateBinaryExecutorFromDir(rootDir string) (*BinaryExecutor, error) {
	payloadDir := path.Join(rootDir, "payload")
	mantikfile, err := serving.LoadMantikfile(path.Join(rootDir, "Mantikfile"))
	if err != nil {
		return nil, err
	}
	return CreateBinaryExecutor(&payloadDir, mantikfile)
}

func CreateBinaryExecutor(payloadDir *string, mantikfile serving.Mantikfile) (*BinaryExecutor, error) {
	if payloadDir == nil {
		return nil, errors.New("Expected payload")
	}
	var mf BinaryMantikfile
	err := json.Unmarshal(mantikfile.Json(), &mf)
	if err != nil {
		return nil, errors.Wrap(err, "Could not parse Mantikfile")
	}
	// Creating multi reader for validating correctness
	multiReader, err := CreateMultiFileAdapter(*payloadDir, &mf)
	if err != nil {
		return nil, errors.Wrap(err, "Could not create reader")
	}
	multiReader.Close()
	// We can't store the multi reader, as it is possible to
	// to read the data set multiple times.
	return &BinaryExecutor{
		&mf, *payloadDir,
	}, nil
}

func (b *BinaryExecutor) Cleanup() {
	// nothing to do
}

func (b *BinaryExecutor) ExtensionInfo() interface{} {
	return nil
}

func (b *BinaryExecutor) Type() ds.TypeReference {
	return b.mf.Type
}

func (b *BinaryExecutor) Get() element.StreamReader {
	multiReader, err := CreateMultiFileAdapter(b.dataDirectory, b.mf)
	if err != nil {
		return &failedStreamReader{err}
	}
	return &wrappedStreamReader{multiReader}
}

// Wraps Element Extractor into a Stream Reader
type wrappedStreamReader struct {
	ElementExtractor
}

func (w *wrappedStreamReader) Read() (element.Element, error) {
	columns, err := w.ReadRow()
	if err != nil {
		w.Close()
		return nil, err
	}
	return &element.TabularRow{columns}, nil
}

// A Stream reader which always fails.
type failedStreamReader struct {
	err error
}

func (f *failedStreamReader) Read() (element.Element, error) {
	return nil, f.err
}
