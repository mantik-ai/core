package jsonsplit

import (
	"encoding/json"
	"github.com/stretchr/testify/assert"
	"io"
	"testing"
)

func snippetize(s string) ([]JsonElement, error) {
	data := []byte(s)
	splitter := MakeJsonSplitter(data)
	result := make([]JsonElement, 0)
	for {
		elements, err := splitter.ReadJsonElements()
		result = append(result, elements...)
		if err == io.EOF {
			return result, nil
		}
		if err != nil {
			return nil, err
		}
	}
}

func snippetizeArray(s string) ([]JsonElement, error) {
	data := []byte(s)
	splitter, err := MakeJsonSplitterForArray(data)
	if err != nil {
		return nil, err
	}
	result := make([]JsonElement, 0)
	for {
		elements, err := splitter.ReadJsonElements()
		result = append(result, elements...)
		if err == io.EOF {
			return result, nil
		}
		if err != nil {
			return nil, err
		}
	}
}

func forceSnippetize(t *testing.T, s string) []JsonElement {
	parts, err := snippetize(s)
	assert.NoError(t, err)
	return parts
}

func forceSnippetizeArray(t *testing.T, s string) []JsonElement {
	parts, err := snippetizeArray(s)
	assert.NoError(t, err)
	return parts
}

func TestEmptyString(t *testing.T) {
	elements, err := snippetize("")
	assert.NoError(t, err)
	assert.Equal(t, []JsonElement{}, elements)
}

func TestSimpleLiterals(t *testing.T) {
	samples := []string{
		"123",
		"1",
		"true",
		"false",
		"1.3",
		"2.563",
		"null",
	}
	for _, sample := range samples {
		parts, err := snippetize(sample)
		assert.NoError(t, err)

		assert.Equal(t, []JsonElement{JsonElement{OtherLiteral, sample}}, parts)
	}
}

func TestStrings(t *testing.T) {
	samples := []string{
		"\"\"",
		"\"a\"",
		"\"abc\"",
		"\"ab\\\"\"",
		"\"ab\\\"b\"",
		"\"ab,ac\"",
	}
	for _, sample := range samples {
		parts, err := snippetize(sample)
		assert.NoError(t, err)

		var s string
		err = json.Unmarshal([]byte(sample), &s)
		assert.NoError(t, err)

		assert.Equal(t, []JsonElement{JsonElement{String, s}}, parts)
	}
}

func TestArrays(t *testing.T) {
	assert.Equal(t, []JsonElement{{Array, 0}}, forceSnippetize(t, "[]"))
	assert.Equal(t, []JsonElement{{Array, 1}, {OtherLiteral, "1"}}, forceSnippetize(t, "[1]"))
	assert.Equal(t, []JsonElement{{Array, 2}, {OtherLiteral, "1"}, {OtherLiteral, "2"}}, forceSnippetize(t, "[1,2]"))
	assert.Equal(t, []JsonElement{{Array, 3}, {OtherLiteral, "1"}, {OtherLiteral, "2"}, {String, "Hello"}}, forceSnippetize(t, "[1,2,\"Hello\"]"))
}

func TestObjects(t *testing.T) {
	assert.Equal(t, []JsonElement{{Object, 0}}, forceSnippetize(t, "{}"))
	assert.Equal(t, []JsonElement{{Object, 1}, {String, "hello"}, {String, "world"}}, forceSnippetize(t, "{\"hello\":\"world\"}"))
	assert.Equal(t, []JsonElement{
		{Object, 2},
		{String, "hello"},
		{String, "world"},
		{String, "foo"},
		{String, "bar"},
	}, forceSnippetize(t, "{\"hello\":\"world\", \"foo\":\"bar\"}"))
}

func TestMixed(t *testing.T) {
	assert.Equal(t, []JsonElement{
		{Object, 1},
		{String, "hello"},
		{Array, 5},
		{OtherLiteral, "1"},
		{OtherLiteral, "2"},
		{Object, 0},
		{Array, 0},
		{Object, 1},
		{String, "a"},
		{OtherLiteral, "123"},
	}, forceSnippetize(t, "{\"hello\":[1,2,{},[],{\"a\":123}]}"))
}

func TestWhitespace(t *testing.T) {
	sample :=
		`
		{
			"a": [1,2,3,4],
			"b": {
			},
			"c": {
				"4": false
			}
		}
		`

	assert.Equal(t, []JsonElement{
		{Object, 3},
		{String, "a"},
		{Array, 4},
		{OtherLiteral, "1"},
		{OtherLiteral, "2"},
		{OtherLiteral, "3"},
		{OtherLiteral, "4"},
		{String, "b"},
		{Object, 0},
		{String, "c"},
		{Object, 1},
		{String, "4"},
		{OtherLiteral, "false"},
	}, forceSnippetize(t, sample))

}

func TestSnippetizeArray(t *testing.T) {
	assert.Equal(t, []JsonElement{}, forceSnippetizeArray(t, "[]"))
	assert.Equal(t, []JsonElement{
		{String, "Hello World"},
	}, forceSnippetizeArray(t, "[\"Hello World\"]"))
	assert.Equal(t, []JsonElement{
		{String, "Hello World"},
		{OtherLiteral, "1"},
		{OtherLiteral, "2"},
	}, forceSnippetizeArray(t, "[\"Hello World\", 1, 2]"))
}

func TestSnippetizeComplexArray(t *testing.T) {
	sample :=
		`
		[
			[1,2,3,4],
			{"b": {
			}},
			{"c": {
				"4": false
			}}
		]
		`

	assert.Equal(t, []JsonElement{
		{Array, 4},
		{OtherLiteral, "1"},
		{OtherLiteral, "2"},
		{OtherLiteral, "3"},
		{OtherLiteral, "4"},
		{Object, 1},
		{String, "b"},
		{Object, 0},
		{Object, 1},
		{String, "c"},
		{Object, 1},
		{String, "4"},
		{OtherLiteral, "false"},
	}, forceSnippetizeArray(t, sample))
}
