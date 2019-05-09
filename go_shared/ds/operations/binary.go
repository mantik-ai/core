package operations

import (
	"encoding/json"
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
)

type BinaryOperation int

const (
	AddCode BinaryOperation = iota
	SubCode
	MulCode
	DivCode
)

var binaryOperationCodes = []string{"add", "sub", "mul", "div"}

func (b BinaryOperation) MarshalJSON() ([]byte, error) {
	c := binaryOperationCodes[b]
	return json.Marshal(c)
}

func (b *BinaryOperation) UnmarshalJSON(data []byte) error {
	var s string
	err := json.Unmarshal(data, &s)
	if err != nil {
		return err
	}
	for i, c := range binaryOperationCodes {
		if c == s {
			*b = BinaryOperation(i)
			return nil
		}
	}
	return errors.Errorf("Unknown binary operation %s", s)
}

type BinaryFunction func(element.Element, element.Element) element.Element

func FindBinaryFunction(op BinaryOperation, dataType ds.DataType) (BinaryFunction, error) {
	ft, isFt := dataType.(*ds.FundamentalType)
	if isFt {
		return lookupFundamentalBinaryFunction(op, ft)
	}
	return nil, errors.New("Binary function only supported for fundamental operations")
}
