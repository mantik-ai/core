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

	Compare(left interface{}, right interface{}) int
	CompareArray(left interface{}, right interface{}) int
}

var badType = errors.New("Unexpected type")

//go:generate sh -c "go run gen/fundamentals.go > fundamentals_generated.go"

func (c boolCodec) Compare(left interface{}, right interface{}) int {
	lv := left.(bool)
	rv := right.(bool)
	if !lv && rv {
		return -1
	} else if lv && !rv {
		return 1
	} else {
		return 0
	}
}
