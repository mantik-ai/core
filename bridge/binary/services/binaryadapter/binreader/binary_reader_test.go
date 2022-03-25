/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package binreader

import (
	"fmt"
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/mantik-ai/core/go_shared/ds/element"
	"github.com/stretchr/testify/assert"
	"testing"
)

type PrimitiveSample struct {
	dataType     ds.DataType
	binaryValue  []byte
	littleEndian interface{}
	bigEndian    interface{}
}

var samples = []PrimitiveSample{
	{ds.Void, []byte{}, nil, nil},

	{ds.Int8, []byte{100}, int8(100), int8(100)},
	{ds.Uint8, []byte{100}, uint8(100), uint8(100)},

	{ds.Int32, []byte{0x0a, 0xbb, 0xcc, 0x0d}, int32(0x0dccbb0a), int32(0x0abbcc0d)},
	{ds.Int32, []byte{0x80, 0x00, 0x00, 0x00}, int32(0x80), int32(-2147483648)},
	{ds.Int32, []byte{0x00, 0x00, 0x00, 0x80}, int32(-2147483648), int32(0x80)},

	{ds.Uint32, []byte{0xaa, 0xbb, 0xcc, 0xdd}, uint32(0xddccbbaa), uint32(0xaabbccdd)},

	{ds.Int64, []byte{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08}, int64(0x0807060504030201), int64(0x0102030405060708)},
	{ds.Int64, []byte{0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}, int64(0x80), int64(-9223372036854775808)},
	{ds.Int64, []byte{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x80}, int64(-9223372036854775808), int64(0x80)},

	{ds.Uint64, []byte{0xa1, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7, 0xa8}, uint64(0xa8a7a6a5a4a3a2a1), uint64(0xa1a2a3a4a5a6a7a8)},

	{ds.Float32, []byte{0xa1, 0xa2, 0xa3, 0xa4}, float32(-7.09654857e-17), float32(-1.10208623e-18)},
	{ds.Float64, []byte{0xa1, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7, 0xa8}, float64(-7.683171020869066e-113), float64(-1.1661652944499428e-146)},

	// TODO Float32, Float64
}

func TestBigEndianSamples(t *testing.T) {
	for _, v := range samples {
		t.Run(fmt.Sprintf("Support %s (Big Endian)", v.dataType.TypeName()), func(t *testing.T) {
			byteSize, bigEndianReader, err := LookupReader(v.dataType, true)
			assert.NoError(t, err)
			assert.Equal(t, len(v.binaryValue), byteSize)
			parsed := bigEndianReader(v.binaryValue)
			assert.Equal(t, element.KIND_PRIMITIVE, parsed.Kind())
			assert.Equal(t, v.bigEndian, parsed.(element.Primitive).X)
		})
	}
}

func TestLittleEndianSamples(t *testing.T) {
	for _, v := range samples {
		t.Run(fmt.Sprintf("Support %s (Big Little Endian)", v.dataType.TypeName()), func(t *testing.T) {
			byteSize, bigEndianReader, err := LookupReader(v.dataType, false)
			assert.NoError(t, err)
			assert.Equal(t, len(v.binaryValue), byteSize)
			parsed := bigEndianReader(v.binaryValue)
			assert.Equal(t, element.KIND_PRIMITIVE, parsed.Kind())
			assert.Equal(t, v.littleEndian, parsed.(element.Primitive).X)
		})
	}
}

func TestImages(t *testing.T) {
	image := ds.Image{
		2,
		3,
		ds.OrderedComponentMap{
			ds.ImageComponentElement{ds.Red, ds.ImageComponent{ds.Ref(ds.Uint8)}},
			ds.ImageComponentElement{ds.Blue, ds.ImageComponent{ds.Ref(ds.Int32)}},
		},
		"plain",
	}
	byteLen, converter, err := LookupReader(&image, true)
	assert.NoError(t, err)
	assert.Equal(t, 2*3*(1+4), byteLen)
	block := make([]byte, 3*byteLen)
	for i := 0; i < len(block); i++ {
		block[i] = byte(i % 256)
	}
	put := converter(block)
	assert.Equal(t, element.KIND_IMAGE_ELEMENT, put.Kind())
	unpacked := put.(*element.ImageElement).Bytes
	assert.Equal(t, block[0:byteLen], unpacked)
	// it must contain a copy
	assert.Equal(t, byte(0), unpacked[0])
	block[0] = 1
	assert.Equal(t, byte(0), unpacked[0])

	image.Format = "jpeg"
	_, _, err = LookupReader(&image, true)
	assert.Error(t, err)

	image.Format = "plain"
	_, _, err = LookupReader(&image, true)
	assert.NoError(t, err)
}

func TestTensor(t *testing.T) {
	tensor := ds.Tensor{
		ComponentType: ds.Ref(ds.Int32),
		Shape:         []int{2, 3},
	}
	byteLen, converter, err := LookupReader(&tensor, true)
	assert.NoError(t, err)
	assert.Equal(t, 24, byteLen)
	bytes := make([]byte, 24)
	for i := 0; i < 24; i++ {
		bytes[i] = byte(i)
	}
	converted := converter(bytes)
	assert.Equal(t, element.KIND_TENSOR_ELEMENT, converted.Kind())
	unpacked := converted.(*element.TensorElement).Values
	assert.Equal(t,
		[]int32{
			0x00010203,
			0x04050607,
			0x08090a0b,
			0x0c0d0e0f,
			0x10111213,
			0x14151617,
		},
		unpacked)
}
