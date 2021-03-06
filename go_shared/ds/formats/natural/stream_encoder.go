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

/** An Encoder which can encode streams of data and is optimized for reuse. */
type StreamEncoder interface {
	StartEncoding(closer io.WriteCloser) element.StreamWriter
}

func NewStreamEncoder(contentType string, dataType ds.DataType) (StreamEncoder, error) {
	rootElementSerializer, err := LookupRootElementSerializer(dataType)
	if err != nil {
		return nil, err
	}
	var backendFactory func(writer io.Writer) (serializer.SerializingBackend, error)
	var hasHeader bool
	switch contentType {
	case MimeJson:
		backendFactory = func(writer io.Writer) (backend serializer.SerializingBackend, err error) {
			return serializer.CreateSerializingBackend(serializer.BACKEND_JSON, writer)
		}
		hasHeader = false
	case MimeMsgPack:
		backendFactory = func(writer io.Writer) (backend serializer.SerializingBackend, err error) {
			return serializer.CreateSerializingBackend(serializer.BACKEND_MSGPACK, writer)
		}
		hasHeader = false
	case MimeMantikBundleJson:
		backendFactory = func(writer io.Writer) (backend serializer.SerializingBackend, err error) {
			return serializer.CreateSerializingBackend(serializer.BACKEND_JSON, writer)
		}
		hasHeader = true
	case MimeMantikBundle:
		backendFactory = func(writer io.Writer) (backend serializer.SerializingBackend, err error) {
			return serializer.CreateSerializingBackend(serializer.BACKEND_MSGPACK, writer)
		}
		hasHeader = true
	default:
		return nil, errors.Errorf("Unsupported content type %s", contentType)
	}

	_, isTabular := dataType.(*ds.TabularData)
	return &streamEncoder{
		expectedType:   dataType,
		serializer:     rootElementSerializer,
		backendFactory: backendFactory,
		hasHeader:      hasHeader,
		isTabular:      isTabular,
	}, nil
}

type streamEncoder struct {
	expectedType   ds.DataType
	serializer     ElementSerializer
	backendFactory func(writer io.Writer) (serializer.SerializingBackend, error)
	hasHeader      bool
	isTabular      bool
}

type streamWriter struct {
	serializer         ElementSerializer
	backend            serializer.SerializingBackend
	dataType           ds.DataType
	writeHeader        bool
	writeTabularPrefix bool
	closer             io.Closer
}

func (d *streamWriter) Write(e element.Element) error {
	err := d.startIfNecessary()
	if err != nil {
		return err
	}
	err = d.backend.NextRow()
	if err != nil {
		return err
	}
	return d.serializer.Write(d.backend, e)
}

func (d *streamWriter) startIfNecessary() error {
	if d.writeHeader {
		header := serializer.Header{
			ds.Ref(d.dataType),
		}
		err := d.backend.EncodeHeader(&header)
		if err != nil {
			return err
		}
		d.writeHeader = false
	}
	if d.writeTabularPrefix {
		err := d.backend.StartTabularValues()
		if err != nil {
			return err
		}
		d.writeTabularPrefix = false
	}
	return nil
}

func (d *streamWriter) Close() error {
	err := d.startIfNecessary()
	if err != nil {
		return err
	}
	err = d.backend.Finish()
	if err != nil {
		return err
	}
	return d.closer.Close()
}

type failedStreamWriter struct {
	err error
}

func (f failedStreamWriter) Write(element.Element) error {
	return f.err
}

func (f failedStreamWriter) Close() error {
	return f.err
}

func (s *streamEncoder) StartEncoding(writer io.WriteCloser) element.StreamWriter {
	backend, err := s.backendFactory(writer)
	if err != nil {
		writer.Close()
		return failedStreamWriter{err}
	}
	return &streamWriter{
		serializer:         s.serializer,
		backend:            backend,
		dataType:           s.expectedType,
		writeHeader:        s.hasHeader,
		writeTabularPrefix: s.isTabular,
		closer:             writer,
	}
}
