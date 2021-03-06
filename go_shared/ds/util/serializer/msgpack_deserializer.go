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
	"bytes"
	"encoding/json"
	"github.com/pkg/errors"
	"github.com/vmihailenco/msgpack"
	"github.com/vmihailenco/msgpack/codes"
)

type msgPackDeserializingBackend struct {
	*msgpack.Decoder
}

func (m msgPackDeserializingBackend) DecodeHeader() (*Header, error) {
	var header Header
	err := m.DecodeJson(&header)
	return &header, err
}

func (m msgPackDeserializingBackend) StartReadingTabularValues() error {
	// nothing to do
	return nil
}

func (m msgPackDeserializingBackend) NextIsNil() (bool, error) {
	code, err := m.PeekCode()
	return code == codes.Nil, err
}

func (m msgPackDeserializingBackend) DecodeJson(destination interface{}) error {
	// Convert to JSON then parsing through regular JSON facilities in order
	// to make it default to golang JSON structures
	data, err := m.DecodeRawJson()
	if err != nil {
		return err
	}
	return json.Unmarshal(data, destination)
}

func (m msgPackDeserializingBackend) DecodeRawJson() ([]byte, error) {
	var buf bytes.Buffer
	err := m.decodePlainJson(&buf)
	return buf.Bytes(), err
}

func (m msgPackDeserializingBackend) DecodeFloat32() (float32, error) {
	// Workaround Issue #61, python encodes float32 to float64, so let's not fail on float64 here.
	value, err := m.DecodeFloat64()
	return float32(value), err
}

func (m msgPackDeserializingBackend) decodePlainJson(buf *bytes.Buffer) error {
	code, err := m.PeekCode()

	addMarshall := func(i interface{}) error {
		marshalled, err := json.Marshal(i)
		if err != nil {
			return err
		}
		_, err = buf.Write(marshalled)
		return err
	}

	if err != nil {
		return err
	}
	if code == codes.Array16 || code == codes.Array32 || codes.IsFixedArray(code) {
		return m.decodePlainJsonArray(buf)
	}
	if code == codes.Map16 || code == codes.Map32 || codes.IsFixedMap(code) {
		return m.decodePlainJsonMap(buf)
	}
	if codes.IsString(code) {
		s, err := m.DecodeString()
		if err != nil {
			return err
		}
		return addMarshall(s)
	}
	if codes.IsFixedNum(code) || code == codes.Int8 || code == codes.Int16 || code == codes.Int32 || code == codes.Int64 {
		i, err := m.DecodeInt64()
		if err != nil {
			return err
		}
		return addMarshall(i)
	}
	if code == codes.Uint8 || code == codes.Uint16 || code == codes.Uint32 || code == codes.Uint64 {
		i, err := m.DecodeUint64()
		if err != nil {
			return err
		}
		return addMarshall(i)
	}
	if code == codes.Float || code == codes.Double {
		i, err := m.DecodeFloat64()
		if err != nil {
			return err
		}
		return addMarshall(i)
	}
	if code == codes.True || code == codes.False {
		b, err := m.DecodeBool()
		if err != nil {
			return err
		}
		return addMarshall(b)
	}
	if code == codes.Nil {
		_, err := buf.Write([]byte("null"))
		return err
	}
	return errors.Errorf("Unsupported type %d", code)
}

func (m msgPackDeserializingBackend) decodePlainJsonArray(buf *bytes.Buffer) error {
	arrayLength, err := m.DecodeArrayLen()
	if err != nil {
		return err
	}
	if err = buf.WriteByte('['); err != nil {
		return err
	}
	for i := 0; i < arrayLength; i++ {
		if i > 0 {
			if err = buf.WriteByte(','); err != nil {
				return err
			}
		}
		if err = m.decodePlainJson(buf); err != nil {
			return err
		}
	}
	if err = buf.WriteByte(']'); err != nil {
		return err
	}
	return nil
}

func (m msgPackDeserializingBackend) decodePlainJsonMap(buf *bytes.Buffer) error {
	mapLength, err := m.DecodeMapLen()
	if err != nil {
		return err
	}
	if err = buf.WriteByte('{'); err != nil {
		return err
	}
	for i := 0; i < mapLength; i++ {
		if i > 0 {
			if err = buf.WriteByte(','); err != nil {
				return err
			}
		}
		s, err := m.DecodeString()
		if err != nil {
			return err
		}
		sbytes, err := json.Marshal(s)
		if err != nil {
			return err
		}
		if _, err = buf.Write(sbytes); err != nil {
			return err
		}
		if err = buf.WriteByte(':'); err != nil {
			return err
		}
		if err = m.decodePlainJson(buf); err != nil {
			return err
		}
	}
	if err = buf.WriteByte('}'); err != nil {
		return err
	}
	return nil
}
