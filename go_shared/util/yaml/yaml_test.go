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
