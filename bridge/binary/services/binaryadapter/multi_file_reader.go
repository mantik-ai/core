package binaryadapter

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"io"
)

// Combines the single file adapters into a full one

func CreateMultiFileAdapter(directory string, mantikfile *BinaryMantikfile) (ElementExtractor, error) {
	tabularData, ok := mantikfile.Type.Underlying.(*ds.TabularData)
	if !ok {
		return nil, errors.New("Only tabular data supported")
	}
	err := checkColumns(tabularData, mantikfile)
	if err != nil {
		return nil, err
	}
	if len(mantikfile.Files) == 0 {
		// can this happen?
		return nil, errors.New("No files given")
	}
	var fileReaders []io.ReadCloser
	var elementExtractors []ElementExtractor
	isError := true
	defer func() {
		if isError {
			for _, f := range fileReaders {
				f.Close()
			}
		}
	}()
	for _, fileEntry := range mantikfile.Files {
		reader, err := OpenFileReader(directory, &fileEntry)
		if err != nil {
			return nil, err
		}
		fileReaders = append(fileReaders, reader)
		extractor, err := CreateSingleFileExtractor(reader, tabularData, &fileEntry)
		if err != nil {
			return nil, err
		}
		elementExtractors = append(elementExtractors, extractor)
	}
	isError = false
	return CombineFileExtractors(len(tabularData.Columns), elementExtractors), nil
}

// Check that all columns are in use
func checkColumns(tabularData *ds.TabularData, mantikfile *BinaryMantikfile) error {
	usedColumn := make([]bool, len(tabularData.Columns))
	for _, file := range mantikfile.Files {
		for _, entry := range file.Content {
			if entry.Element != nil {
				columnName := *entry.Element
				index := tabularData.IndexOfColumn(columnName)
				if index < 0 {
					return errors.Errorf("Unresolved column %s", columnName)
				}
				if usedColumn[index] {
					return errors.Errorf("Double column %s", columnName)
				}
				usedColumn[index] = true
			}
		}
	}
	for idx, b := range usedColumn {
		if !b {
			columnName := tabularData.Columns[idx].Name
			return errors.Errorf("Column not given in file %s", columnName)
		}
	}
	return nil
}
