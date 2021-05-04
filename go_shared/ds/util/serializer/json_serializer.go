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
	"encoding/base64"
	"encoding/json"
	"io"
)

type jsonSerializer struct {
	destination io.Writer
	// A stack for sub elements (arrays, objects)
	stack []stackElement
	// A Meta has been written and must be closed
	hasHeader bool
	isTabular bool
	rowCount  int
}

func (j *jsonSerializer) push(data []byte) error {
	if _, err := j.destination.Write(data); err != nil {
		return err
	}
	if len(j.stack) > 0 {
		lastStack := j.stack[len(j.stack)-1]
		if err := lastStack.After(); err != nil {
			return err
		}
	}
	return nil
}

type stackElement interface {
	// is called after an element is written, may remove itself from the stack
	After() error
}

type arrayStack struct {
	serializer *jsonSerializer
	// Number of pending elements
	pending int
}

func (j *arrayStack) After() error {
	j.pending = j.pending - 1
	if j.pending > 0 {
		_, err := j.serializer.destination.Write([]byte(","))
		if err != nil {
			return err
		}
	} else {
		// last element
		_, err := j.serializer.destination.Write([]byte("]"))
		if err != nil {
			return err
		}
		// pop serializer from stack
		j.serializer.stack = j.serializer.stack[:len(j.serializer.stack)-1]
		// tell the upper one that this is finished
		if len(j.serializer.stack) > 0 {
			return j.serializer.stack[len(j.serializer.stack)-1].After()
		}

	}
	return nil
}

func (j *arrayStack) Done() bool {
	return true
}

func (j *jsonSerializer) EncodeHeader(h *Header) error {
	if j.hasHeader {
		panic("Meta may be encoded only once")
	}
	encodedData, err := h.Format.MarshalJSON()
	if err != nil {
		return err
	}
	_, err = j.destination.Write(
		[]byte(`{"type":`),
	)
	if err != nil {
		return err
	}
	_, err = j.destination.Write(encodedData)
	if err != nil {
		return err
	}
	_, err = j.destination.Write(
		[]byte(`,"value":`),
	)
	if err != nil {
		return err
	}
	j.hasHeader = true
	return nil
}

func (j *jsonSerializer) StartTabularValues() error {
	err := j.plainWrite("[")
	j.isTabular = true
	j.rowCount = 0
	return err
}

func (j *jsonSerializer) NextRow() error {
	if j.rowCount > 0 {
		err := j.plainWrite(",")
		if err != nil {
			return err
		}
	}
	j.rowCount += 1
	return nil
}

func (j *jsonSerializer) Finish() error {
	if j.isTabular {
		err := j.plainWrite("]")
		if err != nil {
			return err
		}
	}
	if j.hasHeader {
		return j.plainWrite("}")
	}
	return nil
}

func (j *jsonSerializer) EncodeArrayLen(l int) error {
	if l == 0 {
		return j.push([]byte("[]"))
	}
	j.stack = append(j.stack, &arrayStack{j, l})
	_, err := j.destination.Write([]byte("["))
	return err
}

func (j *jsonSerializer) EncodeJson(i interface{}) error {
	encoded, err := json.Marshal(i)
	if err != nil {
		return err
	}
	return j.push(encoded)
}

func (j *jsonSerializer) EncodeInt8(v int8) error {
	return j.EncodeJson(v)
}

func (j *jsonSerializer) EncodeUint8(v uint8) error {
	return j.EncodeJson(v)
}

func (j *jsonSerializer) EncodeInt32(v int32) error {
	return j.EncodeJson(v)
}

func (j *jsonSerializer) EncodeUint32(v uint32) error {
	return j.EncodeJson(v)
}

func (j *jsonSerializer) EncodeInt64(v int64) error {
	return j.EncodeJson(v)
}

func (j *jsonSerializer) EncodeUint64(v uint64) error {
	return j.EncodeJson(v)
}

func (j *jsonSerializer) EncodeString(s string) error {
	return j.EncodeJson(s)
}

func (j *jsonSerializer) EncodeFloat32(f float32) error {
	return j.EncodeJson(f)
}

func (j *jsonSerializer) EncodeFloat64(f float64) error {
	return j.EncodeJson(f)
}

func (j *jsonSerializer) EncodeBool(b bool) error {
	return j.EncodeJson(b)
}

func (j *jsonSerializer) EncodeBytes(bytes []byte) error {
	// Bytes only happens for Images and for this
	// We defined a base64 encoding in a string.
	err := j.plainWrite("\"")
	if err != nil {
		return err
	}
	encoder := base64.NewEncoder(base64.StdEncoding, j.destination)
	_, err = encoder.Write(bytes)
	if err != nil {
		return err
	}
	err = encoder.Close()
	if err != nil {
		return err
	}
	err = j.push([]byte("\""))
	return err
}

func (j *jsonSerializer) EncodeNil() error {
	return j.EncodeJson(nil)
}

func (j *jsonSerializer) Flush() error {
	// Nothing todo
	return nil
}

func (j *jsonSerializer) plainWrite(s string) error {
	_, err := j.destination.Write([]byte(s))
	return err
}
