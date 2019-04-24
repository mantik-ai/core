package binaryadapter

import (
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
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
