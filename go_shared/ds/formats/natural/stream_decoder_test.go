package natural

import (
	"bytes"
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/element/builder"
	"gl.ambrosys.de/mantik/go_shared/ds/util/serializer"
	"io"
	"testing"
)

var tabularExample = ds.BuildTabular().Add("x", ds.Int32).Result()
var fundamental = ds.Float32

var tabularValue = builder.Bundle(
	tabularExample,
	builder.PrimitiveRow(int32(1)),
	builder.PrimitiveRow(int32(2)),
)

var fundamentalValue = builder.PrimitiveBundle(
	fundamental,
	element.Primitive{X: float32(1.5)},
)

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
	tableExample(t, decoder, bytes.NewBufferString("[[1],[2]]"))

	decoder2, err := NewStreamDecoder(MimeJson, fundamental)
	assert.NoError(t, err)
	primitiveExample(t, decoder2, bytes.NewBufferString("1.5"))
}

func TestStreamDecoderMsgPack(t *testing.T) {
	decoder, err := NewStreamDecoder(MimeMsgPack, tabularExample)
	assert.NoError(t, err)
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
	encoded, err := EncodeBundle(&tabularValue, serializer.BACKEND_MSGPACK)
	assert.NoError(t, err)

	tableExample(t, decoder, bytes.NewBuffer(encoded))

	decoder2, err := NewStreamDecoder(MimeMantikBundle, fundamental)
	encoded, err = EncodeBundle(&fundamentalValue, serializer.BACKEND_MSGPACK)
	assert.NoError(t, err)
	primitiveExample(t, decoder2, bytes.NewBuffer(encoded))
}
