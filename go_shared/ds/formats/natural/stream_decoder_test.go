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
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/mantik-ai/core/go_shared/ds/element"
	"github.com/mantik-ai/core/go_shared/ds/element/builder"
	"github.com/mantik-ai/core/go_shared/ds/util/serializer"
	"github.com/stretchr/testify/assert"
	"io"
	"testing"
)

var tabularExample = ds.BuildTabular().Add("x", ds.Int32).Result()
var fundamental = ds.Float32

var emptyTabularValue = builder.Bundle(
	tabularExample,
)

var tabularValue = builder.Bundle(
	tabularExample,
	builder.PrimitiveRow(int32(1)),
	builder.PrimitiveRow(int32(2)),
)

var fundamentalValue = builder.PrimitiveBundle(
	fundamental,
	element.Primitive{X: float32(1.5)},
)

func emptyTableExample(t *testing.T, decoder StreamDecoder, table *bytes.Buffer) {
	reader := decoder.StartDecoding(table)
	_, err := reader.Read()
	assert.Equal(t, io.EOF, err)
}

func tableExample(t *testing.T, decoder StreamDecoder, table *bytes.Buffer) {
	reader := decoder.StartDecoding(table)
	value, err := reader.Read()
	assert.Equal(t, tabularValue.Rows[0], value)
	value, err = reader.Read()
	assert.NoError(t, err)
	assert.Equal(t, tabularValue.Rows[1], value)
	_, err = reader.Read()
	assert.Equal(t, io.EOF, err)
}

func primitiveExample(t *testing.T, decoder StreamDecoder, table *bytes.Buffer) {
	reader := decoder.StartDecoding(table)
	value, err := reader.Read()
	assert.Equal(t, fundamentalValue.Rows[0], value)
	value, err = reader.Read()
	assert.Equal(t, io.EOF, err)
}

func TestStreamDecoderJson(t *testing.T) {
	decoder, err := NewStreamDecoder(MimeJson, tabularExample)
	assert.NoError(t, err)
	emptyTableExample(t, decoder, bytes.NewBufferString("[]"))
	tableExample(t, decoder, bytes.NewBufferString("[[1],[2]]"))

	decoder2, err := NewStreamDecoder(MimeJson, fundamental)
	assert.NoError(t, err)
	primitiveExample(t, decoder2, bytes.NewBufferString("1.5"))
}

func TestStreamDecoderMsgPack(t *testing.T) {
	decoder, err := NewStreamDecoder(MimeMsgPack, tabularExample)
	assert.NoError(t, err)

	encodedEmpty, err := EncodeBundleValue(&emptyTabularValue, serializer.BACKEND_MSGPACK)
	assert.NoError(t, err)
	emptyTableExample(t, decoder, bytes.NewBuffer(encodedEmpty))

	encoded, err := EncodeBundleValue(&tabularValue, serializer.BACKEND_MSGPACK)
	assert.NoError(t, err)

	tableExample(t, decoder, bytes.NewBuffer(encoded))

	decoder2, err := NewStreamDecoder(MimeMsgPack, fundamental)
	encoded, err = EncodeBundleValue(&fundamentalValue, serializer.BACKEND_MSGPACK)
	assert.NoError(t, err)
	primitiveExample(t, decoder2, bytes.NewBuffer(encoded))
}

func TestStreamDecoderJsonWithHeader(t *testing.T) {
	decoder, err := NewStreamDecoder(MimeMantikBundleJson, tabularExample)
	assert.NoError(t, err)

	encodedEmpty, err := EncodeBundle(&emptyTabularValue, serializer.BACKEND_JSON)

	assert.NoError(t, err)
	emptyTableExample(t, decoder, bytes.NewBuffer(encodedEmpty))

	encoded, err := EncodeBundle(&tabularValue, serializer.BACKEND_JSON)
	assert.NoError(t, err)

	tableExample(t, decoder, bytes.NewBuffer(encoded))

	decoder2, err := NewStreamDecoder(MimeMantikBundleJson, fundamental)
	encoded, err = EncodeBundle(&fundamentalValue, serializer.BACKEND_JSON)
	assert.NoError(t, err)
	primitiveExample(t, decoder2, bytes.NewBuffer(encoded))
}

func TestStreamDecoderMsgPackWithHeader(t *testing.T) {
	decoder, err := NewStreamDecoder(MimeMantikBundle, tabularExample)
	assert.NoError(t, err)

	encodedEmpty, err := EncodeBundle(&emptyTabularValue, serializer.BACKEND_MSGPACK)
	assert.NoError(t, err)
	emptyTableExample(t, decoder, bytes.NewBuffer(encodedEmpty))

	encoded, err := EncodeBundle(&tabularValue, serializer.BACKEND_MSGPACK)
	assert.NoError(t, err)

	tableExample(t, decoder, bytes.NewBuffer(encoded))

	decoder2, err := NewStreamDecoder(MimeMantikBundle, fundamental)
	encoded, err = EncodeBundle(&fundamentalValue, serializer.BACKEND_MSGPACK)
	assert.NoError(t, err)
	primitiveExample(t, decoder2, bytes.NewBuffer(encoded))
}
