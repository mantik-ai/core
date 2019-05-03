package element

import "gl.ambrosys.de/mantik/go_shared/ds"

// Tabular DataRows together with their type.
type Bundle struct {
	Type ds.DataType
	Rows []Element
}

// Get a Primitive, assuming that the bundle is tabular, will panic if not existing or wrong type.
func (b *Bundle) GetTabularPrimitive(row int, column int) interface{} {
	return b.Rows[row].(*TabularRow).Columns[column].(Primitive).X
}

// Get a Primitive, assuming that  the bundle is a single element.
func (b *Bundle) GetSinglePrimitive() interface{} {
	return b.Rows[0].(Primitive).X
}

func (b *Bundle) IsTabular() bool {
	_, ok := b.Type.(*ds.TabularData)
	return ok
}
