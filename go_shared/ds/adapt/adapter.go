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
	"github.com/mantik-ai/core/go_shared/ds/element"
	"github.com/pkg/errors"
)

type Adapter = func(element.Element) (element.Element, error)

var emptyAdapter Adapter = func(element element.Element) (i element.Element, e error) {
	return element, nil
}

var toVoidAdapter Adapter = func(in element.Element) (i element.Element, e error) {
	return element.Primitive{nil}, nil
}

func ApplyForMany(a Adapter, rows []element.Element) ([]element.Element, error) {
	converted := make([]element.Element, len(rows))
	for i, v := range rows {
		c, err := a(v)
		if err != nil {
			return nil, err
		}
		converted[i] = c
	}
	return converted, nil
}

/** Applies a chain, outputAdapter(f(inputAdapter(rows))) for all rows. */
func ApplyChain(rows []element.Element, inputAdapter Adapter, f func([]element.Element) ([]element.Element, error), outputAdapter Adapter) ([]element.Element, error) {
	inputConverted, err := ApplyForMany(inputAdapter, rows)
	if err != nil {
		return nil, err
	}
	preResult, err := f(inputConverted)
	if err != nil {
		return nil, err
	}
	outputConverted, err := ApplyForMany(outputAdapter, preResult)
	return outputConverted, err
}

// Lookup for an automatic adapter from from to to.
// In contrast to casts, this auto adapters will never fail or loose data.
func LookupAutoAdapter(from ds.DataType, to ds.DataType) (Adapter, error) {
	if ds.DataTypeEquality(from, to) {
		return emptyAdapter, nil
	}
	if ds.DataTypeEquality(to, ds.Void) {
		return toVoidAdapter, nil
	}
	cast, err := LookupCast(from, to)
	if err != nil {
		return nil, err
	}
	if cast.CanFail {
		return nil, errors.Errorf("Cannot create automatic from %s to %s adapter as a cast can fail", ds.ToJsonString(from), ds.ToJsonString(to))
	}
	if cast.Loosing {
		return nil, errors.Errorf("Cannot create automatic adapter from %s to %s as a cast would loose precision", ds.ToJsonString(from), ds.ToJsonString(to))
	}
	return cast.Adapter, nil
}
