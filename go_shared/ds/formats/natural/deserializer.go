package natural

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/util/serializer"
)

type ElementDeserializer interface {
	Read(backend serializer.DeserializingBackend) (element.Element, error)
}

/** Read from internal representation. (Also see SerializeToBytes) */
func DeserializeFromBytes(ed ElementDeserializer, bytes []byte) (element.Element, error) {
	backend, err := serializer.CreateDeserializingBackendForBytes(serializer.BACKEND_MSGPACK, bytes)
	if err != nil {
		return nil, err
	}
	return ed.Read(backend)
}

type nilDeserializer struct{}

func (n *nilDeserializer) Read(backend serializer.DeserializingBackend) (element.Element, error) {
	err := backend.DecodeNil()
	return element.Primitive{nil}, err
}

func LookupRootElementDeserializer(dataType ds.DataType) (ElementDeserializer, error) {
	tabularData, ok := dataType.(*ds.TabularData)
	if !ok {
		// deserialize a single element
		return lookupElementDeserializer(dataType)
	}
	trds, err := newTabularRowDeserializer(*tabularData)
	if err != nil {
		return nil, err
	}
	return trds, nil
}

func lookupElementDeserializer(dataType ds.DataType) (ElementDeserializer, error) {
	if dataType == ds.Void {
		return &nilDeserializer{}, nil
	}
	if dataType.IsFundamental() {
		return lookupFundamentalElementDeserializer(dataType)
	}
	imageType, ok := dataType.(*ds.Image)
	if ok {
		return imageDeserializer{imageType}, nil
	}
	tensorType, ok := dataType.(*ds.Tensor)
	if ok {
		codec, err := GetFundamentalCodec(tensorType.ComponentType.Underlying)
		if err != nil {
			return nil, err
		}
		return tensorDeserializer{tensorType, codec}, nil
	}
	embeddedTabularType, ok := dataType.(*ds.TabularData)
	if ok {
		subDeserializer, err := newTabularRowDeserializer(*embeddedTabularType)
		if err != nil {
			return nil, err
		}
		return embeddedTabularDeserializer{subDeserializer}, nil
	}
	nullable, ok := dataType.(*ds.Nullable)
	if ok {
		subDeserializer, err := lookupElementDeserializer(nullable.Underlying.Underlying)
		if err != nil {
			return nil, err
		}
		return &nullableDeserializer{subDeserializer}, nil
	}
	array, ok := dataType.(*ds.Array)
	if ok {
		subDeserializer, err := lookupElementDeserializer(array.Underlying.Underlying)
		if err != nil {
			return nil, err
		}
		return &arrayDeserializer{subDeserializer}, nil
	}
	s, ok := dataType.(*ds.Struct)
	if ok {
		subDeserializer, err := newTabularRowDeserializer(
			ds.TabularData{Columns: s.Fields},
		)
		if err != nil {
			return nil, err
		}
		return &structDeserializer{*subDeserializer}, nil
	}
	return nil, errors.Errorf("Not implemented %s", dataType.TypeName())
}

func lookupFundamentalElementDeserializer(dataType ds.DataType) (ElementDeserializer, error) {
	fc, err := GetFundamentalCodec(dataType)
	if err != nil {
		return nil, err
	}
	return fundamentalDeserializer{fc}, nil
}

type fundamentalDeserializer struct {
	fc FundamentalCodec
}

func (f fundamentalDeserializer) Read(backend serializer.DeserializingBackend) (element.Element, error) {
	v, err := f.fc.Read(backend)
	if err != nil {
		return nil, err
	}
	return element.Primitive{v}, nil
}

func newTabularRowDeserializer(data ds.TabularData) (*tableRowDeserializer, error) {
	subDecoders := make([]ElementDeserializer, len(data.Columns))
	for i, v := range data.Columns {
		subDecoder, err := lookupElementDeserializer(v.SubType.Underlying)
		if err != nil {
			return nil, err
		}
		subDecoders[i] = subDecoder
	}
	return &tableRowDeserializer{subDecoders}, nil
}

type tableRowDeserializer struct {
	subDecoders []ElementDeserializer
}

func (f tableRowDeserializer) Read(backend serializer.DeserializingBackend) (element.Element, error) {
	return f.ReadTabularRow(backend)
}

func (f tableRowDeserializer) ReadTabularRow(backend serializer.DeserializingBackend) (*element.TabularRow, error) {
	cnt, err := backend.DecodeArrayLen()
	if err != nil {
		return nil, err
	}
	if cnt != len(f.subDecoders) {
		return nil, errors.Errorf("Table column count mismatch, expected %d, got %d", len(f.subDecoders), cnt)
	}
	elements := make([]element.Element, len(f.subDecoders))
	for i := 0; i < cnt; i++ {
		elements[i], err = f.subDecoders[i].Read(backend)
		if err != nil {
			return nil, err
		}
	}
	return &element.TabularRow{elements}, nil
}

type imageDeserializer struct {
	image *ds.Image
}

func (i imageDeserializer) Read(backend serializer.DeserializingBackend) (element.Element, error) {
	bytes, err := backend.DecodeBytes()
	if err != nil {
		return nil, err
	}
	return &element.ImageElement{bytes}, nil
}

type tensorDeserializer struct {
	tensor *ds.Tensor
	codec  FundamentalCodec
}

func (t tensorDeserializer) Read(backend serializer.DeserializingBackend) (element.Element, error) {
	data, err := t.codec.ReadArray(backend)
	if err != nil {
		return nil, err
	}
	return &element.TensorElement{data}, nil
}

type embeddedTabularDeserializer struct {
	rowDeserializer *tableRowDeserializer
}

func (t embeddedTabularDeserializer) Read(backend serializer.DeserializingBackend) (element.Element, error) {
	cnt, err := backend.DecodeArrayLen()
	if err != nil {
		return nil, err
	}
	rows := make([]*element.TabularRow, cnt)
	for i := 0; i < cnt; i++ {
		row, err := t.rowDeserializer.ReadTabularRow(backend)
		if err != nil {
			return nil, err
		}
		rows[i] = row
	}
	return &element.EmbeddedTabularElement{rows}, nil
}

type nullableDeserializer struct {
	underlyingDeserializer ElementDeserializer
}

func (n nullableDeserializer) Read(backend serializer.DeserializingBackend) (element.Element, error) {
	isNil, err := backend.NextIsNil()
	if err != nil {
		return nil, err
	}
	if isNil {
		err = backend.DecodeNil()
		return element.Primitive{nil}, err
	} else {
		return n.underlyingDeserializer.Read(backend)
	}
}

type arrayDeserializer struct {
	underlyingDeserializer ElementDeserializer
}

func (a arrayDeserializer) Read(backend serializer.DeserializingBackend) (element.Element, error) {
	len, err := backend.DecodeArrayLen()
	if err != nil {
		return nil, err
	}
	result := make([]element.Element, len, len)
	for i := 0; i < len; i++ {
		e, err := a.underlyingDeserializer.Read(backend)
		if err != nil {
			return nil, err
		}
		result[i] = e
	}
	return &element.ArrayElement{result}, nil
}

type structDeserializer struct {
	// borrowing tableDeserializer
	underlying tableRowDeserializer
}

func (s structDeserializer) Read(backend serializer.DeserializingBackend) (element.Element, error) {
	row, err := s.underlying.ReadTabularRow(backend)
	if err != nil {
		return nil, err
	}
	return &element.StructElement{Elements: row.Columns}, nil
}
