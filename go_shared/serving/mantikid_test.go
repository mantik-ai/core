package  serving

import (
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestFormatNamedMantikId(t *testing.T) {
	assert.Equal(t, "abc", FormatNamedMantikId("abc", nil, nil))
	assert.Equal(t, "acc/abc:version", FormatNamedMantikId("abc", sptr("acc"), sptr("version")))
	assert.Equal(t, "abc:version", FormatNamedMantikId("abc", nil, sptr("version")))
	assert.Equal(t, "acc/abc", FormatNamedMantikId("abc", sptr("acc"), nil))
	assert.Equal(t, "abc", FormatNamedMantikId("abc", sptr("library"), sptr("latest"))) // defaults
}

// Return a pointer to a string
// (workaround as we can't take it from constants)
func sptr(s string) * string {
	return &s
}