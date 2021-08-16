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
package natural

import (
	"bytes"
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/util/serializer"
)

type ElementSerializer interface {
	Write(backend serializer.SerializingBackend, element element.Element) error
}

/** Encode into internal representation. (Also see DeserializeFromBytes)*/
func SerializeToBytes(es ElementSerializer, element element.Element) ([]byte, error) {
	buf := bytes.Buffer{}
	backend, err := serializer.CreateSerializingBackend(serializer.BACKEND_MSGPACK, &buf)
	if err != nil {
		return nil, err
	}
	err = es.Write(backend, element)
	if err != nil {
		return nil, err
	}
	backend.Flush()
	return buf.Bytes(), err
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
	nullableType, ok := dataType.(*ds.Nullable)
	if ok {
		underlying, err := lookupElementSerializer(nullableType.Underlying.Underlying)
		if err != nil {
			return nil, err
		}
		return nullableSerializer{underlying}, nil
	}
	arrayType, ok := dataType.(*ds.Array)
	if ok {
		underlying, err := lookupElementSerializer(arrayType.Underlying.Underlying)
		if err != nil {
			return nil, err
		}
		return arraySerializer{underlying}, nil
	}
	structType, ok := dataType.(*ds.Struct)
	if ok {
		// Using the tableRowSerializer for that
		tableRowSerializer, err := prepareTableRowSerializer(
			&ds.TabularData{Columns: structType.Fields},
		)
		if err != nil {
			return nil, err
		}
		return &structSerializer{*tableRowSerializer}, nil
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
	for _, v := range data.Columns.Values {
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

type nullableSerializer struct {
	underlying ElementSerializer
}

func (n nullableSerializer) Write(backend serializer.SerializingBackend, e element.Element) error {
	primitiveElement, ok := e.(element.Primitive)
	if ok && primitiveElement.X == nil {
		backend.EncodeNil()
		return nil
	} else {
		return n.underlying.Write(backend, e)
	}
}

type arraySerializer struct {
	underlying ElementSerializer
}

func (a arraySerializer) Write(backend serializer.SerializingBackend, e element.Element) error {
	arrayElement, ok := e.(*element.ArrayElement)
	if !ok {
		return errors.Errorf("Expected array element, got %d", e.Kind())
	} else {
		backend.EncodeArrayLen(len(arrayElement.Elements))
		for _, v := range arrayElement.Elements {
			err := a.underlying.Write(backend, v)
			if err != nil {
				return err
			}
		}
	}
	return nil
}

type structSerializer struct {
	// Borrowing the tableRowSerializer
	underlying tableRowSerializer
}

func (s structSerializer) Write(backend serializer.SerializingBackend, e element.Element) error {
	structElement, ok := e.(*element.StructElement)
	if !ok {
		return errors.Errorf("Expected struct element, got %d", e.Kind())
	} else {
		return s.underlying.Write(backend, &element.TabularRow{structElement.Elements})
	}
}
