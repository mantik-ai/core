package adapt

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
)

/* A Cast from one type to another one. */
type Cast struct {
	From    ds.DataType
	To      ds.DataType
	Loosing bool
	CanFail bool
	Adapter Adapter
}

/** Looks for a cast from from to to. In contrast to Auto adapters, casts can also fail. */
func LookupCast(from ds.DataType, to ds.DataType) (*Cast, error) {
	if ds.DataTypeEquality(from, to) {
		return &Cast{from, to, false, false, emptyAdapter}, nil
	}
	fromFt, isFromFt := from.(*ds.FundamentalType)
	toFt, isToFt := to.(*ds.FundamentalType)
	if isFromFt && isToFt {
		return lookupFundamentalCast(fromFt, toFt)
	}
	fromImage, isFromImage := from.(*ds.Image)
	toTensor, isToTensor := to.(*ds.Tensor)
	if isFromImage && isToTensor {
		return lookupImageToTensor(fromImage, toTensor)
	}
	fromTabular, isFromTabular := from.(*ds.TabularData)
	toTabular, isToTabular := to.(*ds.TabularData)
	if isFromTabular && isToTabular {
		return lookupTableAdapter(fromTabular, toTabular)
	}
	fromTensor, isFromTensor := from.(*ds.Tensor)
	if isFromTensor && isToTensor {
		return lookupTensorAdapter(fromTensor, toTensor)
	}
	if isFromTensor && isToFt {
		return lookupTensorPackOut(fromTensor, toFt)
	}
	if isFromFt && isToTensor {
		return lookupTensorPackIn(fromFt, toTensor)
	}
	toImage, isToImage := to.(*ds.Image)
	if isFromImage && isToImage {
		return lookupImageToImage(fromImage, toImage)
	}
	if isFromTensor && isToImage {
		return lookupTensorToImage(fromTensor, toImage)
	}

	fromNullable, isFromNullable := from.(*ds.Nullable)
	toNullable, isToNullable := to.(*ds.Nullable)
	if isFromNullable && isToNullable {
		return lookupNullableToNullable(fromNullable, toNullable)
	}
	if isFromNullable && !isToNullable {
		return lookupNullableToNonNullable(fromNullable, to)
	}
	if !isFromNullable && isToNullable {
		return lookupNonNullableToNullable(from, toNullable)
	}

	fromArray, isFromArray := from.(*ds.Array)
	toArray, isToArray := to.(*ds.Array)
	if isFromArray && isToArray {
		return lookupArrayCast(fromArray, toArray)
	}

	fromStruct, isFromStruct := from.(*ds.Struct)
	toStruct, isToStruct := to.(*ds.Struct)
	if isFromStruct && isToStruct {
		return lookupStructCast(fromStruct, toStruct)
	}

	if isToStruct {
		return lookupPackCast(from, toStruct)
	}

	if isFromStruct {
		return lookupUnpackCast(fromStruct, to)
	}

	return nil, errors.New("Cast not available")
}

func lookupFundamentalCast(from *ds.FundamentalType, to *ds.FundamentalType) (*Cast, error) {
	if to == ds.Void {
		return &Cast{from, to, false, false, toVoidAdapter}, nil
	}
	if to == ds.String {
		return &Cast{from, to, false, false, wrapRawAdapter(toStringAdapter(from))}, nil
	}
	if from == ds.String {
		return &Cast{from, to, false, true, fromStringAdapter(to)}, nil
	}
	losslessAdapter, err := LookupRawAdapter(from, to)
	if err == nil {
		return &Cast{from, to, false, false, wrapRawAdapter(losslessAdapter)}, nil
	}
	lossyAdapter, err := LookupLossyRawAdapter(from, to)
	if err == nil {
		return &Cast{from, to, true, false, wrapRawAdapter(lossyAdapter)}, nil
	}
	return nil, errors.New("Not yet implemented")
}

func lookupNullableToNullable(from *ds.Nullable, to *ds.Nullable) (*Cast, error) {
	underlyingCast, err := LookupCast(from.Underlying.Underlying, to.Underlying.Underlying)
	if err != nil {
		return nil, err
	}
	adapter := func(in element.Element) (element.Element, error) {
		p, isP := in.(element.Primitive)
		if isP && p.X == nil {
			return in, nil
		} else {
			return underlyingCast.Adapter(in)
		}
	}
	return &Cast{
		From:    from,
		To:      to,
		Loosing: underlyingCast.Loosing,
		CanFail: underlyingCast.CanFail,
		Adapter: adapter,
	}, nil
}

