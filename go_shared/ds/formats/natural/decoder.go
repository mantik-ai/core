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
