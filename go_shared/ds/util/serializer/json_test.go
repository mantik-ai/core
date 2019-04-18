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
