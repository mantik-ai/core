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
		sub := make([]Comparator, len(tabularData.Columns), len(tabularData.Columns))
		for i, c := range tabularData.Columns {
			subC, err := LookupComparator(c.SubType.Underlying)
			if err != nil {
				return nil, err
			}
			sub[i] = subC
		}
		return tabularComparator{sub}, nil
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
	sub []Comparator
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
	for i, c := range t.sub {
		r := c.Compare(leftTab.Columns[i], rightTab.Columns[i])
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
