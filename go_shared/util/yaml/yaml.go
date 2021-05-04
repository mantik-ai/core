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
package yaml

import (
	"bytes"
	"encoding/json"
	"github.com/pkg/errors"
	"gopkg.in/yaml.v3"
	"io"
)

// Custom YAML code, as we have rely on ordering in maps (especially ds.TabularData)
// and underlying libraries (https://github.com/ghodss/yaml) had no support for them
// Note: this depends on yaml.v3 which is still beta at time of writing.

// See Bug %55

// The main goal is to have YAML support by using the standard Go JSON Marshallers.
// As we need to maintain YAML and JSON Compatibility (YAML only needed for Reading, not writing)

// Unmarshal yaml by converting to JSON and using the regular go JSON facilities
func Unmarshal(data []byte, value interface{}) error {
	jsonCode, err := YamlToJson(data)
	if err != nil {
		return err
	}
	return json.Unmarshal(jsonCode, value)
}

// Marshall to yaml by converting to JSON first and then converting to YAML
func Marshal(value interface{}) ([]byte, error) {
	jsonCode, err := json.Marshal(value)
	if err != nil {
		return nil, err
	}
	return JsonToYaml(jsonCode)
}

/* Convert YAML to JSON while preserving the oder in Maps as being used in DS Data Types. */
func YamlToJson(data []byte) ([]byte, error) {
	buffer := bytes.Buffer{}
	serializer := jsonSerializer{&buffer}
	err := yaml.Unmarshal(data, &serializer)
	if err != nil {
		return nil, err
	}
	if buffer.Len() == 0 {
		return []byte("null"), nil
	}
	return buffer.Bytes(), nil
}

type jsonSerializer struct {
	writer io.Writer
}

func (s *jsonSerializer) append(c byte) {
	_, err := s.writer.Write([]byte{c})
	if err != nil {
		panic(err.Error())
	}
}

func (s *jsonSerializer) appendMany(data []byte) {
	_, err := s.writer.Write(data)
	if err != nil {
		panic(err.Error())
	}
}

func (s *jsonSerializer) appendString(str string) {
	s.appendMany([]byte(str))
}

func (s *jsonSerializer) appendScalar(value *yaml.Node) error {
	switch value.Tag {
	case "!!null":
		s.appendString("null")
	case "!!str":
		encoded, err := json.Marshal(value.Value)
		if err != nil {
			return err
		}
		s.appendMany(encoded)
	default:
		s.appendString(value.Value) // let it crash..
	}
	return nil
}

var stringAsKeyError = errors.New("JSON only supports strings as keys")

func (s *jsonSerializer) UnmarshalYAML(value *yaml.Node) error {
	switch value.Kind {
	case yaml.ScalarNode:
		return s.appendScalar(value)
	case yaml.SequenceNode:
		s.append('[')
		first := true
		for _, c := range value.Content {
			if !first {
				s.append(',')
			}
			first = false
			err := s.UnmarshalYAML(c)
			if err != nil {
				return err
			}
		}
		s.append(']')
	case yaml.MappingNode:
		s.append('{')
		first := true
		for i := 0; i < len(value.Content); i += 2 {
			left := value.Content[i]
			right := value.Content[i+1]
			if !first {
				s.append(',')
			}
			first = false
			if left.Tag != "!!str" {
				return stringAsKeyError
			}
			err := s.UnmarshalYAML(left)
			if err != nil {
				return err
			}
			s.append(':')
			err = s.UnmarshalYAML(right)
			if err != nil {
				return err
			}
		}
		s.append('}')
	}
	return nil
}

// Converts Json To Yaml
func JsonToYaml(data []byte) ([]byte, error) {
	var node yaml.Node
	err := yaml.Unmarshal(data, &node)
	if err != nil {
		return nil, err
	}
	fixStyle(&node)
	if len(node.Content) == 0 {
		return []byte("null"), nil
	}
	return yaml.Marshal(node.Content[0])
}

func fixStyle(node *yaml.Node) {
	if node.Kind == yaml.MappingNode || node.Kind == yaml.SequenceNode {
		node.Style = yaml.LiteralStyle
	}
	if node.Kind == yaml.ScalarNode {
		node.Style = yaml.FlowStyle
	}
	for _, c := range node.Content {
		fixStyle(c)
	}
}
