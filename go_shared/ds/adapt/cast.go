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
