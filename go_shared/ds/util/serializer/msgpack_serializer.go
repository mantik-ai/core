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
package serializer

import (
	"encoding/json"
	"github.com/buger/jsonparser"
	"github.com/pkg/errors"
	"github.com/vmihailenco/msgpack"
)

type msgPackSerializingBackend struct {
	*msgpack.Encoder
}

func (m msgPackSerializingBackend) EncodeHeader(h *Header) error {
	return m.EncodeJson(h)
}

func (m msgPackSerializingBackend) StartTabularValues() error {
	// not needed for msgpack
	return nil
}

func (m msgPackSerializingBackend) NextRow() error {
	// not needed for msgpack
	return nil
}

func (m msgPackSerializingBackend) Finish() error {
	// Nothing to do
	return nil
}

func (m msgPackSerializingBackend) EncodeJson(i interface{}) error {
	// We want the JSON Marshalling to use the regular MarshallJSON Routines
	// Thats why we have to convert to JSON and transcode to MsgPack afterwards
	// If we directly encode it via m.encoder.Encode(i) we get a different encoding
	bytes, err := json.Marshal(i)
	if err != nil {
		return err
	}
	return m.EncodeRawJson(bytes)
}

func (m msgPackSerializingBackend) EncodeRawJson(jsonBytes []byte) error {
	value, dataType, _, err := jsonparser.Get(jsonBytes)
	if err != nil {
		return err
	}
	return m.encodeJsonWithType(value, dataType)
}

func (m msgPackSerializingBackend) encodeJsonWithType(value []byte, dataType jsonparser.ValueType) error {
	switch dataType {
	case jsonparser.String:
		s := (string)(value)
		return m.EncodeString(s)
	case jsonparser.Object:
		count := 0
		counter := func([]byte, []byte, jsonparser.ValueType, int) error {
			count += 1
			return nil
		}
		err := jsonparser.ObjectEach(value, counter)
		if err != nil {
			return nil
		}
		err = m.EncodeMapLen(count)
		if err != nil {
			return nil
		}
		subWriter := func(key []byte, value []byte, valueType jsonparser.ValueType, offset int) error {
			// key is always string
			err = m.EncodeString((string)(key))
			if err != nil {
				return err
			}
			err = m.encodeJsonWithType(value, valueType)
			return nil
		}
		return jsonparser.ObjectEach(value, subWriter)
	case jsonparser.Number:
		i, err := jsonparser.GetInt(value)
		if err == nil {
			return m.EncodeInt(i)
		}
		f, err := jsonparser.GetFloat(value)
		if err != nil {
			return err
		}
		return m.EncodeFloat64(f)
	case jsonparser.Null:
		return m.EncodeNil()
	case jsonparser.Boolean:
		b, err := jsonparser.ParseBoolean(value)
		if err != nil {
			return err
		}
		return m.EncodeBool(b)
	case jsonparser.Array:
		count := 0
		counter := func([]byte, jsonparser.ValueType, int, error) {
			count += 1
		}
		_, err := jsonparser.ArrayEach(value, counter)
		if err != nil {
			return nil
		}
		err = m.EncodeArrayLen(count)
		if err != nil {
			return nil
		}
		subWriter := func(value []byte, valueType jsonparser.ValueType, offset int, e error) {
			subError := m.encodeJsonWithType(value, valueType)
			if subError != nil {
				err = subError
			}
		}
		if err != nil {
			return err
		}
		_, err = jsonparser.ArrayEach(value, subWriter)
		return err
	}
	return errors.Errorf("Unimplemented sub type %d", dataType)
}

func (m msgPackSerializingBackend) Flush() error {
	// Nothing
	return nil
}
