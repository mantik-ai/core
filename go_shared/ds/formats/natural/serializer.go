package natural

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/util/serializer"
)

type ElementSerializer interface {
	Write(backend serializer.SerializingBackend, element element.Element) error
}

type nilSerializer struct{}

func (n *nilSerializer) Write(backend serializer.SerializingBackend, element element.Element) error {
	return backend.EncodeNil()
}

func lookupElementSerializer(dataType ds.DataType) (ElementSerializer, error) {
	if dataType == ds.Void {
		return &nilSerializer{}, nil
	}
	if dataType.IsFundamental() {
		codec, err := GetFundamentalCodec(dataType)
		if err != nil {
			return nil, err
		}
		return primitiveSerializer{codec}, nil
	}
	tabularData, ok := dataType.(*ds.TabularData)
	if ok {
		tableRowSerializer, err := prepareTableRowSerializer(tabularData)
		if err != nil {
			return nil, err
		}
		return embeddedTabularSerializer{*tableRowSerializer}, nil
	}
	image, ok := dataType.(*ds.Image)
	if ok {
		return imageSerializer{image}, nil
	}
	tensorType, ok := dataType.(*ds.Tensor)
	if ok {
		codec, err := GetFundamentalCodec(tensorType.ComponentType.Underlying)
		if err != nil {
			return nil, err
		}
		return tensorSerializer{*tensorType, codec}, nil
	}
	return nil, errors.Errorf("Unsupported type %s", dataType.TypeName())
}

func LookupRootElementSerializer(dataType ds.DataType) (ElementSerializer, error) {
	tabularData, isTabular := dataType.(*ds.TabularData)
	if !isTabular {
		// Single Element
		return lookupElementSerializer(dataType)
	}
	tableRowSerializer, error := prepareTableRowSerializer(tabularData)
	if error != nil {
		return nil, error
	}
	return tableRowSerializer, nil
}

type primitiveSerializer struct {
	codec FundamentalCodec
}

func (p primitiveSerializer) Write(backend serializer.SerializingBackend, e element.Element) error {
	primitiveElement, ok := e.(element.Primitive)
	if !ok {
		return errors.New("Expected primitive value")
	}
	return p.codec.Write(backend, primitiveElement.X)
}

func prepareTableRowSerializer(data *ds.TabularData) (*tableRowSerializer, error) {
	subEncoders := make([]ElementSerializer, 0)
	for _, v := range data.Columns {
		subEncoder, err := lookupElementSerializer(v.SubType.Underlying)
		if err != nil {
			return nil, err
		}
		subEncoders = append(subEncoders, subEncoder)
	}
	return &tableRowSerializer{subEncoders}, nil
}

type tableRowSerializer struct {
	columnEncoders []ElementSerializer
}

func (t tableRowSerializer) Write(backend serializer.SerializingBackend, e element.Element) error {
	row, ok := e.(*element.TabularRow)
	if !ok {
		return errors.New("Expected table row")
	}
	if len(row.Columns) != len(t.columnEncoders) {
		return errors.Errorf("Expected %d columns, got %d", len(t.columnEncoders), len(row.Columns))
	}
	err := backend.EncodeArrayLen(len(t.columnEncoders))
	if err != nil {
		return err
	}
	for i := 0; i < len(t.columnEncoders); i++ {
		err = t.columnEncoders[i].Write(backend, row.Columns[i])
		if err != nil {
			return err
		}
	}
	return nil
}

type imageSerializer struct {
	image *ds.Image
}

func (i imageSerializer) Write(backend serializer.SerializingBackend, e element.Element) error {
	imageElement, ok := e.(*element.ImageElement)
	if !ok {
		return errors.New("Expected image element")
	}
	return backend.EncodeBytes(imageElement.Bytes)
}

type tensorSerializer struct {
	tensor ds.Tensor
	codec  FundamentalCodec
}

func (t tensorSerializer) Write(backend serializer.SerializingBackend, e element.Element) error {
	tensorElement, ok := e.(*element.TensorElement)
	if !ok {
		return errors.New("Expected tensor element")
	}
	return t.codec.WriteArray(backend, tensorElement.Values)
}

type embeddedTabularSerializer struct {
	tableRowSerializer tableRowSerializer
}

func (t embeddedTabularSerializer) Write(backend serializer.SerializingBackend, e element.Element) error {
	tabularElement, ok := e.(*element.EmbeddedTabularElement)
	if !ok {
		return errors.New("Expected embedded tabular element")
	}
	err := backend.EncodeArrayLen(len(tabularElement.Rows))
	if err != nil {
		return err
	}
	for i := 0; i < len(tabularElement.Rows); i++ {
		err = t.tableRowSerializer.Write(backend, tabularElement.Rows[i])
		if err != nil {
			return err
		}
	}
	return nil
}
