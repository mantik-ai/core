package adapt

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
)

type columnAdapter struct {
	sourcePos int
	adapter   Adapter
}

func lookupTableAdapter(from *ds.TabularData, to *ds.TabularData) (Adapter, error) {
	// Algorithm:
	// - Each expected column must also exist in from
	// - Inner type must be convertable

	columnCount := len(to.Columns)
	columnAdapters := make([]columnAdapter, columnCount)

	if to.RowCount != nil && from.RowCount == nil {
		return nil, errors.Errorf("Cannot convert unrestricted row count into restricted row count (%d)", *to.RowCount)
	}

	// Special Case, input and output has ony one column, than we can directly match it and do not have to look at the name
	if columnCount == 1 && len(from.Columns) == 1 {
		subAdapter, err := LookupAutoAdapter(from.Columns[0].SubType.Underlying, to.Columns[0].SubType.Underlying)
		if err != nil {
			return nil, err
		}
		columnAdapters[0] = columnAdapter{0, subAdapter}
	} else {
		for i, v := range to.Columns {
			sourcePos, subAdapter, err := lookupColumnConverter(from, v.SubType.Underlying, v.Name)
			if err != nil {
				return nil, err
			}
			columnAdapters[i] = columnAdapter{sourcePos, subAdapter}
		}
	}

	var result Adapter = func(in element.Element) (element.Element, error) {
		inRow := in.(*element.TabularRow)
		var result = make([]element.Element, columnCount)
		for i := 0; i < columnCount; i++ {
			converted, err := columnAdapters[i].adapter(inRow.Columns[columnAdapters[i].sourcePos])
			if err != nil {
				return nil, err
			}
			result[i] = converted
		}
		return &element.TabularRow{result}, nil
	}
	return result, nil
}

// Lookup a column with given name from a source table, generates a converter for it.
func lookupColumnConverter(from *ds.TabularData, expectedType ds.DataType, name string) (int, Adapter, error) {
	for i, v := range from.Columns {
		if v.Name == name {
			subAdapter, err := LookupAutoAdapter(v.SubType.Underlying, expectedType)
			if err != nil {
				return 0, nil, err
			}
			return i, subAdapter, nil
		}
	}
	return 0, nil, errors.Errorf("Column %s not found in source tabular data", name)
}
