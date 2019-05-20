package adapt

import (
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"testing"
)

func TestFundamentalCasts(t *testing.T) {
	simple, err := LookupCast(ds.Int32, ds.Int64)
	assert.NoError(t, err)
	assert.Equal(t, ds.Int32, simple.From)
	assert.Equal(t, ds.Int64, simple.To)
	assert.False(t, simple.Loosing)
	assert.False(t, simple.CanFail)

	c, err := simple.Adapter(element.Primitive{int32(14)})
	assert.NoError(t, err)
	assert.Equal(t, element.Primitive{int64(14)}, c)

	back, err := LookupCast(ds.Int64, ds.Int32)
	assert.NoError(t, err)
	assert.True(t, back.Loosing)
	assert.Equal(t, ds.Int64, back.From)
	assert.Equal(t, ds.Int32, back.To)
	assert.False(t, back.CanFail)
	c2, err := back.Adapter(element.Primitive{int64(-123)})
	assert.NoError(t, err)
	assert.Equal(t, element.Primitive{int32(-123)}, c2)
}

func TestNonLoosingIntToFloatCasts(t *testing.T) {
	c1, err := LookupCast(ds.Int8, ds.Float32)
	assert.NoError(t, err)
	assert.False(t, c1.Loosing)
	assert.False(t, c1.CanFail)
	casted, err := c1.Adapter(element.Primitive{int8(-3)})
	assert.NoError(t, err)
	assert.Equal(t, element.Primitive{float32(-3.0)}, casted)

	c2, err := LookupCast(ds.Uint8, ds.Float32)
	assert.NoError(t, err)
	assert.False(t, c2.Loosing)
	assert.False(t, c2.CanFail)
	casted, err = c2.Adapter(element.Primitive{uint8(200)})
	assert.NoError(t, err)
	assert.Equal(t, element.Primitive{float32(200.0)}, casted)

	c3, err := LookupCast(ds.Int32, ds.Float64)
	assert.NoError(t, err)
	assert.False(t, c3.Loosing)
	assert.False(t, c3.CanFail)
	casted, err = c3.Adapter(element.Primitive{int32(-400000)})
	assert.NoError(t, err)
	assert.Equal(t, element.Primitive{float64(-400000.0)}, casted)

	c4, err := LookupCast(ds.Uint32, ds.Float64)
	assert.NoError(t, err)
	assert.False(t, c4.Loosing)
	assert.False(t, c4.CanFail)
	casted, err = c4.Adapter(element.Primitive{uint32(400000)})
	assert.NoError(t, err)
	assert.Equal(t, element.Primitive{float64(400000.0)}, casted)
}

func TestToStringCast(t *testing.T) {
	testToString(t, ds.Int32, int32(4), "4")
	testToString(t, ds.Void, nil, "void")
	testToString(t, ds.Int64, int64(-100), "-100")
	testToString(t, ds.Float32, float32(2.5), "2.5")
	testToString(t, ds.Bool, true, "true")
}

func testToString(t *testing.T, ft *ds.FundamentalType, v interface{}, s string) {
	toStringCast, err := LookupCast(ft, ds.String)
	assert.NoError(t, err)
	assert.Equal(t, ft, toStringCast.From)
	assert.Equal(t, ds.String, toStringCast.To)
	assert.False(t, toStringCast.Loosing)
	assert.False(t, toStringCast.CanFail)
	adapted, err := toStringCast.Adapter(element.Primitive{v})
	assert.NoError(t, err)
	assert.Equal(t, element.Primitive{s}, adapted)
}

func TestStringConversions(t *testing.T) {
	for _, sample := range element.FundamentalTypeSamples() {
		toString, err := LookupCast(sample.Type, ds.String)
		assert.NoError(t, err)
		assert.False(t, toString.CanFail)
		assert.False(t, toString.Loosing)
		s, err := toString.Adapter(sample.SingleValue())
		assert.NotEmpty(t, s)
		assert.NoError(t, err)

		fromString, err := LookupCast(ds.String, sample.Type)
		assert.NoError(t, err)
		if sample.Type != ds.Void && sample.Type != ds.String {
			assert.True(t, fromString.CanFail)
		}
		assert.Equal(t, ds.String, fromString.From)
		assert.Equal(t, sample.Type, fromString.To)
		back, err := fromString.Adapter(s)
		assert.NoError(t, err)
		assert.Equal(t, sample.SingleValue(), back)
	}
}
