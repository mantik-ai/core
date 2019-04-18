package adapt_test

import (
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/adapt"
	"testing"
)

func TestIdentityConversion(t *testing.T) {
	converter, err := adapt.LookupRawAdapter(ds.Int32, ds.Int32)
	assert.NoError(t, err)
	assert.Equal(t, int32(4), converter(int32(4)))
}

func TestSimpleConversions(t *testing.T) {
	converter, err := adapt.LookupRawAdapter(ds.Uint8, ds.Int64)
	assert.NoError(t, err)
	assert.Equal(t, int64(254), converter(uint8(254)))
}

func TestFailing(t *testing.T) {
	_, err := adapt.LookupRawAdapter(ds.Uint32, ds.Int8)
	assert.Error(t, err)
	_, err = adapt.LookupRawAdapter(ds.String, ds.Int32)
	assert.Error(t, err)
}
