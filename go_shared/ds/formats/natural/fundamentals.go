package natural

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds/util/serializer"
)

type FundamentalCodec interface {
	Write(backend serializer.SerializingBackend, value interface{}) error
	WriteArray(backend serializer.SerializingBackend, value interface{}) error

	Read(backend serializer.DeserializingBackend) (interface{}, error)
	ReadArray(backend serializer.DeserializingBackend) (interface{}, error)
}

var badType = errors.New("Unexpected type")

//go:generate sh -c "go run gen/fundamentals.go > fundamentals_generated.go"
