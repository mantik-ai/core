package runner

import "gl.ambrosys.de/mantik/go_shared/ds/element"

// An element stack, create using CreateStack
// Elements are growing to the right.
type stack struct {
	elements []element.Element
}

// Crates a stack with initial capacity
func CreateStack(capacity int) stack {
	return stack{
		make([]element.Element, 0, capacity),
	}
}

// Pops an element from the stack
func (s *stack) pop() element.Element {
	l := len(s.elements)
	e := s.elements[l-1]
	s.elements = s.elements[:l-1]
	return e
}

// Pushes an element from the stack
func (s *stack) push(e element.Element) {
	s.elements = append(s.elements, e)
}

// Returns last element
func (s *stack) last() element.Element {
	l := len(s.elements)
	e := s.elements[l-1]
	return e
}

// Overwrite the last element
func (s *stack) setLast(e element.Element) {
	l := len(s.elements)
	s.elements[l-1] = e
}
