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
package serializer

import (
	"bytes"
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/stretchr/testify/assert"
	"github.com/vmihailenco/msgpack"
	"testing"
)

func TestMsgPackJsonSerialization(t *testing.T) {
	samples := []ds.DataType{
		ds.Int32,
		ds.String,
		&ds.TabularData{
			Columns: ds.NewNamedDataTypeMap(
				ds.NamedType{"x", ds.Ref(ds.Int32)},
			),
		},
	}
	for _, sample := range samples {
		buf := bytes.NewBuffer([]byte{})
		encoder := msgpack.NewEncoder(buf)
		backend := msgPackSerializingBackend{Encoder: encoder}
		err := backend.EncodeJson(ds.Ref(sample))
		assert.NoError(t, err)

		decoder := msgpack.NewDecoder(buf)
		decoderBackend := msgPackDeserializingBackend{Decoder: decoder}
		var back ds.TypeReference
		err = decoderBackend.DecodeJson(&back)
		assert.NoError(t, err)
		assert.Equal(t, ds.Ref(sample), back)
	}
}

func TestRawJsonSupport(t *testing.T) {
	samples := []string{
		`1`, `-1`, `0`, `0.5`, `-0.5`, `[1,2,3,4,5]`, `[]`, `{}`, `{"x":"y"}`, `{"head":{"x":["y"]}}`, `null`, `true`, `false`,
	}
	for _, sample := range samples {
		buf := bytes.NewBuffer([]byte{})
		encoder := msgpack.NewEncoder(buf)
		backend := msgPackSerializingBackend{encoder}
		err := backend.EncodeRawJson([]byte(sample))
		assert.NoError(t, err)

		decoder := msgpack.NewDecoder(buf)
		decoderBackend := msgPackDeserializingBackend{decoder}
		back, err := decoderBackend.DecodeRawJson()
		assert.NoError(t, err)
		assert.Equal(t, sample, string(back))
	}
}

func TestFloatFromDouble(t *testing.T) {
	// Bug #61, python is serializing float32 to float64 values. This should not crash parsing in go.
	data := []byte{0xCB, 0x40, 0x54, 0x7F, 0xA9, 0x80, 0x00, 0x00, 0x00}
	decoder, err := CreateDeserializingBackend(BACKEND_MSGPACK, bytes.NewReader(data))
	assert.NoError(t, err)
	x, err := decoder.DecodeFloat32()
	assert.NoError(t, err)
	assert.Equal(t, float32(81.9947204589844), x)

}
