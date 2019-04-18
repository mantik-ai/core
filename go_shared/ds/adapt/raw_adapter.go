package adapt

type RawAdapter = func(interface{}) interface{}

var emptyRawAdapter RawAdapter = func(i interface{}) interface{} {
	return i
}

//go:generate sh -c "go run gen/raw_converters.go > raw_adapters_generated.go"
