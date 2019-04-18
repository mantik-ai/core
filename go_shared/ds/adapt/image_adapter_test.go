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
}

func TestImageConversionWithRescaling(t *testing.T) {
	// images have special rules for rescaling from uint8 to float / double
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
				"componentType": "float32"
			}
		`,
	)
	converter, err := LookupAutoAdapter(from, to)
	assert.NoError(t, err)

	input := &element.ImageElement{[]byte{0, 128, 255, 255, 0, 127}}
	output, err := converter(input)
	assert.NoError(t, err)
	casted := output.(*element.TensorElement)
	assert.Equal(t, []float32{0.0, 128 / 255.0, 1.0, 1.0, 0, 127 / 255.0}, casted.Values)
}

func TestRescalingConverter(t *testing.T) {
	int8ToFloat32, err := imageComponentConverter(ds.Uint8, ds.Float32)
	assert.NoError(t, err)
	assert.Equal(t, float32(0), int8ToFloat32(uint8(0)))
	assert.Equal(t, float32(128.0/255.0), int8ToFloat32(uint8(128)))
	assert.Equal(t, float32(1.0), int8ToFloat32(uint8(255)))

	int8ToFloat64, err := imageComponentConverter(ds.Uint8, ds.Float64)
	assert.NoError(t, err)
	assert.Equal(t, float64(0), int8ToFloat64(uint8(0)))
	assert.Equal(t, float64(128.0/255.0), int8ToFloat64(uint8(128)))
	assert.Equal(t, float64(1.0), int8ToFloat64(uint8(255)))

	float32ToUint8, err := imageComponentConverter(ds.Float32, ds.Uint8)
	assert.NoError(t, err)
	assert.Equal(t, uint8(0), float32ToUint8(float32(-0.1)))
	assert.Equal(t, uint8(255), float32ToUint8(float32(1.1)))
	assert.Equal(t, uint8(127), float32ToUint8(float32(0.5)))
	assert.Equal(t, uint8(255), float32ToUint8(float32(1.0)))
	assert.Equal(t, uint8(0), float32ToUint8(float32(0)))

	float64ToUint8, err := imageComponentConverter(ds.Float64, ds.Uint8)
	assert.NoError(t, err)
	assert.Equal(t, uint8(0), float64ToUint8(float64(-0.1)))
	assert.Equal(t, uint8(255), float64ToUint8(float64(1.1)))
	assert.Equal(t, uint8(127), float64ToUint8(float64(0.5)))
	assert.Equal(t, uint8(255), float64ToUint8(float64(1.0)))
	assert.Equal(t, uint8(0), float64ToUint8(float64(0)))
}
