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
	"encoding/base64"
	"encoding/json"
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/util/serializer/jsonsplit"
	"io"
	"io/ioutil"
)

type jsonDeserializer struct {
	jsonBlob        []byte
	splitter        *jsonsplit.JsonSplitter
	pendingElements []jsonsplit.JsonElement
	pos             int
}

type jsonStructure struct {
	Type  ds.TypeReference
	Value json.RawMessage
}

func MakeJsonDeserializerFromReader(reader io.Reader) (DeserializingBackend, error) {
	data, err := ioutil.ReadAll(reader)
	if err != nil {
		return nil, err
	}
	return MakeJsonDeserializer(data), nil
}

func MakeJsonDeserializer(blob []byte) DeserializingBackend {
	return &jsonDeserializer{
		blob,
		nil,
		nil,
		0,
	}
}

func (j *jsonDeserializer) nextElement() (jsonsplit.JsonElement, error) {
	if j.splitter == nil {
		j.splitter = jsonsplit.MakeJsonSplitter(j.jsonBlob)
	}
	if j.pos >= len(j.pendingElements) {
		j.pos = 0
		elements, err := j.splitter.ReadJsonElements()
		if err != nil {
			return jsonsplit.JsonElement{}, err
		}
		j.pendingElements = elements
	}
	result := j.pendingElements[j.pos]
	j.pos += 1
	return result, nil
}

func (j *jsonDeserializer) putBack() {
	if j.pos <= 0 {
		panic("Cannot put back last value, as we are on the first position")
	}
	j.pos--
}

func (j *jsonDeserializer) DecodeArrayLen() (int, error) {
	e, err := j.nextElement()
	if err != nil {
		return 0, err
	}
	if e.ElementType != jsonsplit.Array {
		return 0, errors.New("No array type")
	}
	return e.Value.(int), nil
}

func (j *jsonDeserializer) DecodeHeader() (*Header, error) {
	if j.splitter != nil {
		panic("Cannot decode header after reading started")
	}
	var structure jsonStructure
	err := json.Unmarshal(j.jsonBlob, &structure)
	if err != nil {
		return nil, err
	}
	var header Header
	header.Format = structure.Type
	// Start over with value
	j.jsonBlob = structure.Value
	return &header, nil
}

func (j *jsonDeserializer) StartReadingTabularValues() error {
	splitter, err := jsonsplit.MakeJsonSplitterForArray(j.jsonBlob)
	if err != nil {
		return err
	}
	j.splitter = splitter
	return nil
}

func (j *jsonDeserializer) NextIsNil() (bool, error) {
	e, err := j.nextElement()
	if err != nil {
		return false, err
	}
	j.putBack()
	return e.Value.(string) == "null", nil
}

func (j *jsonDeserializer) DecodeLiteral(i interface{}) error {
	e, err := j.nextElement()
	if err != nil {
		return err
	}
	if e.ElementType != jsonsplit.OtherLiteral {
		return errors.New("Expected literal")
	}
	return json.Unmarshal([]byte(e.Value.(string)), i)
}

func (j *jsonDeserializer) DecodeInt8() (int8, error) {
	var i int8
	err := j.DecodeLiteral(&i)
	return i, err
}

func (j *jsonDeserializer) DecodeUint8() (uint8, error) {
	var i uint8
	err := j.DecodeLiteral(&i)
	return i, err
}

func (j *jsonDeserializer) DecodeInt32() (int32, error) {
	var i int32
	err := j.DecodeLiteral(&i)
	return i, err
}

func (j *jsonDeserializer) DecodeUint32() (uint32, error) {
	var i uint32
	err := j.DecodeLiteral(&i)
	return i, err
}

func (j *jsonDeserializer) DecodeInt64() (int64, error) {
	var i int64
	err := j.DecodeLiteral(&i)
	return i, err
}

func (j *jsonDeserializer) DecodeUint64() (uint64, error) {
	var i uint64
	err := j.DecodeLiteral(&i)
	return i, err
}

func (j *jsonDeserializer) DecodeString() (string, error) {
	e, err := j.nextElement()
	if err != nil {
		return "", err
	}
	if e.ElementType != jsonsplit.String {
		return "", errors.New("No string type")
	}
	return e.Value.(string), nil
}

func (j *jsonDeserializer) DecodeFloat32() (float32, error) {
	var i float32
	err := j.DecodeLiteral(&i)
	return i, err
}

func (j *jsonDeserializer) DecodeFloat64() (float64, error) {
	var i float64
	err := j.DecodeLiteral(&i)
	return i, err
}

func (j *jsonDeserializer) DecodeBool() (bool, error) {
	var i bool
	err := j.DecodeLiteral(&i)
	return i, err
}

func (j *jsonDeserializer) DecodeBytes() ([]byte, error) {
	s, err := j.DecodeString()
	if err != nil {
		return nil, err
	}
	decoder := base64.NewDecoder(base64.StdEncoding, bytes.NewReader([]byte(s)))
	data, err := ioutil.ReadAll(decoder)
	return data, err
}

func (j *jsonDeserializer) DecodeNil() error {
	e, err := j.nextElement()
	if err != nil {
		return err
	}
	if e.ElementType != jsonsplit.OtherLiteral {
		return errors.New("Expected literal")
	}
	// discard content
	return nil
}
