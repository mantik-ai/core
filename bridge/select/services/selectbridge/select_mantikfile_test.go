package selectbridge

import (
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/element/builder"
	"gl.ambrosys.de/mantik/go_shared/ds/formats/natural"
	"select/services/selectbridge/ops"
	"testing"
)

// Note: the program doesn't make much sense
var sampleFile string = `
type:
  input: int32
  output: string
selectProgram:
  "selector" : {
    "args" : 2,
    "retStackDepth" : 1,
    "stackInitDepth" : 2,
    "ops" : [
      "get",
      1,
      "cnt",
      {
        "type" : "int8",
        "value" : 1
      },
      "cast",
      "int8",
      "int32",
      "eq",
      "int32"
    ]
  }	
  "projector" : {
    "args" : 1,
    "retStackDepth" : 1,
    "stackInitDepth" : 1,
    "ops" : [
      "get",
      0
    ]
  }
`

func TestParseSelectMantikfile(t *testing.T) {
	mf, err := ParseSelectMantikfile([]byte(sampleFile))
	assert.NoError(t, err)
	assert.Equal(t, ds.Int32, mf.Type.Input.Underlying)
	assert.Equal(t, ds.String, mf.Type.Output.Underlying)
	s := mf.Program.Selector
	assert.Equal(t, 2, s.Args)
	assert.Equal(t, 1, s.RetStackDepth)
	assert.Equal(t, 2, s.StackInitDepth)
	assert.Equal(t, ops.OpList{
		&ops.GetOp{1},
		&ops.ConstantOp{natural.BundleRef{builder.PrimitiveBundle(ds.Int8, element.Primitive{int8(1)})}},
		&ops.CastOp{ds.Ref(ds.Int8), ds.Ref(ds.Int32)},
		&ops.EqualsOp{ds.Ref(ds.Int32)},
	}, s.Ops)

	p := mf.Program.Projector
	assert.Equal(t, 1, p.Args)
	assert.Equal(t, 1, p.StackInitDepth)
	assert.Equal(t, 1, p.RetStackDepth)
	assert.Equal(t, ops.OpList{
		&ops.GetOp{0},
	}, p.Ops)
}
