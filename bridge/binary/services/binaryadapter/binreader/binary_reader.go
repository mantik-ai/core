package binreader

import (
	"encoding/binary"
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"math"
	"reflect"
)

// Reads bytes and creates an element. The size should fit already
// Note: the input value is not stable, if you use the (sub) slice, then make a copy!
type BinaryReader func([]byte) element.Element

type primitiveValueReader func([]byte) interface{}

type primitiveTypeInfo struct {
	ByteLength         int
	BigEndianReader    primitiveValueReader
	LittleEndianReader primitiveValueReader
}

func makePrimitiveTypeInfo(len int, bigEndian primitiveValueReader, littleEndian primitiveValueReader) primitiveTypeInfo {
	return primitiveTypeInfo{
		len,
		bigEndian,
		littleEndian,
	}
}

// Converts a primitiveValueReader into a BinaryReader
func wrapReadingPrimitive(f primitiveValueReader) BinaryReader {
	return func(bytes []byte) element.Element {
		return element.Primitive{f(bytes)}
	}
}

func readUint8(bytes []byte) interface{} {
	return uint8(bytes[0])
}

func readInt8(bytes []byte) interface{} {
	return int8(bytes[0])
}

func readInt32Be(bytes []byte) interface{} {
	return int32(binary.BigEndian.Uint32(bytes))
}

func readInt32Le(bytes []byte) interface{} {
	return int32(binary.LittleEndian.Uint32(bytes))
}

func readUint32Be(bytes []byte) interface{} {
	return binary.BigEndian.Uint32(bytes)
}

func readUint32Le(bytes []byte) interface{} {
	return binary.LittleEndian.Uint32(bytes)
}

func readInt64Be(bytes []byte) interface{} {
	return int64(binary.BigEndian.Uint64(bytes))
}

func readInt64Le(bytes []byte) interface{} {
	return int64(binary.LittleEndian.Uint64(bytes))
}

func readUint64Be(bytes []byte) interface{} {
	return binary.BigEndian.Uint64(bytes)
}

func readUint64Le(bytes []byte) interface{} {
	return binary.LittleEndian.Uint64(bytes)
}

func readFloat32Be(bytes []byte) interface{} {
	return math.Float32frombits(binary.BigEndian.Uint32(bytes))
}

func readFloat32Le(bytes []byte) interface{} {
	return math.Float32frombits(binary.LittleEndian.Uint32(bytes))
}

func readFloat64Be(bytes []byte) interface{} {
	return math.Float64frombits(binary.BigEndian.Uint64(bytes))
}

func readFloat64Le(bytes []byte) interface{} {
	return math.Float64frombits(binary.LittleEndian.Uint64(bytes))
}

func readNil(bytes []byte) interface{} {
	return nil
}

var primitiveTypeInfos = map[ds.DataType]primitiveTypeInfo{
	ds.Void: makePrimitiveTypeInfo(0, readNil, readNil),

	ds.Int8:  makePrimitiveTypeInfo(1, readInt8, readInt8),
	ds.Uint8: makePrimitiveTypeInfo(1, readUint8, readUint8),

	ds.Int32:  makePrimitiveTypeInfo(4, readInt32Be, readInt32Le),
	ds.Uint32: makePrimitiveTypeInfo(4, readUint32Be, readUint32Le),

	ds.Int64:  makePrimitiveTypeInfo(8, readInt64Be, readInt64Le),
	ds.Uint64: makePrimitiveTypeInfo(8, readUint64Be, readUint64Le),

	ds.Float32: makePrimitiveTypeInfo(4, readFloat32Be, readFloat32Le),
	ds.Float64: makePrimitiveTypeInfo(8, readFloat64Be, readFloat64Le),
}

// Returns byte length, a reader or an error. */
func LookupReader(dataType ds.DataType, bigEndian bool) (int, BinaryReader, error) {
	if dataType.IsFundamental() {
		reader, ok := primitiveTypeInfos[dataType]
		if !ok {
			return 0, nil, errors.Errorf("Unsupported fundamental type %s", dataType.TypeName())
		}
		if bigEndian {
			return reader.ByteLength, wrapReadingPrimitive(reader.BigEndianReader), nil
		} else {
			return reader.ByteLength, wrapReadingPrimitive(reader.LittleEndianReader), nil
		}
	}

	imageType, isImage := dataType.(*ds.Image)
	if isImage {
		return LookupImageReader(imageType, bigEndian)
	}

	tensorType, isTensor := dataType.(*ds.Tensor)
	if isTensor {
		return LookupTensorReader(tensorType, bigEndian)
	}

	return 0, nil, errors.Errorf("Unsupported type %s", dataType.TypeName())
}

func LookupImageReader(image *ds.Image, bigEndian bool) (int, BinaryReader, error) {
	if !image.IsPlain() {
		return 0, nil, errors.New("only plain image format supported")
	}
	if image.Width <= 0 || image.Height <= 0 {
		return 0, nil, errors.New("Invalid image size")
	}
	// calculate size
	componentSizeSum := 0
	for _, c := range image.Components {
		size, _, err := LookupReader(c.Component.ComponentType.Underlying, bigEndian)
		if err != nil {
			return 0, nil, err
		}
		componentSizeSum += size
	}

	imageSize := image.Width * image.Height * componentSizeSum

	var reader BinaryReader = func(bytes []byte) element.Element {
		// the input is not stable, we have to make a copy of it
		buf := make([]byte, imageSize)
		copy(buf, bytes)
		return &element.ImageElement{buf}
	}
	return imageSize, reader, nil
}

func LookupTensorReader(tensor *ds.Tensor, bigEndian bool) (int, BinaryReader, error) {
	ft, ok := tensor.ComponentType.Underlying.(*ds.FundamentalType)
	if !ok {
		return 0, nil, errors.Errorf("Underlying type %s of tensor is not a fundamental type", tensor.ComponentType.Underlying.TypeName())
	}
	sliceType := reflect.SliceOf(ft.GoType)

	typeInfo, ok := primitiveTypeInfos[tensor.ComponentType.Underlying]
	if !ok {
		return 0, nil, errors.Errorf("Unsupported underlying type %s", tensor.ComponentType.Underlying.TypeName())
	}

	var elementReader primitiveValueReader
	if bigEndian {
		elementReader = typeInfo.BigEndianReader
	} else {
		elementReader = typeInfo.LittleEndianReader
	}
	singleElementByteLength := typeInfo.ByteLength

	elementCount := tensor.PackedElementCount()
	byteCount := elementCount * typeInfo.ByteLength
	var reader BinaryReader = func(bytes []byte) element.Element {
		elements := reflect.MakeSlice(sliceType, elementCount, elementCount)
		for i := 0; i < elementCount; i++ {
			subBlock := bytes[i*singleElementByteLength : (i+1)*singleElementByteLength]
			parsed := elementReader(subBlock)
			elements.Index(i).Set(reflect.ValueOf(parsed))
		}
		return &element.TensorElement{elements.Interface()}
	}
	return byteCount, reader, nil
}
