package runner

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds/adapt"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/operations"
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
// Return value: true if the next instruction should be executed
type instruction func(c *context) bool

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
	for _, instr := range r.instructions {
		c := instr(&ctxt)
		if !c {
			return ctxt.s.elements, nil
		}
	}
	return ctxt.s.elements, nil
}

func locateInstruction(code ops.OpCode) (instruction, error) {
	switch v := code.(type) {
	case *ops.GetOp:
		return func(c *context) bool {
			c.s.push(c.arguments[v.Id])
			return true
		}, nil
	case *ops.ConstantOp:
		e := v.Value.Bundle.SingleValue()
		return func(c *context) bool {
			c.s.push(e)
			return true
		}, nil
	case *ops.PopOp:
		return func(c *context) bool {
			c.s.pop()
			return true
		}, nil
	case *ops.CastOp:
		cast, err := adapt.LookupCast(v.From.Underlying, v.To.Underlying)
		if err != nil {
			return nil, err
		}
		return func(c *context) bool {
			last := c.s.last()
			last, err = cast.Adapter(last)
			if err != nil {
				panic(err.Error())
			}
			c.s.setLast(last)
			return true
		}, nil
	case *ops.NegOp:
		return func(c *context) bool {
			last := c.s.last()
			value := last.(element.Primitive).X.(bool)
			updated := element.Primitive{!value}
			c.s.setLast(updated)
			return true
		}, nil
	case *ops.EqualsOp:
		op := operations.FindEqualsOperation(v.DataType.Underlying)
		return func(c *context) bool {
			right := c.s.pop()
			left := c.s.pop()
			result := element.Primitive{op(left, right)}
			c.s.push(result)
			return true
		}, nil
	case *ops.OrOp:
		return func(c *context) bool {
			right := c.s.pop().(element.Primitive).X.(bool)
			left := c.s.pop().(element.Primitive).X.(bool)
			result := right || left
			c.s.push(element.Primitive{result})
			return true
		}, nil
	case *ops.AndOp:
		return func(c *context) bool {
			right := c.s.pop().(element.Primitive).X.(bool)
			left := c.s.pop().(element.Primitive).X.(bool)
			result := right && left
			c.s.push(element.Primitive{result})
			return true
		}, nil
	case *ops.ReturnOnFalseOp:
		return func(c *context) bool {
			value := c.s.last().(element.Primitive).X.(bool)
			return value == true
		}, nil
	case *ops.BinaryOp:
		f, err := operations.FindBinaryFunction(v.Op, v.DataType.Underlying)
		if err != nil {
			return nil, err
		}
		return func(c *context) bool {
			right := c.s.pop()
			left := c.s.pop()
			c.s.push(f(left, right))
			return true
		}, nil
	case *ops.IsNullOp:
		return func(c *context) bool {
			last := c.s.last()
			p, isPrimitive := last.(element.Primitive)
			result := element.Primitive{X: isPrimitive && p.X == nil}
			c.s.setLast(result)
			return true
		}, nil
	}
	t := reflect.TypeOf(code)
	return nil, errors.Errorf("Unsupported operation %s", t.String())
}
