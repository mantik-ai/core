package serializer

import (
	"bufio"
	"github.com/pkg/errors"
	"github.com/vmihailenco/msgpack"
	"io"
)

type BackendType = int

const BACKEND_MSGPACK BackendType = 1
const BACKEND_JSON BackendType = 2

func CreateSerializingBackend(backendType BackendType, destination io.Writer) (SerializingBackend, error) {
	switch backendType {
	case BACKEND_MSGPACK:
		encoder := msgpack.NewEncoder(destination)
		return &msgPackSerializingBackend{Encoder: *encoder}, nil
	case BACKEND_JSON:
		return &jsonSerializer{destination, nil}, nil
	default:
		return nil, errors.New("Unsupported backend")
	}
}

func CreateDeserializingBackend(backendType BackendType, reader io.Reader) (DeserializingBackend, error) {
	switch backendType {
	case BACKEND_MSGPACK:
		return &msgPackDeserializingBackend{
			*msgpack.NewDecoder(reader),
		}, nil
	case BACKEND_JSON:
		return &jsonDeserializer{bufio.NewReader(reader), nil, 0}, nil
	default:
		return nil, errors.Errorf("Unsupported backend %d", backendType)
	}
}

// Custom serializing backend, should make it possible to write a JSON Serializer too
// The interface is compatible to vmihailenco/msgpack with some extensions

type SerializingBackend interface {
	EncodeArrayLen(l int) error
	EncodeJson(i interface{}) error
	// Methods are like in msgpack.Encoder (for automatic deriving)
	EncodeInt8(v int8) error
	EncodeUint8(v uint8) error
	EncodeInt32(v int32) error
	EncodeUint32(v uint32) error
	EncodeInt64(v int64) error
	EncodeUint64(v uint64) error
	EncodeString(s string) error
	EncodeFloat32(f float32) error
	EncodeFloat64(f float64) error
	EncodeBool(b bool) error
	EncodeBytes(bytes []byte) error
	EncodeNil() error
	Flush() error
}

type DeserializingBackend interface {
	DecodeArrayLen() (int, error)
	DecodeJson(destination interface{}) error
	DecodeInt8() (int8, error)
	DecodeUint8() (uint8, error)
	DecodeInt32() (int32, error)
	DecodeUint32() (uint32, error)
	DecodeInt64() (int64, error)
	DecodeUint64() (uint64, error)
	DecodeString() (string, error)
	DecodeFloat32() (float32, error)
	DecodeFloat64() (float64, error)
	DecodeBool() (bool, error)
	DecodeBytes() ([]byte, error)
	DecodeNil() error
}
