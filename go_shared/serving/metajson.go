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
package serving

import (
	"bytes"
	"encoding/json"
	"github.com/mantik-ai/core/go_shared/ds/element"
	"github.com/mantik-ai/core/go_shared/ds/formats/natural"
	"github.com/mantik-ai/core/go_shared/ds/util/serializer"
	"github.com/mantik-ai/core/go_shared/util/yaml"
	"github.com/pkg/errors"
	"io"
	"strings"
)

// Implement Meta JSON as being used on Scala Side
// There is a block "metaVariables", which can be referenced by "${metaVariableName}" in JSON
// And which is applied to everything except Meta Variables.

// A Single Meta Variable
type MetaVariable struct {
	Name   string `json:"name"`
	Fix    bool   `json:"fix"`
	Bundle element.Bundle
	// Serialized JSON value
	JsonValue []byte
}

func (m *MetaVariable) UnmarshalJSON(data []byte) error {
	// decode Bundle
	var p metaVariableHeader
	err := json.Unmarshal(data, &p)
	if err != nil {
		return err
	}
	var v natural.BundleRef
	err = json.Unmarshal(data, &v)
	if err != nil {
		return err
	}
	if v.Bundle.IsTabular() {
		return errors.New("Meta variables may only contain single values")
	}
	m.Bundle = v.Bundle
	m.Name = p.Name
	m.Fix = p.Fix

	encoded, err := natural.EncodeBundleValue(&v.Bundle, serializer.BACKEND_JSON)
	if err != nil {
		return err
	}
	m.JsonValue = encoded
	return nil
}

// Helper for parsing meta variables
type metaVariableHeader struct {
	Name string `json:"name"`
	Fix  bool   `json:"fix"`
}

// Contains a Meta variables block
type metaVariablesHeader struct {
	MetaVariables MetaVariables `json:"metaVariables"`
}

// A list of Meta Variables.
type MetaVariables []MetaVariable

// Finds a Meta Variable by name or return nil.
func (m *MetaVariables) GetByName(name string) *MetaVariable {
	for _, v := range *m {
		if v.Name == name {
			return &v
		}
	}
	return nil
}

/** Decodes the Meta JSON as being used in MantikHeaders. Returns JSON or an error. */
func DecodeMetaJson(data []byte) ([]byte, error) {
	var d metaVariablesHeader
	err := json.Unmarshal(data, &d)
	if err != nil {
		return nil, err
	}

	decoder := json.NewDecoder(bytes.NewReader(data))
	result := bytes.Buffer{}
	err = decodeMetaJsonValue(d.MetaVariables, decoder, &result, 0, false)
	if err != nil {
		return nil, err
	}
	return result.Bytes(), nil
}

/* Decode a Meta json value, writes new json
   meta meta variables
   decoder JSON decoder
   result where the output goes
   depth: current depth
   ignore: if true, do not apply interpolation
*/
func decodeMetaJsonValue(meta MetaVariables, decoder *json.Decoder, result io.Writer, depth int, ignore bool) error {
	t, err := decoder.Token()
	if err == io.EOF {
		return io.ErrUnexpectedEOF
	}
	if err != nil {
		return err
	}
	return decodeMetaJsonValueFromToken(t, meta, decoder, result, depth, ignore)
}

func decodeMetaJsonValueFromToken(token json.Token, meta MetaVariables, decoder *json.Decoder, result io.Writer, depth int, ignore bool) error {
	var err error
	switch v := token.(type) {
	case json.Delim:
		if v == '[' {
			err = decodeMetaJsonArray(meta, decoder, result, depth+1, ignore)
		} else if v == '{' {
			err = decodeMetaJsonObject(meta, decoder, result, depth+1, ignore)
		} else {
			return errors.Errorf("Unexpected %s", v.String())
		}
	case bool:
		err = writeJsonValue(v, result)
	case json.Number:
		_, err = result.Write([]byte(v.String()))
	case float64:
		err = writeJsonValue(v, result)
	case string:
		err = decodeMetaJsonString(meta, v, result, ignore)
	default:
		if v == nil {
			_, err = result.Write([]byte("null"))
		} else {
			return errors.Errorf("Unexpected token %v", token)
		}
	}
	return err
}

