package serializer

import (
	"bufio"
	"bytes"
	"encoding/json"
	"github.com/pkg/errors"
)

type jsonDeserializer struct {
	reader          *bufio.Reader
	pendingElements []jsonElement
	pos             int
}

func (j *jsonDeserializer) nextElement() (jsonElement, error) {
	if j.pos >= len(j.pendingElements) {
		j.pos = 0
		elements, err := readJsonElements(j.reader)
		if err != nil {
			return jsonElement{}, err
		}
		j.pendingElements = elements
	}
	result := j.pendingElements[j.pos]
	j.pos += 1
	return result, nil
}

func (j *jsonDeserializer) peekNextElement() (jsonElement, error) {
	if j.pos >= len(j.pendingElements) {
		j.pos = 0
		elements, err := readJsonElements(j.reader)
		if stackOverflow != nil {
			return jsonElement{}, err
		}
		j.pendingElements = elements
	}
	return j.pendingElements[j.pos], nil
}

func (j *jsonDeserializer) DecodeArrayLen() (int, error) {
	e, err := j.nextElement()
	if err != nil {
		return 0, err
	}
	if e.elementType != Array {
		return 0, errors.New("No array type")
	}
	return e.value.(int), nil
}

func (j *jsonDeserializer) DecodeJson(destination interface{}) error {
	// this is tricky, we have to reserialize it...
	buf := bytes.Buffer{}
	err := j.decodePlainJson(&buf)
	if err != nil {
		return err
	}
	return json.Unmarshal(buf.Bytes(), destination)
}

func (j *jsonDeserializer) decodePlainJson(buf *bytes.Buffer) error {
	e, err := j.nextElement()
	if err != nil {
		return err
	}
	code := e.elementType

	if code == Array {
		return j.decodePlainJsonArray(buf, e.value.(int))
	}
	if code == Object {
		return j.decodePlainJsonObject(buf, e.value.(int))
	}
	if code == String {
		encoded, _ := json.Marshal(e.value.(string))
		_, err := buf.Write(encoded)
		return err
	}
	if code == OtherLiteral {
		_, err := buf.Write([]byte(e.value.(string)))
		return err
	}
	return errors.Errorf("Unsupported type %d", code)
}

func (j *jsonDeserializer) decodePlainJsonArray(buf *bytes.Buffer, arrayLength int) error {
	var err error
	if err = buf.WriteByte('['); err != nil {
		return err
	}
	for i := 0; i < arrayLength; i++ {
		if i > 0 {
			if err = buf.WriteByte(','); err != nil {
				return err
			}
		}
		if err = j.decodePlainJson(buf); err != nil {
			return err
		}
	}
	if err = buf.WriteByte(']'); err != nil {
		return err
	}
	return nil
}

func (j *jsonDeserializer) decodePlainJsonObject(buf *bytes.Buffer, objectLength int) error {
	var err error
	if err = buf.WriteByte('{'); err != nil {
		return err
	}
	for i := 0; i < objectLength; i++ {
		if i > 0 {
			if err = buf.WriteByte(','); err != nil {
				return err
			}
		}
		err := j.decodePlainJson(buf)
		if err != nil {
			return err
		}
		if err = buf.WriteByte(':'); err != nil {
			return err
		}
		if err = j.decodePlainJson(buf); err != nil {
			return err
		}
	}
	if err = buf.WriteByte('}'); err != nil {
		return err
	}
	return nil
}

func (j *jsonDeserializer) DecodeLiteral(i interface{}) error {
	e, err := j.nextElement()
	if err != nil {
		return err
	}
	if e.elementType != OtherLiteral {
		return errors.New("Expected literal")
	}
	return json.Unmarshal([]byte(e.value.(string)), i)
}

func (j *jsonDeserializer) DecodeInt8() (int8, error) {
	var i int8
	err := j.DecodeLiteral(&i)
	return i, err
}

func (j *jsonDeserializer) DecodeUint8() (uint8, error) {
	var i uint8
	err := j.DecodeLiteral(&i)
	return i, err
}

func (j *jsonDeserializer) DecodeInt32() (int32, error) {
	var i int32
	err := j.DecodeLiteral(&i)
	return i, err
}

func (j *jsonDeserializer) DecodeUint32() (uint32, error) {
	var i uint32
	err := j.DecodeLiteral(&i)
	return i, err
}

func (j *jsonDeserializer) DecodeInt64() (int64, error) {
	var i int64
	err := j.DecodeLiteral(&i)
	return i, err
}

func (j *jsonDeserializer) DecodeUint64() (uint64, error) {
	var i uint64
	err := j.DecodeLiteral(&i)
	return i, err
}

func (j *jsonDeserializer) DecodeString() (string, error) {
	e, err := j.nextElement()
	if err != nil {
		return "", err
	}
	if e.elementType != String {
		return "", errors.New("No string type")
	}
	return e.value.(string), nil
}

func (j *jsonDeserializer) DecodeFloat32() (float32, error) {
	var i float32
	err := j.DecodeLiteral(&i)
	return i, err
}

func (j *jsonDeserializer) DecodeFloat64() (float64, error) {
	var i float64
	err := j.DecodeLiteral(&i)
	return i, err
}

func (j *jsonDeserializer) DecodeBool() (bool, error) {
	var i bool
	err := j.DecodeLiteral(&i)
	return i, err
}

func (j *jsonDeserializer) DecodeBytes() ([]byte, error) {
	len, err := j.DecodeArrayLen()
	if err != nil {
		return nil, err
	}
	result := make([]byte, len)
	for i := 0; i < len; i++ {
		var b byte
		err := j.DecodeLiteral(&b)
		if err != nil {
			return nil, err
		}
		result[i] = b
	}
	return result, nil
}

func (j *jsonDeserializer) DecodeNil() error {
	e, err := j.nextElement()
	if err != nil {
		return err
	}
	if e.elementType != OtherLiteral {
		return errors.New("Expected literal")
	}
	// discard content
	return nil
}
