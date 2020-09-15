package ops

import (
	"encoding/json"
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/formats/natural"
	"gl.ambrosys.de/mantik/go_shared/ds/operations"
)

// The op codes for the interpreter
// For a better documentation look into the Scala Code

type OpList []OpCode

func (o *OpList) UnmarshalJSON(data []byte) error {
	var elements []json.RawMessage
	err := json.Unmarshal(data, &elements)
	if err != nil {
		return err
	}
	*o = OpList{}
	for len(elements) > 0 {
		code, remaining, err := ParseOpCode(elements)
		if err != nil {
			return err
		}
		elements = remaining
		*o = append(*o, code)
	}
	return nil
}

// Base interface for opcodes
// (As the interface is empty, everything would be an opcode, here it just exists To mark it as opcode)
type OpCode interface {
}

type GetOp struct {
	Id int
}

type ConstantOp struct {
	Value natural.BundleRef
}

type PopOp struct {
}

type CastOp struct {
	From ds.TypeReference
	To   ds.TypeReference
}

type NegOp struct {
}

type EqualsOp struct {
	DataType ds.TypeReference
}

type AndOp struct {
}

type OrOp struct {
}

type ReturnOnFalseOp struct {
}

type BinaryOp struct {
	DataType ds.TypeReference
	Op       operations.BinaryOperation
}

type IsNullOp struct {
}

/* Parse a single opcode, returns the opcode and the remaining raw messages. */
func ParseOpCode(in []json.RawMessage) (OpCode, []json.RawMessage, error) {
	if len(in) == 0 {
		return nil, nil, errors.New("Empty argument")
	}
	var code string
	err := json.Unmarshal(in[0], &code)
	if err != nil {
		return nil, nil, err
	}
	switch code {
	case "get":
		var op GetOp
		return parseOp1(in, &op, &op.Id)
	case "cnt":
		var op ConstantOp
		return parseOp1(in, &op, &op.Value)
	case "pop":
		var op PopOp
		return parseOp0(in, &op)
	case "cast":
		var op CastOp
		return parseOp2(in, &op, &op.From, &op.To)
	case "neg":
		var op NegOp
		return parseOp0(in, &op)
	case "eq":
		var op EqualsOp
		return parseOp1(in, &op, &op.DataType)
	case "or":
		var op OrOp
		return parseOp0(in, &op)
	case "and":
		var op AndOp
		return parseOp0(in, &op)
	case "retf":
		var op ReturnOnFalseOp
		return parseOp0(in, &op)
	case "bn":
		var op BinaryOp
		return parseOp2(in, &op, &op.DataType, &op.Op)
	case "isn":
		var op IsNullOp
		return parseOp0(in, &op)
	default:
		return nil, nil, errors.Errorf("Unknown operation %s", code)
	}
}

/** Parse a no argument operation. */
func parseOp0(in []json.RawMessage, code OpCode) (OpCode, []json.RawMessage, error) {
	return code, in[1:], nil
}

/** Parse a one argument operation. arg1 must be part of code */
func parseOp1(in []json.RawMessage, code OpCode, arg1 interface{}) (OpCode, []json.RawMessage, error) {
	if len(in) < 2 {
		return nil, nil, errors.New("Expected one argument")
	}
	err := json.Unmarshal(in[1], arg1)
	return code, in[2:], err
}

/** Parse a one argument operation. arg1 must be part of code */
func parseOp2(in []json.RawMessage, code OpCode, arg1 interface{}, arg2 interface{}) (OpCode, []json.RawMessage, error) {
	if len(in) < 3 {
		return nil, nil, errors.New("Expected two argument")
	}
	err := json.Unmarshal(in[1], arg1)
	if err != nil {
		return nil, nil, err
	}
	err = json.Unmarshal(in[2], arg2)
	return code, in[3:], err
}
