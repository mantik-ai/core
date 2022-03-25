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
package serializer

import (
	"bytes"
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/pkg/errors"
	"github.com/vmihailenco/msgpack"
	"io"
)

type BackendType = int

const BACKEND_MSGPACK BackendType = 1
const BACKEND_JSON BackendType = 2

// The header with type information
type Header struct {
	Format ds.TypeReference `json:"format"`
}

func CreateSerializingBackend(backendType BackendType, destination io.Writer) (SerializingBackend, error) {
	switch backendType {
	case BACKEND_MSGPACK:
		encoder := msgpack.NewEncoder(destination)
		return &msgPackSerializingBackend{Encoder: encoder}, nil
	case BACKEND_JSON:
		return &jsonSerializer{destination, nil, false, false, 0}, nil
	default:
		return nil, errors.New("Unsupported backend")
	}
}

func CreateDeserializingBackendForBytes(backendType BackendType, blob []byte) (DeserializingBackend, error) {
	// JSON Consumes always arrays, no need to convert to reader
	if backendType == BACKEND_JSON {
		return MakeJsonDeserializer(blob), nil
	} else {
		reader := bytes.NewReader(blob)
		return CreateDeserializingBackend(backendType, reader)
	}
}

func CreateDeserializingBackend(backendType BackendType, reader io.Reader) (DeserializingBackend, error) {
	switch backendType {
	case BACKEND_MSGPACK:
		return &msgPackDeserializingBackend{
			msgpack.NewDecoder(reader),
		}, nil
	case BACKEND_JSON:
		return MakeJsonDeserializerFromReader(reader)
	default:
		return nil, errors.Errorf("Unsupported backend %d", backendType)
	}
}

// Custom serializing backend, should make it possible to write a JSON Serializer too
// The interface is compatible to vmihailenco/msgpack with some extensions

type SerializingBackend interface {
	EncodeHeader(h *Header) error
	// Start writing tabular values (creates [ in JSON)
	StartTabularValues() error
	// A Next row is going to be written
	NextRow() error
	EncodeArrayLen(l int) error
	// Methods are like in msgpack.Encoder (for automatic deriving)
	EncodeInt8(v int8) error
	EncodeUint8(v uint8) error
	EncodeInt32(v int32) error
	EncodeUint32(v uint32) error
	EncodeInt64(v int64) error
	EncodeUint64(v uint64) error
	EncodeString(s string) error
	EncodeFloat32(f float32) error
	EncodeFloat64(f float64) error
	EncodeBool(b bool) error
	EncodeBytes(bytes []byte) error
	EncodeNil() error
	Flush() error
	// Finish the stream.
	Finish() error
}

type DeserializingBackend interface {
	// Decode the header
	DecodeHeader() (*Header, error)
	// Tell the backend, that's going to deserialize more than one root value
	StartReadingTabularValues() error
	// Check if the next value is a Nil without changing anything
	// Returns an error if there is no next element
	NextIsNil() (bool, error)
	DecodeArrayLen() (int, error)
	DecodeInt8() (int8, error)
	DecodeUint8() (uint8, error)
	DecodeInt32() (int32, error)
	DecodeUint32() (uint32, error)
	DecodeInt64() (int64, error)
	DecodeUint64() (uint64, error)
	DecodeString() (string, error)
	DecodeFloat32() (float32, error)
	DecodeFloat64() (float64, error)
	DecodeBool() (bool, error)
	DecodeBytes() ([]byte, error)
	DecodeNil() error
}
