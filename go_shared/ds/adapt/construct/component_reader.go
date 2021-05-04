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
	"io"
	"reflect"
)

/* A Reader which deconstructs an object into its components. */
type ComponentReader interface {
	ReadComponents(e element.Element) (element.StreamReader, error)
	Type() ds.DataType
	UnderlyingType() *ds.FundamentalType
}

func CreateImageReader(image *ds.Image) (ComponentReader, error) {
	if len(image.Components) != 1 {
		return nil, errors.Errorf("Image deconstructing only supported for 1 component, got %d", len(image.Components))
	}
	ft, ok := image.Components[0].Component.ComponentType.Underlying.(*ds.FundamentalType)
	if !ok {
		return nil, errors.Errorf("Only fundamental image components supported")
	}
	if !image.IsPlain() {
		return nil, errors.Errorf("Can only decode plain images")
	}
	return imageComponentReader{
		image,
		ft,
	}, nil
}

type imageComponentReader struct {
	image *ds.Image
	ft    *ds.FundamentalType
}

func (i imageComponentReader) Type() ds.DataType {
	return i.image
}

func (i imageComponentReader) ReadComponents(e element.Element) (element.StreamReader, error) {
	image, ok := e.(*element.ImageElement)
	if !ok {
		return nil, errors.New("Expected image")
	}
	reader := bytes.NewReader(image.Bytes)
	binReader, err := lookupBinaryReader(i.ft)
	if err != nil {
		return nil, err
	}
	return &imageStreamReader{reader, binReader}, nil
}

func (i imageComponentReader) UnderlyingType() *ds.FundamentalType {
	return i.ft
}

type imageStreamReader struct {
	reader    io.Reader
	binReader binaryReader
}

func (i *imageStreamReader) Read() (element.Element, error) {
	value, err := i.binReader(i.reader)
	if err != nil {
		return nil, err
	}
	return element.Primitive{value}, err
}

func CreateTensorReader(tensor *ds.Tensor) (ComponentReader, error) {
	return &tensorComponentReader{tensor}, nil
}

type tensorComponentReader struct {
	tensor *ds.Tensor
}

func (t *tensorComponentReader) ReadComponents(e element.Element) (element.StreamReader, error) {
	te := e.(*element.TensorElement)
	return &tensorStreamReader{
		reflect.ValueOf(te.Values),
		0,
		t.tensor.PackedElementCount(),
	}, nil
}

func (t *tensorComponentReader) Type() ds.DataType {
	return t.tensor
}

func (t *tensorComponentReader) UnderlyingType() *ds.FundamentalType {
	return t.tensor.ComponentType.Underlying.(*ds.FundamentalType)
}

type tensorStreamReader struct {
	elements reflect.Value
	pos      int
	length   int
}

func (t *tensorStreamReader) Read() (element.Element, error) {
	if t.pos >= t.length {
		return nil, io.EOF
	}
	plain := t.elements.Index(t.pos).Interface()
	t.pos += 1
	return element.Primitive{plain}, nil
}
