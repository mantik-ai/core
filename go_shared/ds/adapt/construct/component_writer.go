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
package construct

import (
	"bytes"
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"reflect"
)

/** A Writer which constructs an object from it's components. */
type ComponentWriter interface {
	Start() SingleComponentWriter
	Type() ds.DataType
	UnderlyingType() *ds.FundamentalType
}

/** Writes a single object, after finishing you can call result to get the resulting element. */
type SingleComponentWriter interface {
	element.StreamWriter
	Result() element.Element
}

func CreateImageWriter(image *ds.Image) (ComponentWriter, error) {
	if len(image.Components) != 1 {
		return nil, errors.Errorf("Image constructing only supported for 1 component, got %d", len(image.Components))
	}
	ft, ok := image.Components[0].Component.ComponentType.Underlying.(*ds.FundamentalType)
	if !ok {
		return nil, errors.Errorf("Only fundamental image components supported")
	}
	if !image.IsPlain() {
		return nil, errors.Errorf("Can only encode plain images")
	}
	return imageComponentWriter{
		image,
		ft,
	}, nil
}

type imageComponentWriter struct {
	image *ds.Image
	ft    *ds.FundamentalType
}

func (i imageComponentWriter) Type() ds.DataType {
	return i.image
}

func (i imageComponentWriter) Start() SingleComponentWriter {
	binWriter, err := lookupBinaryWriter(i.ft)
	if err != nil {
		panic(err.Error())
	}
	return &singleImageComponentWriter{
		bytes.Buffer{},
		binWriter,
		0,
		i.image.Width * i.image.Height,
	}
}

func (i imageComponentWriter) UnderlyingType() *ds.FundamentalType {
	return i.ft
}

type singleImageComponentWriter struct {
	buffer           bytes.Buffer
	binWriter        binaryWriter
	elements         int
	expectedElements int
}

func (i *singleImageComponentWriter) Write(row element.Element) error {
	raw, ok := row.(element.Primitive)
	if !ok {
		return errors.New("Not a primitive")
	}
	err := i.binWriter(raw.X, &i.buffer)
	if err != nil {
		return err
	}
	i.elements += 1
	return nil
}

func (i *singleImageComponentWriter) Close() error {
	if i.elements != i.expectedElements {
		return errors.New("Element count mismatch")
	}
	return nil
}

func (i *singleImageComponentWriter) Result() element.Element {
	return &element.ImageElement{i.buffer.Bytes()}
}

func CreateTensorWriter(tensor *ds.Tensor) (ComponentWriter, error) {
	return tensorWriter{
		tensor,
	}, nil
}

type tensorWriter struct {
	tensor *ds.Tensor
}

func (i tensorWriter) Start() SingleComponentWriter {
	sliceType := reflect.SliceOf(i.UnderlyingType().GoType)
	size := i.tensor.PackedElementCount()
	value := reflect.MakeSlice(sliceType, size, size)
	return &singleTensorWriter{
		value,
		0,
		size,
	}
}

func (i tensorWriter) Type() ds.DataType {
	return i.tensor
}

func (i tensorWriter) UnderlyingType() *ds.FundamentalType {
	return i.tensor.ComponentType.Underlying.(*ds.FundamentalType)
}

type singleTensorWriter struct {
	value    reflect.Value
	pos      int
	expected int
}

func (i *singleTensorWriter) Write(row element.Element) error {
	plain := row.(element.Primitive).X
	i.value.Index(i.pos).Set(reflect.ValueOf(plain))
	i.pos += 1
	return nil
}

func (i *singleTensorWriter) Close() error {
	if i.pos != i.expected {
		return errors.New("Unexpected count of elements")
	}
	return nil
}

func (i *singleTensorWriter) Result() element.Element {
	return &element.TensorElement{
		i.value.Interface(),
	}
}
