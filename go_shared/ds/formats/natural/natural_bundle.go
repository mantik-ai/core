package natural

import (
	"bytes"
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/util/serializer"
	"io"
)

func EncodeBundle(bundle *element.Bundle, backendType serializer.BackendType) ([]byte, error) {
	var b bytes.Buffer
	err := EncodeBundleToWriter(bundle, backendType, &b)
	if err != nil {
		return nil, err
	}
	return b.Bytes(), nil
}

func EncodeBundleToWriter(bundle *element.Bundle, backendType serializer.BackendType, writer io.Writer) error {
	var backend, err = serializer.CreateSerializingBackend(backendType, writer)
	if err != nil {
		return err
	}
	encoder, err := CreateEncoder(bundle.Type, backend)
	if err != nil {
		return err
	}
	for _, r := range bundle.Rows {
		err := encoder.Write(r)
		if err != nil {
			return err
		}
	}
	encoder.Close()
	return nil
}

func DecodeBundle(backendType serializer.BackendType, data []byte) (*element.Bundle, error) {
	reader := bytes.NewBuffer(data)
	return DecodeBundleFromReader(backendType, reader)
}

func DecodeBundleFromReader(backendType serializer.BackendType, reader io.Reader) (*element.Bundle, error) {
	var usedType ds.DataType = nil

	typeTaker := func(dataType ds.DataType) error {
		usedType = dataType
		return nil
	}

	var incomingRows = []*element.TabularRow{} // we want empty slices for deep comparison
	var backend, err = serializer.CreateDeserializingBackend(backendType, reader)
	if err != nil {
		return nil, err
	}
	decoder, err := CreateDecoder(typeTaker, backend)
	if err != nil {
		return nil, err
	}
	for {
		row, err := decoder.Read()
		if err == io.EOF {
			if usedType == nil {
				return nil, errors.New("Stream end without type")
			}
			result := element.Bundle{usedType, incomingRows}
			return &result, nil
		}
		if err != nil {
			return nil, err
		}
		tableRow, ok := row.(*element.TabularRow)
		if !ok {
			return nil, errors.Errorf("Expected TableRow, got type %d", row.Kind())
		}
		incomingRows = append(incomingRows, tableRow)

	}
}
