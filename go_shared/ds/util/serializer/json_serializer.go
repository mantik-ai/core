package serializer

import (
	"encoding/json"
	"io"
)

type jsonSerializer struct {
	destination io.Writer
	// A stack for sub elements (arrays, objects)
	stack []stackElement
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
	err := j.EncodeArrayLen(len(bytes))
	if err != nil {
		return err
	}
	for i := 0; i < len(bytes); i++ {
		b := bytes[i]
		encoded, err := json.Marshal(b)
		if err != nil {
			return err
		}
		err = j.push(encoded)
		if err != nil {
			return err
		}
	}
	return nil
}

func (j *jsonSerializer) EncodeNil() error {
	return j.EncodeJson(nil)
}

func (j *jsonSerializer) Flush() error {
	// Nothing todo
	return nil
}
