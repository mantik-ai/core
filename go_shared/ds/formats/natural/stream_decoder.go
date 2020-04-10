package natural

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/util/serializer"
	"io"
)

/* A Decoder which can decode streams of data and is optimized for reuse. */
type StreamDecoder interface {
	StartDecoding(io.Reader) element.StreamReader
}

func NewStreamDecoder(contentType string, dataType ds.DataType) (StreamDecoder, error) {
	rootElementDeserializer, err := LookupRootElementDeserializer(dataType)
	if err != nil {
		return nil, err
	}
	var backendFactory func(reader io.Reader) (serializer.DeserializingBackend, error)
	var hasHeader bool
	switch contentType {
	case MimeJson:
		backendFactory = func(reader io.Reader) (backend serializer.DeserializingBackend, err error) {
			return serializer.CreateDeserializingBackend(serializer.BACKEND_JSON, reader)
		}
		hasHeader = false
	case MimeMsgPack:
		backendFactory = func(reader io.Reader) (backend serializer.DeserializingBackend, err error) {
			return serializer.CreateDeserializingBackend(serializer.BACKEND_MSGPACK, reader)
		}
		hasHeader = false
	case MimeMantikBundleJson:
		backendFactory = func(reader io.Reader) (backend serializer.DeserializingBackend, err error) {
			return serializer.CreateDeserializingBackend(serializer.BACKEND_JSON, reader)
		}
		hasHeader = true
	case MimeMantikBundle:
		backendFactory = func(reader io.Reader) (backend serializer.DeserializingBackend, err error) {
			return serializer.CreateDeserializingBackend(serializer.BACKEND_MSGPACK, reader)
		}
		hasHeader = true
	default:
		return nil, errors.Errorf("Unsupported content type %s", contentType)
	}

	_, isTabular := dataType.(*ds.TabularData)
	return &streamDecoder{
		expectedType:   dataType,
		deserializer:   rootElementDeserializer,
		backendFactory: backendFactory,
		hasHeader:      hasHeader,
		isTabular:      isTabular,
	}, nil
}

type streamDecoder struct {
	expectedType   ds.DataType
	deserializer   ElementDeserializer
	backendFactory func(reader io.Reader) (serializer.DeserializingBackend, error)
	hasHeader      bool
	isTabular      bool
}

type streamReader struct {
	deserializer ElementDeserializer
	backend      serializer.DeserializingBackend
	expectedType ds.DataType
	waitHeader   bool
	waitTabular  bool
}

func (d *streamReader) Read() (element.Element, error) {
	if d.waitHeader {
		header, err := d.backend.DecodeHeader()
		if err != nil {
			return nil, err
		}
		if !ds.DataTypeEquality(header.Format.Underlying, d.expectedType) {
			return nil, errors.New("Unexpected type")
		}
		d.waitHeader = false
	}
	if d.waitTabular {
		err := d.backend.StartReadingTabularValues()
		if err != nil {
			return nil, errors.New("Expected tabular data")
		}
		d.waitTabular = false
	}
	return d.deserializer.Read(d.backend)
}

type failedStreamReader struct {
	err error
}

func (f failedStreamReader) Read() (element.Element, error) {
	return nil, f.err
}

func (s *streamDecoder) StartDecoding(reader io.Reader) element.StreamReader {
	backend, err := s.backendFactory(reader)
	if err != nil {
		return failedStreamReader{err}
	}
	return &streamReader{
		deserializer: s.deserializer,
		backend:      backend,
		expectedType: s.expectedType,
		waitHeader:   s.hasHeader,
		waitTabular:  s.isTabular,
	}
}