func decodeMetaJsonArray(meta MetaVariables, decoder *json.Decoder, result io.Writer, depth int, ignore bool) error {
	_, err := result.Write([]byte("["))
	if err != nil {
		return err
	}
	var first = true
	for {
		t, err := decoder.Token()
		if err == io.EOF {
			return io.ErrUnexpectedEOF
		}
		if err != nil {
			return err
		}
		delim, isDelim := t.(json.Delim)
		if isDelim && delim == ']' {
			_, err := result.Write([]byte("]"))
			if err != nil {
				return err
			}
			return nil
		} else {
			if !first {
				_, err := result.Write([]byte(","))
				if err != nil {
					return err
				}
			}
			first = false
			err = decodeMetaJsonValueFromToken(t, meta, decoder, result, depth, ignore)
			if err != nil {
				return err
			}
		}
	}
}

func decodeMetaJsonObject(meta MetaVariables, decoder *json.Decoder, result io.Writer, depth int, ignore bool) error {
	_, err := result.Write([]byte("{"))
	if err != nil {
		return err
	}
	var first = true
	for {
		t, err := decoder.Token()
		if err == io.EOF {
			return io.ErrUnexpectedEOF
		}
		if err != nil {
			return err
		}
		delim, isDelim := t.(json.Delim)
		if isDelim && delim == '}' {
			_, err := result.Write([]byte("}"))
			if err != nil {
				return err
			}
			return nil
		} else {
			if !first {
				_, err := result.Write([]byte(","))
				if err != nil {
					return err
				}
			}
			first = false
			// Must be a key
			key, keyIsString := t.(string)
			if !keyIsString {
				return errors.New("Expected Map key")
			}
			err = writeJsonValue(key, result)
			if err != nil {
				return err
			}
			// Now a map value is expected
			_, err := result.Write([]byte(":"))
			if err != nil {
				return err
			}
			// MetaVariables itself should not be interpolated.
			newIgnore := ignore || (depth == 1 && key == "metaVariables")
			err = decodeMetaJsonValue(meta, decoder, result, depth, newIgnore)
			if err != nil {
				return err
			}
		}
	}
}

const metaJsonPrefix = "${"
const metaJsonSuffix = "}"

func decodeMetaJsonString(meta MetaVariables, v string, result io.Writer, ignore bool) error {
	if ignore {
		return writeJsonValue(v, result)
	}
	if strings.HasPrefix(v, "$$") {
		s := strings.TrimPrefix(v, "$")
		return writeJsonValue(s, result)
	}
	if strings.HasPrefix(v, metaJsonPrefix) && strings.HasSuffix(v, metaJsonSuffix) {
		variableName := strings.TrimSuffix(strings.TrimPrefix(
			v, metaJsonPrefix,
		), metaJsonSuffix)
		value := meta.GetByName(variableName)
		if value == nil {
			return errors.Errorf("Meta variable %s not found", variableName)
		}
		_, err := result.Write(value.JsonValue)
		return err
	} else {
		return writeJsonValue(v, result)
	}
}

func writeJsonValue(v interface{}, result io.Writer) error {
	bytes, err := json.Marshal(v)
	if err != nil {
		return err
	}
	_, err = result.Write(bytes)
	return err
}

/** Decode Meta JSON encoded as YAML, as being used in MantikHeaders. Returns JSON or an error. */
func DecodeMetaYaml(data []byte) ([]byte, error) {
	json, err := yaml.YamlToJson(data)
	if err != nil {
		return nil, err
	}
	return DecodeMetaJson(json)
}

func UnmarshallMetaYaml(data []byte, result interface{}) error {
	asJson, err := DecodeMetaYaml(data)
	if err != nil {
		return err
	}
	return json.Unmarshal(asJson, result)
}
