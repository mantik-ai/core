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
package ds

import (
	"bytes"
	"encoding/json"
	"github.com/pkg/errors"
	"io"
)

// Named ordered data types
type NamedDataTypeMap struct {
	Values []NamedType
}

func NewNamedDataTypeMap(namedTypes ...NamedType) NamedDataTypeMap {
	return NamedDataTypeMap{namedTypes}
}

func (o NamedDataTypeMap) IndexOf(name string) int {
	for i, value := range o.Values {
		if value.Name == name {
			return i
		}
	}
	return -1
}

func (o NamedDataTypeMap) Get(name string) DataType {
	for _, value := range o.Values {
		if value.Name == name {
			return value.SubType.Underlying
		}
	}
	return nil
}

func (o NamedDataTypeMap) ByIdx(idx int) NamedType {
	return o.Values[idx]
}

func (o NamedDataTypeMap) Arity() int {
	return len(o.Values)
}

type NamedType struct {
	Name    string
	SubType TypeReference
}

func (omap NamedDataTypeMap) MarshalJSON() ([]byte, error) {
	var buf bytes.Buffer

	buf.WriteString("{")
	for i, kv := range omap.Values {
		if i != 0 {
			buf.WriteString(",")
		}
		// marshal key
		key, err := json.Marshal(kv.Name)
		if err != nil {
			return nil, err
		}
		buf.Write(key)
		buf.WriteString(":")
		// marshal value
		val, err := json.Marshal(kv.SubType)
		if err != nil {
			return nil, err
		}
		buf.Write(val)
	}

	buf.WriteString("}")
	return buf.Bytes(), nil
}

func (omap *NamedDataTypeMap) UnmarshalJSON(data []byte) error {
	dec := json.NewDecoder(bytes.NewReader(data))
	dec.UseNumber()
	t, err := dec.Token()
	if err != nil {
		return err
	}
	if delim, ok := t.(json.Delim); !ok || delim != '{' {
		return errors.New("Expected {")
	}
	keyvalues := make([]NamedType, 0)

	for dec.More() {
		t, err = dec.Token()
		if err != nil {
			return err
		}
		key, ok := t.(string)
		if !ok {
			return errors.New("Expected string, got something else")
		}
		var value TypeReference
		err = dec.Decode(&value)
		if err != nil {
			return err
		}
		keyvalues = append(keyvalues, NamedType{key, value})
	}
	t, err = dec.Token()
	if delim, ok := t.(json.Delim); !ok || delim != '}' {
		return errors.New("Expected }")
	}
	t, err = dec.Token()
	if err != io.EOF {
		return errors.New("No data expected")
	}
	*omap = NamedDataTypeMap{keyvalues}
	return nil
}
