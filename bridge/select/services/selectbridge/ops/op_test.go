package ops

import (
	"encoding/json"
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/element/builder"
	"gl.ambrosys.de/mantik/go_shared/ds/formats/natural"
	"gl.ambrosys.de/mantik/go_shared/ds/operations"
	"testing"
)

func TestOpList_UnmarshalJSON_EmptyList(t *testing.T) {
	var l OpList
	err := json.Unmarshal([]byte("[]"), &l)
	assert.NoError(t, err)
	assert.Equal(t, OpList{}, l)
}

func TestOpList_UnmarshalJSON(t *testing.T) {
	allOps := `
		[
			"get",
    		1,
    		"pop",
    		"cnt",
    		{
      			"type" : "string",
      			"value" : "Hello World"
    		},
			"cast",
    		"int32",
    		"int64",
    		"neg",
    		"and",
    		"or",
    		"eq",
			"int32",
    		"retf",
    		"bn",
    		"int32",
    		"add",
			"bn",
			"float32",
			"sub",
			"bn",
			"float64",
			"mul",
			"bn",
			"int8",
			"div"
		]
	`
	var l OpList
	err := json.Unmarshal([]byte(allOps), &l)
	assert.NoError(t, err)
	assert.Equal(t, OpList{
		&GetOp{1},
		&PopOp{},
		&ConstantOp{natural.BundleRef{builder.PrimitiveBundle(ds.String, element.Primitive{"Hello World"})}},
		&CastOp{ds.Ref(ds.Int32), ds.Ref(ds.Int64)},
		&NegOp{},
		&AndOp{},
		&OrOp{},
		&EqualsOp{ds.Ref(ds.Int32)},
		&ReturnOnFalseOp{},
		&BinaryOp{ds.Ref(ds.Int32), operations.AddCode},
		&BinaryOp{ds.Ref(ds.Float32), operations.SubCode},
		&BinaryOp{ds.Ref(ds.Float64), operations.MulCode},
		&BinaryOp{ds.Ref(ds.Int8), operations.DivCode},
	}, l)
}
