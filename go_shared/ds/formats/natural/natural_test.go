package natural

import (
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/util/serializer"
	"testing"
)

func testEncodeAndDecode(t *testing.T, bundle *element.Bundle) {
	bytes, err := EncodeBundle(bundle, serializer.BACKEND_MSGPACK)
	assert.NoError(t, err)
	back, err := DecodeBundle(serializer.BACKEND_MSGPACK, bytes)
	assert.NoError(t, err)
	assert.Equal(t, bundle, back)

	jsonBytes, err := EncodeBundle(bundle, serializer.BACKEND_JSON)

	// println("AS JSON")
	// println(string(jsonBytes))

	assert.NoError(t, err)
	jsonBack, err := DecodeBundle(serializer.BACKEND_JSON, jsonBytes)
	assert.NoError(t, err)
	assert.Equal(t, bundle, jsonBack)
}

func TestEmptyReadWrite(t *testing.T) {
	format, err := ds.FromJsonString(
		`
	{
		"columns": {
			"x": "int32"
		}
	}
			`,
	)
	assert.NoError(t, err)
	bundle := element.Bundle{
		format, []*element.TabularRow{},
	}
	testEncodeAndDecode(t, &bundle)
}

func TestSimple(t *testing.T) {
	format, err := ds.FromJsonString(
		`
	{
		"columns": {
			"x": "int32"
		}
	}
			`,
	)
	assert.NoError(t, err)
	bundle := element.Bundle{
		format, []*element.TabularRow{
			{
				[]element.Element{element.Primitive{int32(4)}},
			},
			{
				[]element.Element{element.Primitive{int32(2)}},
			},
			{
				[]element.Element{element.Primitive{int32(-2)}},
			},
		},
	}
	testEncodeAndDecode(t, &bundle)
}

func TestMultiType(t *testing.T) {
	format, err := ds.FromJsonString(
		`
	{
		"columns": {
			"x": "int32",
			"s": "string"
		}
	}
			`,
	)
	assert.NoError(t, err)
	bundle := element.Bundle{
		format, []*element.TabularRow{
			{
				[]element.Element{element.Primitive{int32(4)}, element.Primitive{"Hello"}},
			},
			{
				[]element.Element{element.Primitive{int32(2)}, element.Primitive{"World"}},
			},
			{
				[]element.Element{element.Primitive{int32(-2)}, element.Primitive{""}},
			},
		},
	}
	testEncodeAndDecode(t, &bundle)
}

func TestAllPrimitiveTypes(t *testing.T) {
	format, err := ds.FromJsonString(
		`
	{
		"columns": {
			"a": "int8",
            "b": "uint8",
			"c": "int32",
            "d": "uint32",
            "e": "int64",
            "f": "uint64",
            "g": "float32",
            "h": "float64",
            "i": "string",
            "j": "bool",
			"k": "bool",
			"l": "void"
		}
	}
			`,
	)
	assert.NoError(t, err)
	bundle := element.Bundle{
		format, []*element.TabularRow{
			{
				[]element.Element{
					element.Primitive{int8(-4)},
					element.Primitive{uint8(4)},
					element.Primitive{int32(-345458)},
					element.Primitive{uint32(54385348)},
					element.Primitive{int64(-383458435834)},
					element.Primitive{uint64(3453434468368865386)},
					element.Primitive{float32(-4.5)},
					element.Primitive{float64(43543545354555.35)},
					element.Primitive{string("Hello World")},
					element.Primitive{true},
					element.Primitive{false},
					element.Primitive{nil},
				},
			},
		},
	}
	testEncodeAndDecode(t, &bundle)
}

func TestImage(t *testing.T) {
	format, err := ds.FromJsonString(
		`
	{
		"columns": {
			"i": {
				"type": "image",
				"components": {
					"black": {
					  "componentType": "uint8"  
					}
				},
				"width": 2,
				"height": 3
			}
		}
	}
			`,
	)
	assert.NoError(t, err)
	bundle := element.Bundle{
		format, []*element.TabularRow{
			{
				[]element.Element{
					&element.ImageElement{
						[]byte("ABCDEF"),
					},
				},
			},
		},
	}
	testEncodeAndDecode(t, &bundle)
}

func TestTensor(t *testing.T) {
	format, err := ds.FromJsonString(
		`
	{
		"columns": {
			"t": {
              "type": "tensor",
              "shape": [2,3],
			  "componentType": "float32"
			}
		}
	}
			`,
	)
	assert.NoError(t, err)
	bundle := element.Bundle{
		format, []*element.TabularRow{
			{
				[]element.Element{
					&element.TensorElement{
						[]float32{2.3, -2.0, 5.0, 0.0, -3.0, 5.0},
					},
				},
			},
		},
	}
	testEncodeAndDecode(t, &bundle)
}

func TestEmbeddedTabular(t *testing.T) {
	format, err := ds.FromJsonString(
		`
	{
		"columns": {
			"t": {
              "type": "tabular",
              "columns": {
			    "a": "int32",
                "b": "string"
              }
			}
		}
	}
			`,
	)
	assert.NoError(t, err)
	bundle := element.Bundle{
		format, []*element.TabularRow{
			{
				[]element.Element{
					&element.EmbeddedTabularElement{
						[]*element.TabularRow{
							&element.TabularRow{
								[]element.Element{element.Primitive{int32(4)}, element.Primitive{"Hello"}},
							},
							&element.TabularRow{
								[]element.Element{element.Primitive{int32(-1)}, element.Primitive{"World"}},
							},
						},
					},
				},
			},
			{
				[]element.Element{
					&element.EmbeddedTabularElement{
						[]*element.TabularRow{}, // empty row
					},
				},
			},
		},
	}
	testEncodeAndDecode(t, &bundle)
}
