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
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/util"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/util/serializer"
	"testing"
)

func encodeBundle(t *testing.T, contentType string, bundle element.Bundle) *bytes.Buffer {
	encoder, err := NewStreamEncoder(contentType, bundle.Type)
	assert.NoError(t, err)

	buf := util.NewClosableBuffer()
	streamWriter := encoder.StartEncoding(buf)
	for _, r := range bundle.Rows {
		err = streamWriter.Write(r)
		assert.NoError(t, err)
	}
	err = streamWriter.Close()
	assert.NoError(t, err)
	return &buf.Buffer
}

func TestStreamEncoderJson(t *testing.T) {
	buf := encodeBundle(t, MimeJson, emptyTabularValue)
	back, err := DecodeBundleValue(tabularExample, serializer.BACKEND_JSON, buf)
	assert.NoError(t, err)
	assert.Equal(t, emptyTabularValue.Type, back.Type)
	assert.Empty(t, back.Rows)

	buf = encodeBundle(t, MimeJson, tabularValue)

	back, err = DecodeBundleValue(tabularExample, serializer.BACKEND_JSON, buf)
	assert.NoError(t, err)

	assert.Equal(t, &tabularValue, back)

	buf = encodeBundle(t, MimeJson, fundamentalValue)

	back, err = DecodeBundleValue(fundamental, serializer.BACKEND_JSON, buf)
	assert.NoError(t, err)

	assert.Equal(t, &fundamentalValue, back)
}

func TestStreamEncoderMsgPack(t *testing.T) {
	buf := encodeBundle(t, MimeMsgPack, emptyTabularValue)
	back, err := DecodeBundleValue(tabularExample, serializer.BACKEND_MSGPACK, buf)
	assert.NoError(t, err)
	assert.Equal(t, emptyTabularValue.Type, back.Type)
	assert.Empty(t, back.Rows)

	buf = encodeBundle(t, MimeMsgPack, tabularValue)

	back, err = DecodeBundleValue(tabularExample, serializer.BACKEND_MSGPACK, buf)
	assert.NoError(t, err)

	assert.Equal(t, &tabularValue, back)

	buf = encodeBundle(t, MimeMsgPack, fundamentalValue)

	back, err = DecodeBundleValue(fundamental, serializer.BACKEND_MSGPACK, buf)
	assert.NoError(t, err)

	assert.Equal(t, &fundamentalValue, back)
}

func TestStreamEncoderJsonBundle(t *testing.T) {
	buf := encodeBundle(t, MimeMantikBundleJson, emptyTabularValue)
	back, err := DecodeBundleFromReader(serializer.BACKEND_JSON, buf)

	assert.NoError(t, err)
	assert.Equal(t, emptyTabularValue.Type, back.Type)
	assert.Empty(t, back.Rows)

	buf = encodeBundle(t, MimeMantikBundleJson, tabularValue)

	back, err = DecodeBundleFromReader(serializer.BACKEND_JSON, buf)
	assert.NoError(t, err)

	assert.Equal(t, &tabularValue, back)

	buf = encodeBundle(t, MimeMantikBundleJson, fundamentalValue)

	back, err = DecodeBundleFromReader(serializer.BACKEND_JSON, buf)
	assert.NoError(t, err)

	assert.Equal(t, &fundamentalValue, back)
}

func TestStreamEncoderMsgPackBundle(t *testing.T) {
	buf := encodeBundle(t, MimeMantikBundle, emptyTabularValue)
	back, err := DecodeBundleFromReader(serializer.BACKEND_MSGPACK, buf)
	assert.NoError(t, err)
	assert.Equal(t, emptyTabularValue.Type, back.Type)
	assert.Empty(t, back.Rows)

	buf = encodeBundle(t, MimeMantikBundle, tabularValue)

	back, err = DecodeBundleFromReader(serializer.BACKEND_MSGPACK, buf)
	assert.NoError(t, err)
	assert.Equal(t, &tabularValue, back)

	buf = encodeBundle(t, MimeMantikBundle, fundamentalValue)

	back, err = DecodeBundleFromReader(serializer.BACKEND_MSGPACK, buf)
	assert.NoError(t, err)

	assert.Equal(t, &fundamentalValue, back)
}
