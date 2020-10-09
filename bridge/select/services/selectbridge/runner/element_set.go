package runner

import (
	"bytes"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/formats/natural"
	"gl.ambrosys.de/mantik/go_shared/ds/util/serializer"
)

// A Special set of elements which are all from the same type
// Not thread safe
// Used for Distinct generation
type ElementSet struct {
	serializer natural.ElementSerializer
	exists     byteSliceSet
	size       int
}

func NewElementSet(dt ds.DataType) (*ElementSet, error) {
	serializer, err := natural.LookupRootElementSerializer(dt)
	if err != nil {
		return nil, err
	}
	return &ElementSet{
		serializer: serializer,
		exists:     newByteSliceSet(),
	}, nil
}

// Returns true if element was added, false if already existing
func (e *ElementSet) Add(element element.Element) (bool, error) {
	encoded, err := e.encode(element)
	if err != nil {
		return false, err
	}
	added := e.exists.Add(encoded)
	if added {
		e.size += 1
	}
	return added, nil
}

func (e *ElementSet) Size() int {
	return e.size
}

func (e *ElementSet) Clear() {
	e.size = 0
	e.exists = newByteSliceSet()
}

func (e *ElementSet) encode(element element.Element) ([]byte, error) {
	buf := bytes.Buffer{}
	backend, err := serializer.CreateSerializingBackend(serializer.BACKEND_MSGPACK, &buf)
	if err != nil {
		return nil, err
	}
	err = e.serializer.Write(backend, element)
	backend.Flush()
	return buf.Bytes(), err
}

// A Simple set for byteSlices
type byteSliceSet struct {
	elements map[int][][]byte
}

func newByteSliceSet() byteSliceSet {
	return byteSliceSet{make(map[int][][]byte)}
}

// Adds an element to the byte slice set, returns true if it was not existing yet
func (b *byteSliceSet) Add(e []byte) bool {
	h := hash(e)
	rows, exists := b.elements[h]
	if !exists {
		b.elements[h] = [][]byte{e}
		return true
	} else {
		for _, row := range rows {
			if bytes.Equal(e, row) {
				return false
			}
		}
		b.elements[h] = append(rows, e)
		return true
	}
}

func hash(b []byte) int {
	x := 1
	for _, b := range b {
		x = (x * 31) + (int)(b)
	}
	return x
}
