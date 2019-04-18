package images

import (
	"bytes"
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"io/ioutil"
	"math"
	"testing"
)

var sampleImage = ds.Image{
	3, 5,
	[]ds.ImageComponentElement{
		{ds.Green, ds.ImageComponent{ds.Ref(ds.Uint8)}},
		{ds.Blue, ds.ImageComponent{ds.Ref(ds.Uint8)}},
		{ds.Green, ds.ImageComponent{ds.Ref(ds.Uint8)}},
	},
	nil,
}

var grayScaleImage = ds.Image{
	202, 65,
	[]ds.ImageComponentElement{
		{ds.Black, ds.ImageComponent{ds.Ref(ds.Float32)}},
	},
	nil,
}

func TestAdhocDecodePng(t *testing.T) {
	fileContent, err := ioutil.ReadFile("../../../test/resources/images/reactivecore.png")
	assert.NoError(t, err)
	converted, err := AdhocDecode(&sampleImage, bytes.NewBuffer(fileContent))
	assert.NoError(t, err)
	assert.Equal(t, 3*5*3, len(converted.Bytes))
	var min float64
	var max float64
	for _, b := range converted.Bytes {
		min = math.Min(min, float64(b))
		max = math.Max(max, float64(b))
	}
	assert.True(t, min == 0.0)
	assert.True(t, max > 100)
}

func TestAdhocDecodeGray(t *testing.T) {
	fileContent, err := ioutil.ReadFile("../../../test/resources/images/reactivecore.png")
	assert.NoError(t, err)
	converted, err := AdhocDecode(&grayScaleImage, bytes.NewBuffer(fileContent))
	assert.NoError(t, err)
	assert.Equal(t, 202*65*4, len(converted.Bytes))
}
