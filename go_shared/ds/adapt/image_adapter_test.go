package adapt

import (
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"testing"
)

func TestImageToTensorConversion(t *testing.T) {
	from := ds.FromJsonStringOrPanic(
		`
		{
			"width": 2,
			"height": 3,
			"type": "image",
			"components": {
				"black": {
					"componentType": "uint8"
				}
			}
		}
		`,
	)

	to := ds.FromJsonStringOrPanic(
		`
			{
				"type": "tensor",
				"shape": [6],
				"componentType": "uint8"
			}
		`,
	)
	converter, err := LookupAutoAdapter(from, to)
	assert.NoError(t, err)

	input := &element.ImageElement{[]byte{1, 2, 3, 4, 5, 6}}
	output, err := converter(input)
	assert.NoError(t, err)
	casted := output.(*element.TensorElement)
	assert.Equal(t, []uint8{1, 2, 3, 4, 5, 6}, casted.Values)
}

func TestImageConversionToDoubleShapeTensor(t *testing.T) {
	from := ds.FromJsonStringOrPanic(
		`
		{
			"width": 2,
			"height": 3,
			"type": "image",
			"components": {
				"black": {
					"componentType": "uint8"
				}
			}
		}
		`,
	)

	to := ds.FromJsonStringOrPanic(
		`
			{
				"type": "tensor",
				"shape": [3,2],
				"componentType": "uint8"
			}
		`,
	)
	converter, err := LookupAutoAdapter(from, to)
	assert.NoError(t, err)

	input := &element.ImageElement{[]byte{1, 2, 3, 4, 5, 6}}
	output, err := converter(input)
	assert.NoError(t, err)
	casted := output.(*element.TensorElement)
	assert.Equal(t, []uint8{1, 2, 3, 4, 5, 6}, casted.Values)
}

func TestImageConversionWithSubTypeChanges(t *testing.T) {
	from := ds.FromJsonStringOrPanic(
		`
		{
			"width": 2,
			"height": 3,
			"type": "image",
			"components": {
				"black": {
					"componentType": "int32"
				}
			}
		}
		`,
	)

	to := ds.FromJsonStringOrPanic(
		`
			{
				"type": "tensor",
				"shape": [6],
				"componentType": "int64"
			}
		`,
	)
	converter, err := LookupAutoAdapter(from, to)
	assert.NoError(t, err)

	input := &element.ImageElement{[]byte{
		1, 2, 3, 4, // 1st = 16909060 (big endian)
		5, 6, 7, 8, // 2nd
		9, 10, 11, 12, // 3rd
		13, 14, 15, 16, // 4th
		17, 18, 19, 20, // 5th
		21, 22, 23, 24, // 6th
	}}
	output, err := converter(input)
	assert.NoError(t, err)
	casted := output.(*element.TensorElement)
	assert.Equal(t, []int64{16909060, 84281096, 151653132, 219025168, 286397204, 353769240}, casted.Values)

	reverse, err := LookupCast(to, from)
	assert.NoError(t, err)
	assert.True(t, reverse.Loosing)
	assert.False(t, reverse.CanFail)

	back, err := reverse.Adapter(output)
	assert.NoError(t, err)
	assert.Equal(t, input, back)
}

func TestImageToImageConversion(t *testing.T) {
	from := ds.FromJsonStringOrPanic(
		`
		{
			"width": 2,
			"height": 3,
			"type": "image",
			"components": {
				"black": {
					"componentType": "int32"
				}
			}
		}
		`,
	)

	to := ds.FromJsonStringOrPanic(
		`
		{
			"width": 2,
			"height": 3,
			"type": "image",
			"components": {
				"black": {
					"componentType": "int64"
				}
			}
		}
	`)
	converter, err := LookupAutoAdapter(from, to)
	assert.NoError(t, err)

	input := &element.ImageElement{[]byte{
		1, 2, 3, 4, // 1st = 16909060 (big endian)
		5, 6, 7, 8, // 2nd
		9, 10, 11, 12, // 3rd
		13, 14, 15, 16, // 4th
		17, 18, 19, 20, // 5th
		21, 22, 23, 24, // 6th
	}}
	expected := &element.ImageElement{[]byte{
		0, 0, 0, 0, 1, 2, 3, 4, // 1st = 16909060 (big endian)
		0, 0, 0, 0, 5, 6, 7, 8, // 2nd
		0, 0, 0, 0, 9, 10, 11, 12, // 3rd
		0, 0, 0, 0, 13, 14, 15, 16, // 4th
		0, 0, 0, 0, 17, 18, 19, 20, // 5th
		0, 0, 0, 0, 21, 22, 23, 24, // 6th
	}}
	output, err := converter(input)
	assert.NoError(t, err)
	assert.Equal(t, expected, output)
}
