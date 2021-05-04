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
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestArraySerializing(t *testing.T) {
	buf := bytes.Buffer{}
	s, _ := CreateSerializingBackend(BACKEND_JSON, &buf)
	s.EncodeArrayLen(2)
	s.EncodeInt32(1)
	s.EncodeInt32(2)
	assert.Equal(t, "[1,2]", string(buf.Bytes()))
}

func TestEmptyArray(t *testing.T) {
	buf := bytes.Buffer{}
	s, _ := CreateSerializingBackend(BACKEND_JSON, &buf)
	s.EncodeArrayLen(1)
	s.EncodeArrayLen(0)
	assert.Equal(t, "[[]]", string(buf.Bytes()))
}

func TestDeepArraySerializing(t *testing.T) {
	buf := bytes.Buffer{}
	s, _ := CreateSerializingBackend(BACKEND_JSON, &buf)
	s.EncodeArrayLen(3)
	s.EncodeInt32(1)
	s.EncodeArrayLen(3)
	s.EncodeInt32(2)
	s.EncodeInt32(3)
	s.EncodeInt32(4)
	s.EncodeInt32(5)
	assert.Equal(t, "[1,[2,3,4],5]", string(buf.Bytes()))
}

func TestDeepArraySerializing2(t *testing.T) {
	buf := bytes.Buffer{}
	s, _ := CreateSerializingBackend(BACKEND_JSON, &buf)
	s.EncodeArrayLen(3)
	s.EncodeInt32(1)
	s.EncodeInt32(2)
	s.EncodeArrayLen(3)
	s.EncodeInt32(3)
	s.EncodeInt32(4)
	s.EncodeInt32(5)
	assert.Equal(t, "[1,2,[3,4,5]]", string(buf.Bytes()))
}

func TestDeepArraySerializing3(t *testing.T) {
	buf := bytes.Buffer{}
	s, _ := CreateSerializingBackend(BACKEND_JSON, &buf)
	s.EncodeArrayLen(2)
	s.EncodeInt32(1)
	s.EncodeArrayLen(2)
	s.EncodeInt32(2)
	s.EncodeArrayLen(2)
	s.EncodeInt32(3)
	s.EncodeInt32(4)
	assert.Equal(t, "[1,[2,[3,4]]]", string(buf.Bytes()))
}
