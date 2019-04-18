package tfadapter

import (
	"github.com/pkg/errors"
	"github.com/tensorflow/tensorflow/tensorflow/go"
	"gl.ambrosys.de/mantik/go_shared/ds"
)

func convertToDs(dtype string) (*ds.FundamentalType, error) {
	var result *ds.FundamentalType = nil
	switch dtype {
	case "DT_FLOAT":
		result = ds.Float32
	case "DT_DOUBLE":
		result = ds.Float64
	case "DT_INT32":
		result = ds.Int32
	case "DT_UINT32":
		result = ds.Uint32
	case "DT_INT64":
		result = ds.Int64
	case "DT_UINT64":
		result = ds.Uint64
	case "DT_INT8":
		result = ds.Int8
	case "DT_UINT8":
		result = ds.Uint8
	case "DT_STRING":
		result = ds.String
	case "DT_BOOL":
		result = ds.Bool
	default:
		return nil, errors.Errorf("Unsupported tensor flow type %s", dtype)
	}
	return result, nil
}

func convertToTensorFlow(fundamentalType *ds.FundamentalType) (tensorflow.DataType, error) {
	var result tensorflow.DataType
	switch fundamentalType {
	case ds.Int8:
		result = tensorflow.Int8
	case ds.Uint8:
		result = tensorflow.Uint8
	case ds.Int32:
		result = tensorflow.Int32
	case ds.Uint32:
		result = tensorflow.Uint32
	case ds.Int64:
		result = tensorflow.Int64
	case ds.Uint64:
		result = tensorflow.Uint64
	case ds.Float32:
		result = tensorflow.Float
	case ds.Float64:
		result = tensorflow.Double
	case ds.Bool:
		result = tensorflow.Bool
	case ds.String:
		result = tensorflow.String
	default:
		return result, errors.Errorf("Unsupported type %s", fundamentalType.TypeName())
	}
	return result, nil
}
