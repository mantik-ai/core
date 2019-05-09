package adapt

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
)

type columnAdapter struct {
	sourcePos int
	cast      *Cast
}

func lookupTableAdapter(from *ds.TabularData, to *ds.TabularData) (*Cast, error) {
	// Algorithm:
	// - Each expected column must also exist in from
	// - Inner type must be convertable

	columnCount := len(to.Columns)
	columnAdapters := make([]columnAdapter, columnCount)

	if to.RowCount != nil && from.RowCount == nil {
		return nil, errors.Errorf("Cannot convert unrestricted row count into restricted row count (%d)", *to.RowCount)
	}

	var loosing = false
	var canFail = false

	// Special Case, input and output has ony one column, than we can directly match it and do not have to look at the name
	if columnCount == 1 && len(from.Columns) == 1 {
		subAdapter, err := LookupCast(from.Columns[0].SubType.Underlying, to.Columns[0].SubType.Underlying)
		if err != nil {
			return nil, err
		}
		columnAdapters[0] = columnAdapter{0, subAdapter}
		loosing = subAdapter.Loosing
		canFail = subAdapter.CanFail
	} else {
		for i, v := range to.Columns {
			sourcePos, subAdapter, err := lookupColumnConverter(from, v.SubType.Underlying, v.Name)
			if err != nil {
				return nil, err
			}
			columnAdapters[i] = columnAdapter{sourcePos, subAdapter}
			loosing = loosing || subAdapter.Loosing
			canFail = canFail || subAdapter.CanFail
		}
	}

	var rowAdapter = func(in *element.TabularRow) (*element.TabularRow, error) {
		var result = make([]element.Element, columnCount)
		for i := 0; i < columnCount; i++ {
			converted, err := columnAdapters[i].cast.Adapter(in.Columns[columnAdapters[i].sourcePos])
			if err != nil {
				return nil, err
			}
			result[i] = converted
		}
		return &element.TabularRow{result}, nil
	}

	var result Adapter = func(in element.Element) (element.Element, error) {
		// We can adapt embedded tabular elements, but also plain rows
		// This is a design bug, see #53
		inRow, isRow := in.(*element.TabularRow)
		if isRow {
			return rowAdapter(inRow)
		}
		inTabular := in.(*element.EmbeddedTabularElement)
		resultRows := make([]*element.TabularRow, len(inTabular.Rows))
		for i, inRow := range inTabular.Rows {
			converted, err := rowAdapter(inRow)
			if err != nil {
				return nil, err
			}
			resultRows[i] = converted
		}
		return &element.EmbeddedTabularElement{resultRows}, nil
	}
	return &Cast{
		From:    from,
		To:      to,
		Loosing: loosing,
		CanFail: canFail,
		Adapter: result,
	}, nil
}

// Lookup a column with given name from a source table, generates a converter for it.
func lookupColumnConverter(from *ds.TabularData, expectedType ds.DataType, name string) (int, *Cast, error) {
	for i, v := range from.Columns {
		if v.Name == name {
			subAdapter, err := LookupCast(v.SubType.Underlying, expectedType)
			if err != nil {
				return 0, nil, err
			}
			return i, subAdapter, nil
		}
	}
	return 0, nil, errors.Errorf("Column %s not found in source tabular data", name)
}
