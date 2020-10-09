package element

import "io"

// A Buffer for Elements, can be read and written to
// Implements StreamReader and StreamWriter
type ElementBuffer struct {
	elements []Element
	closed   bool
	offset   int
}

// Create a buffer with elements already filled
func NewElementBuffer(elements []Element) *ElementBuffer {
	return &ElementBuffer{elements: elements}
}

func (e *ElementBuffer) Write(row Element) error {
	e.elements = append(e.elements, row)
	return nil
}

func (e *ElementBuffer) Close() error {
	e.closed = true
	return nil
}

func (e *ElementBuffer) Read() (Element, error) {
	if e.offset >= len(e.elements) {
		return nil, io.EOF
	}
	result := e.elements[e.offset]
	e.offset += 1
	return result, nil
}

func (e *ElementBuffer) Elements() []Element {
	return e.elements
}
