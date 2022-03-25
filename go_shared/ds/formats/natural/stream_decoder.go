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
package natural

import (
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/mantik-ai/core/go_shared/ds/element"
	"github.com/mantik-ai/core/go_shared/ds/util/serializer"
	"github.com/pkg/errors"
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
			return nil, errors.Errorf("Unexpected type, got=%s, expected=%s", ds.ToJsonString(header.Format.Underlying), ds.ToJsonString(d.expectedType))
		}
		d.waitHeader = false
	}
	if d.waitTabular {
		err := d.backend.StartReadingTabularValues()
		if err != nil {
			return nil, errors.Wrap(err, "Expected tabular data")
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
