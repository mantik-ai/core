package adapt

import (
	"encoding/binary"
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"reflect"
)

func lookupImageToTensor(from *ds.Image, to *ds.Tensor) (Adapter, error) {
	// only one component currently supported
	if len(from.Components) != 1 {
		return nil, errors.Errorf("Image to tensor conversion only supported for 1 component, got %d", len(from.Components))
	}
	// compatible componentTypes?
	imageComponentType, ok := from.Components[0].Component.ComponentType.Underlying.(*ds.FundamentalType)
	if !ok {
		return nil, errors.Errorf("Only fundamental image components supported")
	}
	tensorComponentType, ok := to.ComponentType.Underlying.(*ds.FundamentalType)
	if !ok {
		return nil, errors.Errorf("Only fundamental tensor components supported")
	}

	if from.Format != nil {
		// TODO: At least support conversion into string tensor, as this is usual.
		return nil, errors.Errorf("No support for image formats yet")
	}

	componentConverter, err := imageComponentConverter(imageComponentType, tensorComponentType)
	if err != nil {
		return nil, errors.Errorf(
			"Image to Tensor not available as component types are not convertable, from %s, to %s",
			ds.ToJsonString(imageComponentType),
			ds.ToJsonString(tensorComponentType),
		)
	}

	byteReader, err := lookupByteReader(imageComponentType)
	if err != nil {
		return nil, err
	}

	packedCount := to.PackedElementCount()

	if packedCount != from.Width*from.Height {
		return nil, errors.Errorf(
			"Tensor shape packed element count is incompatible to image packed element count %d, got %d",
			packedCount,
			from.Width*from.Height,
		)
	}

	var conversionFunction Adapter = func(in element.Element) (element.Element, error) {
		imageElement := in.(*element.ImageElement)
		resultBlock := reflect.MakeSlice(reflect.SliceOf(tensorComponentType.GoType), packedCount, packedCount)
		for i := 0; i < packedCount; i++ {
			resultBlock.Index(i).Set(
				reflect.ValueOf(componentConverter(byteReader(imageElement.Bytes, i))),
			)
		}
		result := element.TensorElement{resultBlock.Interface()}
		return &result, nil
	}
	return conversionFunction, nil
}

type byteReader func(in []byte, idx int) interface{}

func imageComponentConverter(from *ds.FundamentalType, to *ds.FundamentalType) (RawAdapter, error) {
	// Image components often convert from [0..255] into [0..1] (floating point).
	if from == to {
		return emptyRawAdapter, nil
	}
	var result RawAdapter

	if from == ds.Uint8 {
		if to == ds.Float32 {
			result = func(i interface{}) interface{} {
				return float32(i.(uint8)) / 255.0
			}
		} else if to == ds.Float64 {
			result = func(i interface{}) interface{} {
				return float64(i.(uint8)) / 255.0
			}
		}
	} else if from == ds.Float32 && to == ds.Uint8 {
		result = func(i interface{}) interface{} {
			in := i.(float32)
			if in < 0 {
				return uint8(0)
			} else if in >= 1.0 {
				return uint8(255)
			} else {
				return uint8(in * 255)
			}
		}
	} else if from == ds.Float64 && to == ds.Uint8 {
		result = func(i interface{}) interface{} {
			in := i.(float64)
			if in < 0 {
				return uint8(0)
			} else if in >= 1.0 {
				return uint8(255)
			} else {
				return uint8(in * 255)
			}
		}
	}

	if result != nil {
		return result, nil
	} else {
		return LookupRawAdapter(from, to)
	}
}

func lookupByteReader(componentType *ds.FundamentalType) (byteReader, error) {
	var result byteReader
	switch componentType {
	case ds.Uint8:
		result = func(in []byte, idx int) interface{} {
			return uint8(in[idx])
		}
	case ds.Int8:
		result = func(in []byte, idx int) interface{} {
			return int8(in[idx])
		}
	case ds.Int32:
		result = func(in []byte, idx int) interface{} {
			return int32(binary.BigEndian.Uint32(in[idx*4:]))
		}
	case ds.Uint32:
		result = func(in []byte, idx int) interface{} {
			return binary.BigEndian.Uint32(in[idx*4:])
		}
	case ds.Int64:
		result = func(in []byte, idx int) interface{} {
			return int64(binary.BigEndian.Uint64(in[idx*8:]))
		}
	case ds.Uint64:
		result = func(in []byte, idx int) interface{} {
			return uint64(binary.BigEndian.Uint64(in[idx*8:]))
		}
	default:
		return nil, errors.Errorf("Unsupported type for byte reading %s", componentType.TypeName())
	}
	return result, nil
}
