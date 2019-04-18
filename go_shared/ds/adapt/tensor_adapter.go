package adapt

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"reflect"
)

/*
Tensor Adapters:
- their shape must match
- except, outer single components can be automatically packed out / in
- fundamental types can be converted to scalar tensors and back
*/

func lookupTensorAdapter(from *ds.Tensor, to *ds.Tensor) (Adapter, error) {
	if reflect.DeepEqual(from.Shape, to.Shape) || // case same shape
		len(from.Shape) > 0 && reflect.DeepEqual(from.Shape[1:], to.Shape) ||
		len(to.Shape) > 0 && reflect.DeepEqual(to.Shape[1:], from.Shape) {
		componentAdapter, err := lookupTensorComponentAdapter(from.ComponentType.Underlying, to.ComponentType.Underlying)
		if err != nil {
			return nil, err
		}
		return componentAdapter, nil
	}
	return nil, nil
}

func lookupTensorPackOut(from *ds.Tensor, to *ds.FundamentalType) (Adapter, error) {
	if from.PackedElementCount() != 1 {
		return nil, errors.New("only single element tensors can be packed out into scalars")
	}
	fromFundamental, fromOk := from.ComponentType.Underlying.(*ds.FundamentalType)
	if !fromOk {
		return nil, errors.Errorf("Only fundamental types supported, got %s", from.TypeName())
	}
	underlyingAdapter, err := LookupRawAdapter(fromFundamental, to)
	if err != nil {
		return nil, err
	}
	adapter := func(in element.Element) (element.Element, error) {
		inCasted := in.(*element.TensorElement)
		return element.Primitive{underlyingAdapter(reflect.ValueOf(inCasted.Values).Index(0).Interface())}, nil
	}
	return adapter, nil
}

func lookupTensorPackIn(from *ds.FundamentalType, to *ds.Tensor) (Adapter, error) {
	if to.PackedElementCount() != 1 {
		return nil, errors.New("only single element tensors can be packed in from scalars")
	}
	toFundamental, toOk := to.ComponentType.Underlying.(*ds.FundamentalType)
	if !toOk {
		return nil, errors.Errorf("Only fundamental types supported, got %s", from.TypeName())
	}
	underlyingAdapter, err := LookupRawAdapter(from, toFundamental)
	if err != nil {
		return nil, err
	}
	tensorSliceType := reflect.SliceOf(toFundamental.GoType)
	adapter := func(in element.Element) (element.Element, error) {
		inCasted := in.(element.Primitive)
		resultSlice := reflect.MakeSlice(tensorSliceType, 1, 1)
		resultSlice.Index(0).Set(reflect.ValueOf(underlyingAdapter(inCasted.X)))
		return &element.TensorElement{resultSlice.Interface()}, nil
	}
	return adapter, nil
}

func lookupTensorComponentAdapter(from ds.DataType, to ds.DataType) (Adapter, error) {
	fromFundamental, fromOk := from.(*ds.FundamentalType)
	toFundamental, toOk := to.(*ds.FundamentalType)
	if !fromOk || !toOk {
		return nil, errors.Errorf("Only fundamental types supported, got %s to %s", from.TypeName(), to.TypeName())
	}
	if fromFundamental == toFundamental {
		return emptyAdapter, nil
	}
	underlyingAdapter, err := LookupRawAdapter(fromFundamental, toFundamental)
	if err != nil {
		return nil, err
	}
	targetSliceType := reflect.SliceOf(toFundamental.GoType)

	adapter := func(in element.Element) (element.Element, error) {
		inTensorSlice := reflect.ValueOf(in.(*element.TensorElement).Values)
		l := inTensorSlice.Len()
		newValues := reflect.MakeSlice(targetSliceType, l, l)
		for i := 0; i < l; i++ {
			newValues.Index(i).Set(reflect.ValueOf(underlyingAdapter(inTensorSlice.Index(i).Interface())))
		}
		return &element.TensorElement{newValues.Interface()}, nil
	}

	return adapter, nil
}