func lookupNullableToNonNullable(from *ds.Nullable, to ds.DataType) (*Cast, error) {
	underlyingCast, err := LookupCast(from.Underlying.Underlying, to)
	if err != nil {
		return nil, err
	}
	adapter := func(in element.Element) (element.Element, error) {
		p, isP := in.(element.Primitive)
		if isP && p.X == nil {
			return nil, errors.New("Cannot cast away null into non nullable")
		} else {
			return underlyingCast.Adapter(in)
		}
	}
	return &Cast{
		From:    from,
		To:      to,
		Loosing: underlyingCast.Loosing,
		CanFail: true,
		Adapter: adapter,
	}, nil
}

func lookupNonNullableToNullable(from ds.DataType, to *ds.Nullable) (*Cast, error) {
	underlyingCast, err := LookupCast(from, to.Underlying.Underlying)
	if err != nil {
		return nil, err
	}
	adapter := func(in element.Element) (element.Element, error) {
		return underlyingCast.Adapter(in)
	}
	return &Cast{
		From:    from,
		To:      to,
		Loosing: underlyingCast.Loosing,
		CanFail: underlyingCast.CanFail,
		Adapter: adapter,
	}, nil
}

func wrapRawAdapter(adapter RawAdapter) Adapter {
	return func(e element.Element) (element.Element, error) {
		return element.Primitive{adapter(e.(element.Primitive).X)}, nil
	}
}

func lookupArrayCast(from *ds.Array, to *ds.Array) (*Cast, error) {
	underlying, err := LookupCast(from.Underlying.Underlying, to.Underlying.Underlying)
	if err != nil {
		return nil, err
	}
	return &Cast{
		From:    from,
		To:      to,
		Loosing: underlying.Loosing,
		CanFail: underlying.CanFail,
		Adapter: func(e element.Element) (element.Element, error) {
			input, ok := e.(*element.ArrayElement)
			if !ok {
				return nil, errors.New("Expected array")
			}
			result := make([]element.Element, len(input.Elements), len(input.Elements))
			for i, v := range input.Elements {
				x, err := underlying.Adapter(v)
				if err != nil {
					return nil, err
				}
				result[i] = x
			}
			return &element.ArrayElement{result}, nil
		},
	}, nil
}

func lookupStructCast(from *ds.Struct, to *ds.Struct) (*Cast, error) {
	if from.Arity() != to.Arity() {
		return nil, errors.New("Cannot cast with different arity")
	}

	subCasts := make([]*Cast, from.Arity(), from.Arity())
	var canFail = false
	var loosing = false
	for i, fromType := range from.Fields {
		cast, err := LookupCast(fromType.SubType.Underlying, to.Fields[i].SubType.Underlying)
		if err != nil {
			return nil, err
		}
		canFail = canFail || cast.CanFail
		loosing = loosing || cast.Loosing
		subCasts[i] = cast
	}
	return &Cast{
		From:    from,
		To:      to,
		Loosing: loosing,
		CanFail: canFail,
		Adapter: func(e element.Element) (element.Element, error) {
			structElement, ok := e.(*element.StructElement)
			if !ok {
				return nil, errors.New("Expected struct")
			}
			result := make([]element.Element, len(subCasts), len(subCasts))
			for i, e := range structElement.Elements {
				casted, err := subCasts[i].Adapter(e)
				if err != nil {
					return nil, err
				}
				result[i] = casted
			}
			return &element.StructElement{result}, nil
		},
	}, nil
}

func lookupPackCast(from ds.DataType, to *ds.Struct) (*Cast, error) {
	if to.Arity() != 1 {
		return nil, errors.New("Can only pack structs of arity 1")
	}
	cast, err := LookupCast(from, to.Fields[0].SubType.Underlying)
	if err != nil {
		return nil, err
	}
	return &Cast{
		From:    from,
		To:      to,
		Loosing: cast.Loosing,
		CanFail: cast.CanFail,
		Adapter: func(e element.Element) (element.Element, error) {
			casted, err := cast.Adapter(e)
			if err != nil {
				return nil, err
			}
			return &element.StructElement{[]element.Element{casted}}, nil
		},
	}, nil
}

func lookupUnpackCast(from *ds.Struct, to ds.DataType) (*Cast, error) {
	if from.Arity() != 1 {
		return nil, errors.New("Can only unpack structs of arity 1")
	}
	cast, err := LookupCast(from.Fields[0].SubType.Underlying, to)
	if err != nil {
		return nil, err
	}
	return &Cast{
		From:    from,
		To:      to,
		Loosing: cast.Loosing,
		CanFail: cast.CanFail,
		Adapter: func(e element.Element) (element.Element, error) {
			se, ok := e.(*element.StructElement)
			if !ok {
				return nil, errors.New("Expected Struct")
			}
			casted, err := cast.Adapter(se.Elements[0])
			if err != nil {
				return nil, err
			}
			return casted, nil
		},
	}, nil
}
