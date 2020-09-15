package ds

import (
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/util/yaml"
	"testing"
)

var primitiveSamples = []string{
	"int8",
	"uint8",
	"int32",
	"uint32",
	"int64",
	"uint64",
	"float32",
	"float64",
	"bool",
	"string",
	"void",
}

var complexExamples = []string{
	`
	{
		"columns": {
			"x": "int8",
			"y": "string",
			"z": "void"
		}
	}
	`,
	`
	{
		"columns": {
			"x": "int8",
			"y": "string"
		},
		"type": "tabular"
	}
	`,
	`
	{
		"type": "image",
		"width": 120,
		"height": 240,
		"components":{
			"green": {"componentType": "uint8"},
			"red": {"componentType": "int8"},
			"black": {"componentType": "float32"},
			"blue": {"componentType": "float64"}
		}
	}
	`,
	`
	{
		"type": "image",
		"width": 120,
		"height": 240,
		"components":{
			"green": {"componentType": "uint8"},
			"red": {"componentType": "int8"},
			"black": {"componentType": "float32"},
			"blue": {"componentType": "float64"}
		},
		"format": "png"
	}
	`,
	`
	{
		"type": "tensor",
		"componentType": "string",
		"shape": [1]
	}
	`,
	`
	{
		"type": "tensor",
		"componentType": "uint8",
		"shape": [1,2,3,4,5,6,7,8]
	}
	`,
	`
	{
		"type": "nullable",
		"underlying": "float32"
	}
	`,
	`
	{
		"type": "nullable",
		"underlying": {
			"type": "tensor",
			"componentType": "int32",
			"shape": [2,3]
		}
	}`,
}

func TestFundamentalJson(t *testing.T) {
	for _, s := range primitiveSamples {
		asJson := "\"" + s + "\""
		x, err := FromJsonString(asJson)
		assert.NoError(t, err)
		assert.True(t, x.IsFundamental())
		back := ToJsonString(x)
		assert.Equal(t, asJson, back)
	}
}

func TestComplexJson(t *testing.T) {
	for _, s := range complexExamples {
		x, err := FromJsonString(s)
		assert.NoError(t, err)
		assert.False(t, x.IsFundamental())
		back := ToJsonString(x)
		x2, err := FromJsonString(back)
		assert.NoError(t, err)
		assert.Equal(t, x, x2)
	}
}

func TestToJson(t *testing.T) {
	assert.Equal(t, "\"int32\"", ToJsonString(Int32))
	b, e := FromJsonString("\"int32\"")
	assert.NoError(t, e)
	assert.Equal(t, Int32, b)
}

func TestTabularDataJson(t *testing.T) {
	sample := TabularData{}
	sample.Columns = []TabularColumn{
		TabularColumn{"x", TypeReference{Int32}},
		TabularColumn{"y", TypeReference{Float32}},
	}
	l := 3
	sample.RowCount = &l
	expected := "{\"type\":\"tabular\",\"columns\":{\"x\":\"int32\",\"y\":\"float32\"},\"rowCount\":3}"
	assert.Equal(t, expected, ToJsonString(&sample))

	back, err := FromJsonString(expected)
	assert.NoError(t, err)
	assert.Equal(t, &sample, back)
}

func TestEmptyTabularFailure(t *testing.T) {
	// this bits us multiple times, empty tables should not be supported
	json := `{}`
	_, err := FromJsonString(json)
	assert.Error(t, err)
}

func TestImageJson(t *testing.T) {
	sample := Image{
		Width:  100,
		Height: 200,
		Components: []ImageComponentElement{
			{Green, ImageComponent{TypeReference{Uint8}}},
		},
		Format: "plain",
	}
	expected := "{\"type\":\"image\",\"width\":100,\"height\":200,\"components\":{\"green\":{\"componentType\":\"uint8\"}},\"format\":\"plain\"}"
	assert.Equal(t, expected, ToJsonString(&sample))

	back, err := FromJsonString(expected)
	assert.NoError(t, err)
	assert.Equal(t, &sample, back)
}

func TestTensorJson(t *testing.T) {
	sample := Tensor{
		ComponentType: TypeReference{Uint32},
		Shape:         []int{1, 2, 3},
	}
	expected := "{\"type\":\"tensor\",\"componentType\":\"uint32\",\"shape\":[1,2,3]}"
	assert.Equal(t, expected, ToJsonString(&sample))
	back, err := FromJsonString(expected)
	assert.NoError(t, err)
	assert.Equal(t, &sample, back)
}

func TestComparison(t *testing.T) {
	x := Int32
	var y DataType
	y = x
	assert.Equal(t, x, y)
}

func TestStableOrder(t *testing.T) {
	// Bug 55
	sample :=
		`
{
      "type" : "tabular",
      "columns" : {
        "x" : "int32",
        "y" : "int32",
        "s" : "string"
      }
}
`
	table := FromJsonStringOrPanic(sample).(*TabularData)
	assert.Equal(t, table.Columns[0].Name, "x")
	assert.Equal(t, table.Columns[1].Name, "y")
	assert.Equal(t, table.Columns[2].Name, "s")

	var table2Ref TypeReference
	err := yaml.Unmarshal([]byte(sample), &table2Ref)
	assert.NoError(t, err)
	table2 := table2Ref.Underlying.(*TabularData)
	assert.Equal(t, table2.Columns[0].Name, "x")
	assert.Equal(t, table2.Columns[1].Name, "y")
	assert.Equal(t, table2.Columns[2].Name, "s")
}
