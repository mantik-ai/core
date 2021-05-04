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
	"encoding/json"
	"github.com/stretchr/testify/assert"
	"testing"
)

var jsonSamples = []string{
	`true`, `false`, `null`, `1`, `0`, `[1,2,3,4]`, `[]`, `{}`, `{"hello":"world"}`, `{"z":1,"y":2,"x":3}`,
	`[1,null]`,
	`{"a":1, "s":null}`,
	`{
		"a": { "b": [], "c": [1,2,{}, null]}
	}`,
}

func TestJsonYamlConversion(t *testing.T) {
	for _, sample := range jsonSamples {
		var o1 interface{}
		err := json.Unmarshal([]byte(sample), &o1)
		assert.NoError(t, err)
		yaml, err := JsonToYaml([]byte(sample))
		assert.NoError(t, err)
		jsonAgain, err := YamlToJson([]byte(yaml))
		assert.NoError(t, err)
		yaml2, err := JsonToYaml([]byte(jsonAgain))
		assert.NoError(t, err)
		json2, err := YamlToJson([]byte(yaml2))
		var o2 interface{}
		err = json.Unmarshal(json2, &o2)
		assert.NoError(t, err)
		assert.Equal(t, o1, o2)
	}
}

func TestMultiDoc(t *testing.T) {
	doc := []byte(`
a: b
---
a: c
`)
	// Note: this behaviour is not necessary, but should be stable
	json, err := YamlToJson(doc)
	assert.NoError(t, err)
	assert.Equal(t, `{"a":"b"}`, string(json))
}

func TestInvalidKey(t *testing.T) {
	doc := []byte(
		`1: foo`,
	)
	json, err := YamlToJson(doc)
	assert.Equal(t, stringAsKeyError, err)
	assert.Nil(t, json)
}
