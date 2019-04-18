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

func LookupAutoAdapter(from ds.DataType, to ds.DataType) (Adapter, error) {
	if ds.DataTypeEquality(from, to) {
		return emptyAdapter, nil
	}
	if ds.DataTypeEquality(to, ds.Void) {
		return toVoidAdapter, nil
	}

	fromFundamental, fromFundamentalOk := from.(*ds.FundamentalType)
	toFundamental, toFundamentalOk := to.(*ds.FundamentalType)
	if fromFundamentalOk && toFundamentalOk {
		primitiveConverter, err := LookupRawAdapter(fromFundamental, toFundamental)
		if err != nil {
			return nil, err
		}
		var result Adapter = func(i element.Element) (element.Element, error) {
			return element.Primitive{primitiveConverter(i.(element.Primitive).X)}, nil
		}
		return result, nil
	}

	fromTable, fromTableOk := from.(*ds.TabularData)
	toTable, toTableOk := to.(*ds.TabularData)

	if fromTableOk && toTableOk {
		return lookupTableAdapter(fromTable, toTable)
	}

	// special for sample use case
	fromImage, fromImageOk := from.(*ds.Image)
	toTensor, toTensorOk := to.(*ds.Tensor)
	if fromImageOk && toTensorOk {
		return lookupImageToTensor(fromImage, toTensor)
	}

	fromTensor, fromTensorOk := from.(*ds.Tensor)
	if fromTensorOk && toTensorOk {
		return lookupTensorAdapter(fromTensor, toTensor)
	}

	if fromTensorOk && toFundamentalOk {
		return lookupTensorPackOut(fromTensor, toFundamental)
	}
	if fromFundamentalOk && toTensorOk {
		return lookupTensorPackIn(fromFundamental, toTensor)
	}

	return nil, errors.Errorf("Type conversion from %s to %s not yet supported", from.TypeName(), to.TypeName())
}
