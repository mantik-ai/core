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
)

type Comparator interface {
	Compare(left element.Element, right element.Element) int
}

func LookupComparator(dataType ds.DataType) (Comparator, error) {
	if dataType == ds.Void {
		return &nilComparator{}, nil
	}
	if dataType.IsFundamental() {
		codec, err := GetFundamentalCodec(dataType)
		if err != nil {
			return nil, err
		}
		return primitiveComparator{codec}, nil
	}
	tabularData, ok := dataType.(*ds.TabularData)
	if ok {
		cc, err := newCompoundComparator(tabularData.Columns)
		if err != nil {
			return nil, err
		}
		return tabularComparator{*cc}, nil
	}
	_, imageType := dataType.(*ds.Image)
	if imageType {
		return imageComparator{}, nil
	}
	tensorType, ok := dataType.(*ds.Tensor)
	if ok {
		codec, err := GetFundamentalCodec(tensorType.ComponentType.Underlying)
		if err != nil {
			return nil, err
		}
		return tensorComparator{codec}, nil
	}
	nullableType, ok := dataType.(*ds.Nullable)
	if ok {
		underlying, err := LookupComparator(nullableType.Underlying.Underlying)
		if err != nil {
			return nil, err
		}
		return nullableComparator{underlying}, nil
	}
	arrayType, ok := dataType.(*ds.Array)
	if ok {
		underlying, err := LookupComparator(arrayType.Underlying.Underlying)
		if err != nil {
			return nil, err
		}
		return arrayComparator{underlying}, nil
	}
	structType, ok := dataType.(*ds.Struct)
	if ok {
		underlying, err := newCompoundComparator(structType.Fields)
		if err != nil {
			return nil, err
		}
		return structComparator{*underlying}, nil
	}
	return nil, errors.Errorf("Unsupported type %s", dataType.TypeName())
}

type nilComparator struct {
}

func (n nilComparator) Compare(left element.Element, right element.Element) int {
	return 0
}

type primitiveComparator struct {
	fc FundamentalCodec
}

func (p primitiveComparator) Compare(left element.Element, right element.Element) int {
	return p.fc.Compare(left.(element.Primitive).X, right.(element.Primitive).X)
}

type tabularComparator struct {
	compound compoundComparator
}

func (t tabularComparator) Compare(left element.Element, right element.Element) int {
	leftEmbedded, isEmbedded := left.(*element.EmbeddedTabularElement)
	if isEmbedded {
		rightEmbedded := right.(*element.EmbeddedTabularElement)
		if len(leftEmbedded.Rows) < len(rightEmbedded.Rows) {
			return -1
		} else if len(leftEmbedded.Rows) > len(rightEmbedded.Rows) {
			return 1
		} else {
			for idx, row := range leftEmbedded.Rows {
				c := t.Compare(row, rightEmbedded.Rows[idx])
				if c != 0 {
					return c
				}
			}
			return 0
		}
	}
	leftTab := left.(*element.TabularRow)
	rightTab := right.(*element.TabularRow)
	return t.compound.Compare(leftTab.Columns, rightTab.Columns)
}

type compoundComparator struct {
	sub []Comparator
}

func newCompoundComparator(orderedMap ds.NamedDataTypeMap) (*compoundComparator, error) {
	sub := make([]Comparator, orderedMap.Arity(), orderedMap.Arity())
	for i, c := range orderedMap.Values {
		subC, err := LookupComparator(c.SubType.Underlying)
		if err != nil {
			return nil, err
		}
		sub[i] = subC
	}
	return &compoundComparator{sub}, nil
}

func (t compoundComparator) Compare(left []element.Element, right []element.Element) int {
	for i, c := range t.sub {
		r := c.Compare(left[i], right[i])
		if r != 0 {
			return r
		}
	}
	return 0
}

type imageComparator struct {
}

func (i imageComparator) Compare(left element.Element, right element.Element) int {
	li := left.(*element.ImageElement)
	ri := right.(*element.ImageElement)
	return bytes.Compare(li.Bytes, ri.Bytes)
}

type tensorComparator struct {
	underlying FundamentalCodec
}

func (t tensorComparator) Compare(left element.Element, right element.Element) int {
	li := left.(*element.TensorElement)
	ri := right.(*element.TensorElement)
	return t.underlying.CompareArray(li.Values, ri.Values)
}

type nullableComparator struct {
	underlying Comparator
}

func (n nullableComparator) Compare(left element.Element, right element.Element) int {
	leftIsNull := isNull(left)
	rightIsNull := isNull(right)
	if leftIsNull && rightIsNull {
		return 0
	} else if leftIsNull && !rightIsNull {
		return -1
	} else if !leftIsNull && rightIsNull {
		return 1
	} else {
		return n.underlying.Compare(left, right)
	}
}

func isNull(e element.Element) bool {
	leftPrimitive, ok := e.(element.Primitive)
	if ok && leftPrimitive.X == nil {
		return true
	}
	return false
}

type arrayComparator struct {
	underlying Comparator
}

func (a arrayComparator) Compare(left element.Element, right element.Element) int {
	lc := left.(*element.ArrayElement)
	rc := right.(*element.ArrayElement)
	if len(lc.Elements) < len(rc.Elements) {
		return -1
	} else if len(lc.Elements) > len(rc.Elements) {
		return +1
	} else {
		for i, leftValue := range lc.Elements {
			rightValue := rc.Elements[i]
			diff := a.underlying.Compare(leftValue, rightValue)
			if diff != 0 {
				return diff
			}
		}
		return 0
	}
}

type structComparator struct {
	underlying compoundComparator
}

func (s structComparator) Compare(left element.Element, right element.Element) int {
	lc := left.(*element.StructElement)
	rc := right.(*element.StructElement)
	return s.underlying.Compare(lc.Elements, rc.Elements)
}
