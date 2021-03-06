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
	"bytes"
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/mantik-ai/core/go_shared/ds/element"
	"github.com/mantik-ai/core/go_shared/ds/util/serializer"
	"github.com/pkg/errors"
	"io"
)

/*
Wraps a Bundle so that it can be encoded to JSON.
This cannot be directly added to bundle, to avoid circular dependencies
*/
type BundleRef struct {
	Bundle element.Bundle
}

func (b BundleRef) MarshalJSON() ([]byte, error) {
	return EncodeBundle(&b.Bundle, serializer.BACKEND_JSON)
}

func (b *BundleRef) UnmarshalJSON(data []byte) error {
	bundle, err := DecodeBundle(serializer.BACKEND_JSON, data)
	if err != nil {
		return err
	}
	b.Bundle = *bundle
	return err
}

func EncodeBundle(bundle *element.Bundle, backendType serializer.BackendType) ([]byte, error) {
	var b bytes.Buffer
	err := EncodeBundleToWriter(bundle, backendType, &b)
	if err != nil {
		return nil, err
	}
	return b.Bytes(), nil
}

// Encode a Bundle value (without Meta)
func EncodeBundleValue(bundle *element.Bundle, backendType serializer.BackendType) ([]byte, error) {
	var b bytes.Buffer
	var backend, err = serializer.CreateSerializingBackend(backendType, &b)
	if err != nil {
		return nil, err
	}
	rootSerializer, err := LookupRootElementSerializer(bundle.Type)
	if err != nil {
		return nil, err
	}
	if bundle.IsTabular() {
		err = backend.StartTabularValues()
		if err != nil {
			return nil, err
		}
	}
	for _, r := range bundle.Rows {
		err = backend.NextRow()
		if err != nil {
			return nil, err
		}
		err = rootSerializer.Write(backend, r)
		if err != nil {
			return nil, err
		}
	}
	err = backend.Finish()
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
	backend, err := serializer.CreateDeserializingBackendForBytes(backendType, data)
	if err != nil {
		return nil, err
	}
	return DecodeBundleFromDeserializingBackend(backend)
}

func DecodeBundleFromReader(backendType serializer.BackendType, reader io.Reader) (*element.Bundle, error) {
	var backend, err = serializer.CreateDeserializingBackend(backendType, reader)
	if err != nil {
		return nil, err
	}
	return DecodeBundleFromDeserializingBackend(backend)
}

// Decode a single value (without header)
func DecodeBundleValue(dataType ds.DataType, backendType serializer.BackendType, reader io.Reader) (*element.Bundle, error) {
	backend, err := serializer.CreateDeserializingBackend(backendType, reader)
	if err != nil {
		return nil, err
	}
	return DecodeBundleValueFromDeserializingBackend(backend, dataType)
}

func DecodeBundleFromDeserializingBackend(backend serializer.DeserializingBackend) (*element.Bundle, error) {
	var usedType ds.DataType = nil
	typeTaker := func(dataType ds.DataType) error {
		usedType = dataType
		return nil
	}
	decoder, err := CreateDecoder(typeTaker, backend)
	if err != nil {
		return nil, err
	}
	var incomingRows = []element.Element{} // we want empty slices for deep comparison
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
		incomingRows = append(incomingRows, row)
	}
}

func DecodeBundleValueFromDeserializingBackend(backend serializer.DeserializingBackend, dataType ds.DataType) (*element.Bundle, error) {
	decoder, err := CreateHeaderFreeDecoder(dataType, backend)
	if err != nil {
		return nil, err
	}
	var incomingRows = []element.Element{} // we want empty slices for deep comparison
	for {
		row, err := decoder.Read()
		if err == io.EOF {
			result := element.Bundle{dataType, incomingRows}
			return &result, nil
		}
		if err != nil {
			return nil, err
		}
		incomingRows = append(incomingRows, row)
	}
}
