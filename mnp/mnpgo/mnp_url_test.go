package mnpgo

import (
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestParseMnpUrl(t *testing.T) {
	url := "mnp://myhost.name:4000/session1/1234"
	parsed, err := ParseMnpUrl(url)
	assert.NoError(t, err)
	assert.Equal(t, "myhost.name:4000", parsed.Address)
	assert.Equal(t, "session1", parsed.SessionId)
	assert.Equal(t, 1234, parsed.Port)

	assert.Equal(t, url, parsed.String())
}

func TestParseShortUrl(t *testing.T) {
	url := "mnp://myhost.name:4000/session1"
	parsed, err := ParseMnpUrl(url)
	assert.NoError(t, err)

	assert.Equal(t, "myhost.name:4000", parsed.Address)
	assert.Equal(t, "session1", parsed.SessionId)
	assert.Equal(t, -1, parsed.Port)

	assert.Equal(t, url, parsed.String())
}
