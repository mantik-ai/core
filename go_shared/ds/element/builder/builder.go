package builder

import (
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
)

func Bundle(dataType ds.DataType, tabularRows ...*element.TabularRow) element.Bundle {
	return element.Bundle{dataType, tabularRows}
}

func Rows(tabularRows ...*element.TabularRow) []*element.TabularRow {
	return tabularRows
}

func RowsAsElements(tabularRows ...*element.TabularRow) []element.Element {
	result := make([]element.Element, len(tabularRows))
	for i, v := range tabularRows {
		result[i] = v
	}
	return result
}

func Row(elements ...element.Element) *element.TabularRow {
	return &element.TabularRow{elements}
}

/* A row which consits of primitives only. */
func PrimitiveRow(elements ...interface{}) *element.TabularRow {
	converted := make([]element.Element, len(elements))
	for i := 0; i < len(elements); i++ {
		converted[i] = element.Primitive{elements[i]}
	}
	return &element.TabularRow{converted}
}

func Tensor(values interface{}) *element.TensorElement {
	return &element.TensorElement{values}
}
