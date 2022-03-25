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
package runner

import (
	"github.com/mantik-ai/core/go_shared/ds/adapt"
	"github.com/mantik-ai/core/go_shared/ds/element"
	"github.com/mantik-ai/core/go_shared/ds/operations"
	"github.com/pkg/errors"
	"math"
	"reflect"
	"select/services/selectbridge/ops"
)

// Runs Programs, create with CreateRunner
type Runner struct {
	program      *Program
	instructions []instruction
}

// Execution Context
type context struct {
	arguments []element.Element
	s         stack
}

// A Single Instruction
// Return value: offset of the next instruction, math.MinInt32 for early exit
type instruction func(c *context) int32

func CreateRunner(program *Program) (*Runner, error) {
	instructions := make([]instruction, 0, len(program.Ops))
	for _, op := range program.Ops {
		i, err := locateInstruction(op)
		if err != nil {
			return nil, err
		}
		instructions = append(instructions, i)
	}
	return &Runner{
		program,
		instructions,
	}, nil
}

func (r *Runner) Run(args []element.Element) ([]element.Element, error) {
	if len(args) < r.program.Args {
		// argument list can be less
		return nil, errors.Errorf("Expected at least %d arguments, got %d", r.program.Args, len(args))
	}
	ctxt := context{
		args,
		CreateStack(r.program.StackInitDepth),
	}
	var currentInstruction int32 = 0
	instructionCount := int32(len(r.instructions))
	for currentInstruction < instructionCount {
		instr := r.instructions[currentInstruction]
		offset := instr(&ctxt)
		if offset == math.MinInt32 {
			return ctxt.s.elements, nil
		}
		currentInstruction = currentInstruction + offset
	}
	return ctxt.s.elements, nil
}

func locateInstruction(code ops.OpCode) (instruction, error) {
	switch v := code.(type) {
	case *ops.GetOp:
		return func(c *context) int32 {
			c.s.push(c.arguments[v.Id])
			return 1
		}, nil
	case *ops.ConstantOp:
		e := v.Value.Bundle.SingleValue()
		return func(c *context) int32 {
			c.s.push(e)
			return 1
		}, nil
	case *ops.PopOp:
		return func(c *context) int32 {
			c.s.pop()
			return 1
		}, nil
	case *ops.CastOp:
		cast, err := adapt.LookupCast(v.From.Underlying, v.To.Underlying)
		if err != nil {
			return nil, err
		}
		return func(c *context) int32 {
			last := c.s.last()
			last, err = cast.Adapter(last)
			if err != nil {
				panic(err.Error())
			}
			c.s.setLast(last)
			return 1
		}, nil
	case *ops.NegOp:
		return func(c *context) int32 {
			last := c.s.last()
			value := last.(element.Primitive).X.(bool)
			updated := element.Primitive{!value}
			c.s.setLast(updated)
			return 1
		}, nil
	case *ops.EqualsOp:
		op := operations.FindEqualsOperation(v.DataType.Underlying)
		return func(c *context) int32 {
			right := c.s.pop()
			left := c.s.pop()
			result := element.Primitive{op(left, right)}
			c.s.push(result)
			return 1
		}, nil
	case *ops.OrOp:
		return func(c *context) int32 {
			right := c.s.pop().(element.Primitive).X.(bool)
			left := c.s.pop().(element.Primitive).X.(bool)
			result := right || left
			c.s.push(element.Primitive{result})
			return 1
		}, nil
	case *ops.AndOp:
		return func(c *context) int32 {
			right := c.s.pop().(element.Primitive).X.(bool)
			left := c.s.pop().(element.Primitive).X.(bool)
			result := right && left
			c.s.push(element.Primitive{result})
			return 1
		}, nil
	case *ops.ReturnOnFalseOp:
		return func(c *context) int32 {
			value := c.s.last().(element.Primitive).X.(bool)
			if value {
				return 1
			} else {
				return math.MinInt32
			}
		}, nil
	case *ops.BinaryOp:
		f, err := operations.FindBinaryFunction(v.Op, v.DataType.Underlying)
		if err != nil {
			return nil, err
		}
		return func(c *context) int32 {
			right := c.s.pop()
			left := c.s.pop()
			c.s.push(f(left, right))
			return 1
		}, nil
	case *ops.IsNullOp:
		return func(c *context) int32 {
			last := c.s.last()
			p, isPrimitive := last.(element.Primitive)
			result := element.Primitive{X: isPrimitive && p.X == nil}
			c.s.setLast(result)
			return 1
		}, nil
	case *ops.UnpackNullableJump:
		return func(c *context) int32 {
			value := c.s.pop()
			p, isPrimitive := value.(element.Primitive)
			isNull := isPrimitive && p.X == nil
			if isNull {
				for i := int32(0); i < v.Drop; i++ {
					c.s.pop() // pop other values
				}
				// Leave null there
				c.s.push(value)
				return 1 + v.Offset
			} else {
				// no unpacking, golang SQL treats Nullable value as pure value
				c.s.push(value)
				return 1
			}
		}, nil
	case *ops.PackNullable:
		return func(c *context) int32 {
			// null op on golang
			return 1
		}, nil
	case *ops.ArrayGet:
		return func(c *context) int32 {
			index := c.s.pop().(element.Primitive).X.(int32) - 1 // Starting at index 1
			array := c.s.pop().(*element.ArrayElement).Elements
			if index < 0 || int(index) >= len(array) {
				c.s.push(element.Primitive{nil})
			} else {
				c.s.push(array[index])
			}
			return 1
		}, nil
	case *ops.ArraySize:
		return func(c *context) int32 {
			array := c.s.pop().(*element.ArrayElement).Elements
			c.s.push(element.Primitive{int32(len(array))})
			return 1
		}, nil
	case *ops.StructGet:
		return func(c *context) int32 {
			s := c.s.pop().(*element.StructElement).Elements
			c.s.push(s[v.Idx])
			return 1
		}, nil
	}
	t := reflect.TypeOf(code)
	return nil, errors.Errorf("Unsupported operation %s", t.String())
}
