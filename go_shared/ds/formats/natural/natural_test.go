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
package natural

import (
	"bytes"
	"encoding/json"
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/element/builder"
	"gl.ambrosys.de/mantik/go_shared/ds/util/serializer"
	"reflect"
	"testing"
)

func testEncodeAndDecode(t *testing.T, bundle *element.Bundle) {
	bytes, err := EncodeBundle(bundle, serializer.BACKEND_MSGPACK)
	assert.NoError(t, err)
	back, err := DecodeBundle(serializer.BACKEND_MSGPACK, bytes)
	assert.NoError(t, err)
	assert.Equal(t, bundle, back)

	jsonBytes, err := EncodeBundle(bundle, serializer.BACKEND_JSON)

	println("AS JSON")
	println(string(jsonBytes))

	assert.NoError(t, err)
	jsonBack, err := DecodeBundle(serializer.BACKEND_JSON, jsonBytes)
	assert.NoError(t, err)
	assert.Equal(t, bundle, jsonBack)
}

func testComparator(t *testing.T, bundle *element.Bundle) {
	comparator, err := LookupComparator(bundle.Type)
	assert.NoError(t, err)
	if bundle.IsTabular() {
		for _, row := range bundle.Rows {
			assert.Equal(t, 0, comparator.Compare(row, row))
		}
	} else {
		assert.Equal(t, 0, comparator.Compare(bundle.SingleValue(), bundle.SingleValue()))
	}
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
		format, []element.Element{},
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
	bundle := builder.Bundle(
		format,
		builder.Row(element.Primitive{int32(4)}),
		builder.Row(element.Primitive{int32(2)}),
		builder.Row(element.Primitive{int32(-2)}),
	)
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
	bundle := builder.Bundle(
		format,
		builder.Row(element.Primitive{int32(4)}, element.Primitive{"Hello"}),
		builder.Row(element.Primitive{int32(2)}, element.Primitive{"World"}),
		builder.Row(element.Primitive{int32(-2)}, element.Primitive{""}),
	)
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
	bundle := builder.Bundle(
		format, builder.Row(
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
		),
	)
	testEncodeAndDecode(t, &bundle)
	testComparator(t, &bundle)
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
	bundle := builder.Bundle(
		format, builder.Row(&element.ImageElement{
			[]byte("ABCDEF"),
		},
		),
	)
	testEncodeAndDecode(t, &bundle)
	testComparator(t, &bundle)
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
	bundle := builder.Bundle(
		format, builder.Row(
			&element.TensorElement{
				[]float32{2.3, -2.0, 5.0, 0.0, -3.0, 5.0},
			},
		),
	)
	testEncodeAndDecode(t, &bundle)
	testComparator(t, &bundle)
}

func TestNullablePrimitive(t *testing.T) {
	format := ds.BuildTabular().Add(
		"x", &ds.Nullable{
			ds.Ref(ds.Float32),
		},
	).Result()
	bundle := builder.Bundle(
		format,
		builder.Row(
			element.Primitive{float32(100)},
		),
		builder.Row(
			element.Primitive{nil},
		),
	)
	testEncodeAndDecode(t, &bundle)
	testComparator(t, &bundle)
}

func TestArray(t *testing.T) {
	format := ds.BuildTabular().Add(
		"x", &ds.Array{ds.Ref(ds.Int32)},
	).Result()
	bundle := builder.Bundle(
		format,
		builder.Row(
			&element.ArrayElement{Elements: []element.Element{element.Primitive{int32(100)}, element.Primitive{int32(200)}}},
		),
		builder.Row(
			&element.ArrayElement{Elements: []element.Element{}},
		),
	)
	testEncodeAndDecode(t, &bundle)
	testComparator(t, &bundle)
}

func TestStruct(t *testing.T) {
	format := ds.BuildTabular().Add(
		"x", &ds.Struct{ds.NewNamedDataTypeMap(
			ds.NamedType{"name", ds.Ref(ds.String)},
			ds.NamedType{"age", ds.Ref(&ds.Nullable{ds.Ref(ds.Int32)})},
		)},
	).Result()

	bundle := builder.Bundle(
		format,
		builder.Row(
			&element.StructElement{[]element.Element{element.Primitive{"Alice"}, element.Primitive{int32(42)}}},
		),
		builder.Row(
			&element.StructElement{[]element.Element{element.Primitive{"Bob"}, element.Primitive{nil}}},
		),
	)
	testEncodeAndDecode(t, &bundle)
	testComparator(t, &bundle)
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
	bundle := builder.Bundle(
		format, builder.Row(
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
		),
		builder.Row(
			&element.EmbeddedTabularElement{
				[]*element.TabularRow{}, // empty row
			},
		),
	)
	testEncodeAndDecode(t, &bundle)
	testComparator(t, &bundle)
}

