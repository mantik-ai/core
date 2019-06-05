package serving

import (
	"bytes"
	"encoding/json"
	"github.com/stretchr/testify/assert"
	"testing"
)

func testDecodeMetaJsonValue(t *testing.T, expected string, input string) {
	reader := bytes.NewBufferString(input)
	decoder := json.NewDecoder(reader)
	writer := bytes.Buffer{}
	err := decodeMetaJsonValue(nil, decoder, &writer, 0, false)
	result := writer.Bytes()
	assert.NoError(t, err)
	assert.Equal(t, expected, string(result))
}

// Test DecodeMetaJson when in and out should be equal.
func testDecodeMetaJsonValueEq(t *testing.T, inOut string) {
	testDecodeMetaJsonValue(t, inOut, inOut)
}

func TestDecodeMetaJsonValue(t *testing.T) {
	testDecodeMetaJsonValueEq(t, "null")
	testDecodeMetaJsonValueEq(t, "true")
	testDecodeMetaJsonValueEq(t, "false")
	testDecodeMetaJsonValueEq(t, "0")
	testDecodeMetaJsonValueEq(t, "10")
	testDecodeMetaJsonValueEq(t, "10.5")
	testDecodeMetaJsonValueEq(t, "[]")
	testDecodeMetaJsonValueEq(t, "[1]")
	testDecodeMetaJsonValueEq(t, "{}")
	testDecodeMetaJsonValueEq(t, `{"hello":1}`)
	testDecodeMetaJsonValueEq(t, `"Hello World"`)
	testDecodeMetaJsonValueEq(t, `["Hello","World",12]`)
	testDecodeMetaJsonValueEq(t, `{"Hello":1,"World":"bar"}`)
	testDecodeMetaJsonValueEq(t, `{"${foo}":1}`) // map keys are not resolved
}

func testDecodeMetaJson(t *testing.T, expected string, input string) {
	decoded, err := DecodeMetaJson([]byte(input))
	assert.NoError(t, err)
	var parsedGot interface{}
	err = json.Unmarshal(decoded, &parsedGot)
	assert.NoError(t, err)
	var parsedExpected interface{}
	err = json.Unmarshal([]byte(expected), &parsedExpected)
	assert.NoError(t, err)
	assert.Equal(t, parsedExpected, parsedGot)
}

func TestDecodeMetaJson(t *testing.T) {
	sample := `
		{
			"metaVariables": [
				{
					"name": "foo",
					"type": "int32",
					"value": 5
				},
				{
					"name": "bar",
					"type": "bool",
					"value": false
				}
			],
			"other": {
				"value": "${foo}",
				"other": ["${bar}", 100, "$${escaped}"]
			}
		}
	`
	expected := `
		{
			"metaVariables": [
				{
					"name": "foo",
					"type": "int32",
					"value": 5
				},
				{
					"name": "bar",
					"type": "bool",
					"value": false
				}
			],
			"other": {
				"value": 5,
				"other": [false, 100, "${escaped}"]
			}
		}
	`
	testDecodeMetaJson(t, expected, sample)
}

func testDecodeMetaYaml(t *testing.T, expected string, input string) {
	decoded, err := DecodeMetaYaml([]byte(input))
	assert.NoError(t, err)
	var parsedGot interface{}
	err = json.Unmarshal(decoded, &parsedGot)
	assert.NoError(t, err)
	var parsedExpected interface{}
	err = json.Unmarshal([]byte(expected), &parsedExpected)
	assert.NoError(t, err)
	assert.Equal(t, parsedExpected, parsedGot)
}

func TestDecodeMetaYaml(t *testing.T) {
	sample := `
metaVariables:
  - name: foo
    value: 13
    type: int32
    fix: true
code:
  value: ${foo}
`
	expected := `
{
	"metaVariables": [
		{
			"name": "foo",
			"value": 13,
			"type": "int32",
			"fix": true
		}
	],
	"code": {
		"value": 13
	}
}
`
	testDecodeMetaYaml(t, expected, sample)
}

func TestIgnoreMetaJsonBlock(t *testing.T) {
	// it must ignore the meta variables block itself for interpolation.
	sample := `
		{
			"metaVariables": [
				{
					"name": "foo",
					"type": "int32",
					"value": 5,
					"other": "${foo}"
				}
			]
		}
	`
	testDecodeMetaJson(t, sample, sample)
}
