package binaryadapter

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"io/ioutil"
	"path"
)

type BinaryExecutor struct {
	mf            *BinaryMantikfile
	dataDirectory string
}

func CreateBinaryExecutor(dir string) (*BinaryExecutor, error) {
	mfPath := path.Join(dir, "Mantikfile")
	mfContent, err := ioutil.ReadFile(mfPath)
	if err != nil {
		return nil, errors.Wrap(err, "Could not read Mantikfile")
	}
	mf, err := ParseBinaryMantikFile(mfContent)
	if err != nil {
		return nil, errors.Wrap(err, "Could not parse Mantikfile")
	}
	dataDirectory := path.Join(dir, mf.Directory)
	// Creating multi reader for validating correctness
	multiReader, err := CreateMultiFileAdapter(dataDirectory, mf)
	if err != nil {
		return nil, errors.Wrap(err, "Could not create reader")
	}
	multiReader.Close()
	// We can't store the multi reader, as it is possible to
	// to read the data set multiple times.
	return &BinaryExecutor{
		mf, dataDirectory,
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