func TestJsonEncodingForPrimitives(t *testing.T) {
	sample := `
		{"type":"int32", "value":100}
	`
	bundle, err := DecodeBundle(serializer.BACKEND_JSON, []byte(sample))
	assert.NoError(t, err)
	assert.Equal(t, ds.Int32, bundle.Type)
	assert.Equal(t, int32(100), bundle.GetSinglePrimitive())

	encoded, err := EncodeBundle(bundle, serializer.BACKEND_JSON)
	assert.NoError(t, err)
	assert.True(t, compareJson([]byte(sample), encoded))
}

func TestEncodingTabular(t *testing.T) {
	sample := `
		{"type":{"type":"tabular", "columns": {"x": "int32", "y": "string"}},"value": [[1,"Hello"], [2,"World"]]}
	`
	bundle, err := DecodeBundle(serializer.BACKEND_JSON, []byte(sample))
	assert.NoError(t, err)
	assert.Equal(t, int32(1), bundle.GetTabularPrimitive(0, 0))
	assert.Equal(t, "Hello", bundle.GetTabularPrimitive(0, 1))
	assert.Equal(t, int32(2), bundle.GetTabularPrimitive(1, 0))
	assert.Equal(t, "World", bundle.GetTabularPrimitive(1, 1))

	encoded, err := EncodeBundle(bundle, serializer.BACKEND_JSON)
	assert.True(t, compareJson([]byte(sample), encoded))
}

func TestEncodeBundleRef(t *testing.T) {
	sample := BundleRef{
		builder.PrimitiveBundle(ds.Int32, element.Primitive{int32(4)}),
	}
	bytes, err := json.Marshal(sample)
	assert.NoError(t, err)
	assert.Equal(t, `{"type":"int32","value":4}`, string(bytes))
	bytes2, err := json.Marshal(&sample)
	assert.Equal(t, bytes, bytes2)
	var back BundleRef
	err = json.Unmarshal(bytes, &back)
	assert.NoError(t, err)
	assert.Equal(t, sample, back)
}

// Returns true, if both JSONs are equal
func compareJson(a []byte, b []byte) bool {
	// trick: https://gist.github.com/turtlemonvh/e4f7404e28387fadb8ad275a99596f67
	var aDecoded interface{}
	var bDecoded interface{}
	err := json.Unmarshal(a, &aDecoded)
	if err != nil {
		panic(err.Error())
	}
	err = json.Unmarshal(b, &bDecoded)
	if err != nil {
		panic(err.Error())
	}
	return reflect.DeepEqual(aDecoded, bDecoded)
}

func TestEncodeSingleValue(t *testing.T) {
	b := builder.PrimitiveBundle(ds.Int32, element.Primitive{int32(5)})
	r, err := EncodeBundleValue(&b, serializer.BACKEND_JSON)
	assert.NoError(t, err)
	assert.Equal(t, []byte("5"), r)
}

func TestDecodeSingleValue(t *testing.T) {
	sample := bytes.NewBufferString("5")
	r, err := DecodeBundleValue(ds.Int32, serializer.BACKEND_JSON, sample)
	assert.NoError(t, err)
	assert.Equal(t, ds.Int32, r.Type)
	assert.Equal(t, int32(5), r.GetSinglePrimitive())
}

func TestDecodeSingleTabularValue(t *testing.T) {
	sample := bytes.NewBufferString(`[[1,"Hello"],[2, "World"]]`)
	tt := ds.BuildTabular().Add("x", ds.Int32).Add("y", ds.String).Result()
	r, err := DecodeBundleValue(tt, serializer.BACKEND_JSON, sample)
	assert.NoError(t, err)
	assert.Equal(t, tt, r.Type)
	assert.Equal(t, 2, len(r.Rows))
	assert.Equal(t, int32(1), r.GetTabularPrimitive(0, 0))
	assert.Equal(t, int32(2), r.GetTabularPrimitive(1, 0))
	assert.Equal(t, "Hello", r.GetTabularPrimitive(0, 1))
	assert.Equal(t, "World", r.GetTabularPrimitive(1, 1))
}

func TestEncodeTabularValue(t *testing.T) {
	sample := `
		{"type":{"type":"tabular", "columns": {"x": "int32", "y": "string"}},"value": [[1,"Hello"], [2,"World"]]}
	`
	bundle, err := DecodeBundle(serializer.BACKEND_JSON, []byte(sample))
	assert.NoError(t, err)
	r, err := EncodeBundleValue(bundle, serializer.BACKEND_JSON)
	assert.Equal(t, []byte(`[[1,"Hello"],[2,"World"]]`), r)
}
