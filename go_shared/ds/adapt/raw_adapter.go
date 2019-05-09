package adapt

import (
	"fmt"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"strconv"
)

type RawAdapter = func(interface{}) interface{}

var emptyRawAdapter RawAdapter = func(i interface{}) interface{} {
	return i
}

func toStringAdapter(ft *ds.FundamentalType) RawAdapter {
	var result RawAdapter
	switch ft {
	case ds.Uint8:
		result = func(i interface{}) interface{} {
			return strconv.Itoa(int(i.(uint8)))
		}
	case ds.Int8:
		result = func(i interface{}) interface{} {
			return strconv.Itoa(int(i.(int8)))
		}
	case ds.Uint32:
		result = func(i interface{}) interface{} {
			return strconv.FormatInt(int64(i.(uint32)), 10)
		}
	case ds.Int32:
		result = func(i interface{}) interface{} {
			return strconv.FormatInt(int64(i.(int32)), 10)
		}
	case ds.Uint64:
		result = func(i interface{}) interface{} {
			return strconv.FormatUint(i.(uint64), 10)
		}
	case ds.Int64:
		result = func(i interface{}) interface{} {
			return strconv.FormatInt(i.(int64), 10)
		}
	case ds.Float32:
		result = func(i interface{}) interface{} {
			return strconv.FormatFloat(float64(i.(float32)), 'G', -1, 32)
		}
	case ds.Float64:
		result = func(i interface{}) interface{} {
			return strconv.FormatFloat(i.(float64), 'G', -1, 64)
		}
	case ds.Bool:
		result = func(i interface{}) interface{} {
			return strconv.FormatBool(i.(bool))
		}
	case ds.Void:
		result = func(i interface{}) interface{} {
			return "void"
		}
	case ds.String:
		result = func(i interface{}) interface{} {
			return i
		}
	default:
		panic(fmt.Sprintf("No string serializer found for %s", ft.TypeName()))
	}
	return result
}

func fromStringAdapter(ft *ds.FundamentalType) Adapter {
	var result Adapter
	switch ft {
	case ds.Uint8:
		result = func(e element.Element) (element.Element, error) {
			s := e.(element.Primitive).X.(string)
			i, err := strconv.Atoi(s)
			return element.Primitive{uint8(i)}, err
		}
	case ds.Int8:
		result = func(e element.Element) (element.Element, error) {
			s := e.(element.Primitive).X.(string)
			i, err := strconv.Atoi(s)
			return element.Primitive{int8(i)}, err
		}
	case ds.Uint32:
		result = func(e element.Element) (element.Element, error) {
			s := e.(element.Primitive).X.(string)
			i, err := strconv.ParseUint(s, 10, 32)
			return element.Primitive{uint32(i)}, err
		}
	case ds.Int32:
		result = func(e element.Element) (element.Element, error) {
			s := e.(element.Primitive).X.(string)
			i, err := strconv.ParseInt(s, 10, 32)
			return element.Primitive{int32(i)}, err
		}
	case ds.Uint64:
		result = func(e element.Element) (element.Element, error) {
			s := e.(element.Primitive).X.(string)
			i, err := strconv.ParseUint(s, 10, 64)
			return element.Primitive{uint64(i)}, err
		}
	case ds.Int64:
		result = func(e element.Element) (element.Element, error) {
			s := e.(element.Primitive).X.(string)
			i, err := strconv.ParseInt(s, 10, 64)
			return element.Primitive{int64(i)}, err
		}
	case ds.Float32:
		result = func(e element.Element) (element.Element, error) {
			s := e.(element.Primitive).X.(string)
			i, err := strconv.ParseFloat(s, 32)
			return element.Primitive{float32(i)}, err
		}
	case ds.Float64:
		result = func(e element.Element) (element.Element, error) {
			s := e.(element.Primitive).X.(string)
			i, err := strconv.ParseFloat(s, 64)
			return element.Primitive{float64(i)}, err
		}
	case ds.Bool:
		result = func(e element.Element) (element.Element, error) {
			s := e.(element.Primitive).X.(string)
			i, err := strconv.ParseBool(s)
			return element.Primitive{i}, err
		}
	case ds.Void:
		result = func(e element.Element) (element.Element, error) {
			return element.Primitive{nil}, nil
		}
	case ds.String:
		result = func(e element.Element) (element.Element, error) {
			return e, nil
		}
	default:
		panic(fmt.Sprintf("No string serializer found for %s", ft.TypeName()))
	}
	return result
}

//go:generate sh -c "go run gen/raw_converters.go > raw_adapters_generated.go"
