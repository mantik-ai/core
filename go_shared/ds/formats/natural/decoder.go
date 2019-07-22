package natural

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/util/serializer"
)

type DataTypeHandler func(dataType ds.DataType) error

/*
Create a Decoder, which calls the data handler if the type is specified.
*/
func CreateDecoder(dataTypeHandler DataTypeHandler, backend serializer.DeserializingBackend) (element.StreamReader, error) {
	header, err := backend.DecodeHeader()
	if err != nil {
		return nil, errors.Wrap(err, "Error decoding header")
	}
	err = dataTypeHandler(header.Format.Underlying)
	if err != nil {
		return nil, err
	}

	deserializer, err := LookupRootElementDeserializer(header.Format.Underlying)
	if err != nil {
		return nil, err
	}
	_, isTabular := header.Format.Underlying.(*ds.TabularData)
	if isTabular {
		err = backend.StartReadingTabularValues()
		if err != nil {
			return nil, err
		}
	}
	return decoder{
		backend, deserializer,
	}, nil
}

type decoder struct {
	backend      serializer.DeserializingBackend
	deserializer ElementDeserializer
}

func (d decoder) Read() (element.Element, error) {
	return d.deserializer.Read(d.backend)
}

func CreateDecoderForType(expectedDataType ds.DataType, backend serializer.DeserializingBackend) (element.StreamReader, error) {
	checker := func(dataType ds.DataType) error {
		if dataType != expectedDataType {
			return errors.New("Type doesn't match expected type")
		}
		return nil
	}
	return CreateDecoder(checker, backend)
}

func CreateHeaderFreeDecoder(expectedDataType ds.DataType, backend serializer.DeserializingBackend) (element.StreamReader, error) {
	deserializer, err := LookupRootElementDeserializer(expectedDataType)
	if err != nil {
		return nil, err
	}
	_, isTabular := expectedDataType.(*ds.TabularData)
	if isTabular {
		err = backend.StartReadingTabularValues()
		if err != nil {
			return nil, err
		}
	}
	return decoder{
		backend, deserializer,
	}, nil
}
