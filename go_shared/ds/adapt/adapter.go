package adapt

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
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
