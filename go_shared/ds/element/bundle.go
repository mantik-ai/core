package element

import "gl.ambrosys.de/mantik/go_shared/ds"

// Tabular DataRows together with their type.
type Bundle struct {
	Type ds.DataType
	Rows []*TabularRow
}
