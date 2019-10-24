package osext

import (
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestFileExists(t *testing.T) {
	assert.True(t, FileExists("../../test/resources/images/numbers"))// directory
	assert.True(t, FileExists("../../test/resources/images/numbers/0.jpg"))// file
	assert.False(t, FileExists("../../test/resources/images/numbers/notexisting.jpg"))
}

func TestIsDirectory(t *testing.T) {
	assert.True(t, IsDirectory("../../test/resources/images/numbers"))// directory
	assert.False(t, IsDirectory("../../test/resources/images/numbers/0.jpg"))// file
	assert.False(t, IsDirectory("../../test/resources/images/numbers/notexisting.jpg"))
}