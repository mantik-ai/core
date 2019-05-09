package element

import (
	"gl.ambrosys.de/mantik/go_shared/ds"
	"math"
)

/* Returns some type samples (for tests). */
func FundamentalTypeSamples() []Bundle {
	return []Bundle{
		makeSample(ds.Uint8, uint8(200)),
		makeSample(ds.Int8, int8(-100)),
		makeSample(ds.Uint32, uint32(4000000000)),
		makeSample(ds.Int32, int32(-2000000000)),
		makeSample(ds.Uint64, uint64(math.MaxUint64)),
		makeSample(ds.Int64, int64(math.MinInt64)),
		makeSample(ds.Float32, float32(2.5)),
		makeSample(ds.Float64, float64(3435346345.32434324)),
		makeSample(ds.Bool, true),
		makeSample(ds.Void, nil),
		makeSample(ds.String, "Hello World"),
	}
}

func makeSample(dataType ds.DataType, i interface{}) Bundle {
	return Bundle{dataType, []Element{Primitive{i}}}
}
