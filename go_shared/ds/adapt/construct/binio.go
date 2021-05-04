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
package construct

import (
	"encoding/binary"
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"io"
	"math"
)

// Writes packed elements into a binary stream
// Note: not threadsafe
type binaryWriter func(v interface{}, writer io.Writer) error

// Reads packed elements from a binary stream
// Note: not threadsafe
type binaryReader func(reader io.Reader) (interface{}, error)

// Looks up a binary writer
func lookupBinaryWriter(ft *ds.FundamentalType) (binaryWriter, error) {
	size, err := binarySize(ft)
	if err != nil {
		return nil, err
	}

	buf := make([]byte, size)
	switch ft {
	case ds.Uint8:
		return wrapWriter(buf, func(i interface{}) {
			buf[0] = byte(i.(uint8))
		})
	case ds.Int8:
		return wrapWriter(buf, func(i interface{}) {
			buf[0] = byte(i.(int8))
		})
	case ds.Uint32:
		return wrapWriter(buf, func(i interface{}) {
			binary.BigEndian.PutUint32(buf, i.(uint32))
		})
	case ds.Int32:
		return wrapWriter(buf, func(i interface{}) {
			binary.BigEndian.PutUint32(buf, uint32(i.(int32)))
		})
	case ds.Uint64:
		return wrapWriter(buf, func(i interface{}) {
			binary.BigEndian.PutUint64(buf, i.(uint64))
		})
	case ds.Int64:
		return wrapWriter(buf, func(i interface{}) {
			binary.BigEndian.PutUint64(buf, uint64(i.(int64)))
		})
	case ds.Float32:
		return wrapWriter(buf, func(i interface{}) {
			binary.BigEndian.PutUint32(buf, math.Float32bits(i.(float32)))
		})
	case ds.Float64:
		return wrapWriter(buf, func(i interface{}) {
			binary.BigEndian.PutUint64(buf, math.Float64bits(i.(float64)))
		})
	default:
		return nil, errors.Errorf("Unsupported type %s", ft.TypeName())
	}
}

func wrapWriter(buf []byte, packer func(interface{})) (binaryWriter, error) {
	return func(v interface{}, writer io.Writer) error {
		packer(v)
		_, err := writer.Write(buf)
		return err
	}, nil
}

func lookupBinaryReader(ft *ds.FundamentalType) (binaryReader, error) {
	size, err := binarySize(ft)
	if err != nil {
		return nil, err
	}

	buf := make([]byte, size)
	switch ft {
	case ds.Uint8:
		return wrapReader(buf, func() interface{} {
			return uint8(buf[0])
		})
	case ds.Int8:
		return wrapReader(buf, func() interface{} {
			return int8(buf[0])
		})
	case ds.Uint32:
		return wrapReader(buf, func() interface{} {
			return binary.BigEndian.Uint32(buf)
		})
	case ds.Int32:
		return wrapReader(buf, func() interface{} {
			return int32(binary.BigEndian.Uint32(buf))
		})
	case ds.Uint64:
		return wrapReader(buf, func() interface{} {
			return binary.BigEndian.Uint64(buf)
		})
	case ds.Int64:
		return wrapReader(buf, func() interface{} {
			return int64(binary.BigEndian.Uint64(buf))
		})
	case ds.Float32:
		return wrapReader(buf, func() interface{} {
			return math.Float32frombits(binary.BigEndian.Uint32(buf))
		})
	case ds.Float64:
		return wrapReader(buf, func() interface{} {
			return math.Float64frombits(binary.BigEndian.Uint64(buf))
		})
	default:
		return nil, errors.New("Unsupported type")
	}
}

func wrapReader(buf []byte, unpacker func() interface{}) (binaryReader, error) {
	return func(reader io.Reader) (i interface{}, e error) {
		_, err := reader.Read(buf)
		if err != nil {
			return nil, err
		}
		return unpacker(), nil
	}, nil
}

func binarySize(ft *ds.FundamentalType) (int, error) {
	var bufSize int
	switch ft {
	case ds.Uint8:
		bufSize = 1
	case ds.Int8:
		bufSize = 1
	case ds.Uint32:
		bufSize = 4
	case ds.Int32:
		bufSize = 4
	case ds.Uint64:
		bufSize = 8
	case ds.Int64:
		bufSize = 8
	case ds.Float32:
		bufSize = 4
	case ds.Float64:
		bufSize = 8
	default:
		return 0, errors.New("Could not figure out buf size")
	}
	return bufSize, nil
}
