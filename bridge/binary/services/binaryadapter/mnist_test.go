package binaryadapter

import (
	"compress/gzip"
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/adapt"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"io"
	"os"
	"reflect"
	"testing"
)

func TestParseMnistDataset(t *testing.T) {
	executor, err := CreateBinaryExecutor("../../test/mnist")
	assert.NoError(t, err)
	reader := executor.Get()
	all, err := element.ReadAllFromStreamReader(reader)
	assert.NoError(t, err)
	assert.Equal(t, 10000, len(all))

	again, err := element.ReadAllFromStreamReader(executor.Get())
	assert.NoError(t, err)
	assert.Equal(t, 10000, len(again))
}

func TestParseMnistCorrectly(t *testing.T) {
	executor, err := CreateBinaryExecutor("../../test/mnist_train")
	assert.NoError(t, err)
	reader := executor.Get()
	all, err := element.ReadAllFromStreamReader(reader)
	assert.NoError(t, err)
	assert.Equal(t, 60000, len(all))

	imagesGz, err := os.Open("../../test/mnist_train/data/train-images-idx3-ubyte.gz")
	defer imagesGz.Close()
	assert.NoError(t, err)
	images, err := gzip.NewReader(imagesGz)
	assert.NoError(t, err)
	header := make([]byte, 16, 16)
	_, err = io.ReadFull(images, header)
	assert.NoError(t, err)
	imageData := make([]byte, 28*28, 28*28)
	for i := 0; i < 60000; i++ {
		_, err := io.ReadFull(images, imageData)
		assert.NoError(t, err)
		readImage := all[i].(*element.TabularRow).Columns[0].(*element.ImageElement)
		assert.Equal(t, imageData, readImage.Bytes)
		if !reflect.DeepEqual(imageData, readImage.Bytes) {
			break
		}
	}

	labelsGz, err := os.Open("../../test/mnist_train/data/train-labels-idx1-ubyte.gz")
	defer labelsGz.Close()
	labels, err := gzip.NewReader(labelsGz)
	assert.NoError(t, err)
	labelHeader := make([]byte, 8)
	_, err = io.ReadFull(labels, labelHeader)
	assert.NoError(t, err)
	singleLabel := make([]byte, 1, 1)
	for i := 0; i < 60000; i++ {
		n, err := io.ReadFull(labels, singleLabel)
		if err != nil {
			if err == io.EOF {
				assert.Equal(t, 1, n)
			} else {
				assert.NoError(t, err)
			}
		}
		labelParsed := uint8(singleLabel[0])
		readLabel := all[i].(*element.TabularRow).Columns[1].(element.Primitive).X.(uint8)
		assert.Equal(t, labelParsed, readLabel)
		if !reflect.DeepEqual(labelParsed, readLabel) {
			break
		}
	}
}

func TestMnistAsTensorConversion(t *testing.T) {
	executor, err := CreateBinaryExecutor("../../test/mnist")
	assert.NoError(t, err)
	reader := executor.Get()
	all, err := element.ReadAllFromStreamReader(reader)

	asTensor := ds.FromJsonStringOrPanic(`{"type":"tabular","columns":{"image":{"type":"tensor","componentType":"float32","shape":[28,28]},"label":"int32"}}`)
	cast, err := adapt.LookupAutoAdapter(executor.Type().Underlying, asTensor)
	assert.NoError(t, err)

	casted, err := adapt.ApplyForMany(cast, all)

	backCast, err := adapt.LookupCast(asTensor, executor.Type().Underlying) // Is not an auto adapter, as it looses precision (in our case not really)
	assert.NoError(t, err)
	backCasted, err := adapt.ApplyForMany(backCast.Adapter, casted)
	assert.Equal(t, all, backCasted)
}
