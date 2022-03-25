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
package adapt

import (
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/mantik-ai/core/go_shared/ds/adapt/construct"
	"github.com/mantik-ai/core/go_shared/ds/element"
	"github.com/pkg/errors"
	"reflect"
)

/*
Tensor Adapters:
- their shape must match
- except, outer single components can be automatically packed out / in
- fundamental types can be converted to scalar tensors and back
*/

func lookupTensorAdapter(from *ds.Tensor, to *ds.Tensor) (*Cast, error) {
	if reflect.DeepEqual(from.Shape, to.Shape) || // case same shape
		len(from.Shape) > 0 && reflect.DeepEqual(from.Shape[1:], to.Shape) ||
		len(to.Shape) > 0 && reflect.DeepEqual(to.Shape[1:], from.Shape) {
		reader, err := construct.CreateTensorReader(from)
		if err != nil {
			return nil, err
		}
		writer, err := construct.CreateTensorWriter(to)
		if err != nil {
			return nil, err
		}
		return constructComponentCast(reader, writer)
	}
	return nil, errors.New("Incompatible tensors")
}

func lookupTensorPackOut(from *ds.Tensor, to *ds.FundamentalType) (*Cast, error) {
	if from.PackedElementCount() != 1 {
		return nil, errors.New("only single element tensors can be packed out into scalars")
	}
	fromFundamental, fromOk := from.ComponentType.Underlying.(*ds.FundamentalType)
	if !fromOk {
		return nil, errors.Errorf("Only fundamental types supported, got %s", from.TypeName())
	}
	underlyingCast, err := LookupCast(fromFundamental, to)
	if err != nil {
		return nil, err
	}
	adapter := func(in element.Element) (element.Element, error) {
		inCasted := in.(*element.TensorElement)
		inValue := element.Primitive{(reflect.ValueOf(inCasted.Values).Index(0).Interface())}
		convertedIn, err := underlyingCast.Adapter(inValue)
		if err != nil {
			return nil, err
		}
		return convertedIn, nil
	}
	return &Cast{
		From:    from,
		To:      to,
		Loosing: underlyingCast.Loosing,
		CanFail: underlyingCast.CanFail,
		Adapter: adapter,
	}, nil
}

func lookupTensorPackIn(from *ds.FundamentalType, to *ds.Tensor) (*Cast, error) {
	if to.PackedElementCount() != 1 {
		return nil, errors.New("only single element tensors can be packed in from scalars")
	}
	toFundamental, toOk := to.ComponentType.Underlying.(*ds.FundamentalType)
	if !toOk {
		return nil, errors.Errorf("Only fundamental types supported, got %s", from.TypeName())
	}
	underlyingCast, err := LookupCast(from, toFundamental)
	if err != nil {
		return nil, err
	}
	tensorSliceType := reflect.SliceOf(toFundamental.GoType)
	adapter := func(in element.Element) (element.Element, error) {
		converted, err := underlyingCast.Adapter(in)
		if err != nil {
			return nil, err
		}
		plain := converted.(element.Primitive).X
		resultSlice := reflect.MakeSlice(tensorSliceType, 1, 1)
		resultSlice.Index(0).Set(reflect.ValueOf(plain))
		return &element.TensorElement{resultSlice.Interface()}, nil
	}
	return &Cast{
		From:    from,
		To:      to,
		CanFail: underlyingCast.CanFail,
		Loosing: underlyingCast.Loosing,
		Adapter: adapter,
	}, nil
}
