package runner

import (
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/formats/natural"
)

type ElementMultiHashMap struct {
	keySerializer natural.ElementSerializer
	m             map[string][]*element.TabularRow
	size          int
}

func NewElementMultiHashMap(key *ds.TabularData) (*ElementMultiHashMap, error) {
	serializer, err := natural.LookupRootElementSerializer(key)
	if err != nil {
		return nil, err
	}
	return &ElementMultiHashMap{
		keySerializer: serializer,
		m:             make(map[string][]*element.TabularRow),
	}, nil
}

func (e *ElementMultiHashMap) Add(key *element.TabularRow, value *element.TabularRow) {
	keyBytes, err := natural.SerializeToBytes(e.keySerializer, key)
	if err != nil {
		panic("Could not serialize keys")
	}
	keyString := string(keyBytes)
	e.m[keyString] = append(e.m[keyString], value)
	e.size += 1
}

func (e *ElementMultiHashMap) Get(key *element.TabularRow) []*element.TabularRow {
	keyBytes, err := natural.SerializeToBytes(e.keySerializer, key)
	if err != nil {
		panic("Could not serialize keys")
	}
	keyString := string(keyBytes)
	return e.m[keyString]
}

func (e *ElementMultiHashMap) Gets(keyString string) []*element.TabularRow {
	return e.m[keyString]
}

func (e *ElementMultiHashMap) ForEach(f func(string, []*element.TabularRow) error) error {
	for k, values := range e.m {
		err := f(k, values)
		if err != nil {
			return err
		}
	}
	return nil
}

func (e *ElementMultiHashMap) Size() int {
	return e.size
}
