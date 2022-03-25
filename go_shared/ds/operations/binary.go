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
package operations

import (
	"encoding/json"
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/mantik-ai/core/go_shared/ds/element"
	"github.com/pkg/errors"
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
