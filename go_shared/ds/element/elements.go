package element

import (
	"io"
)

const KIND_TABULAR_ROW = 1
const KIND_PRIMITIVE = 2
const KIND_IMAGE_ELEMENT = 3
const KIND_TENSOR_ELEMENT = 4
const KIND_EMBEDDED_TABULAR = 5

type Element interface {
	Kind() int
}

type TabularRow struct {
	Columns []Element
}

func (f *TabularRow) Kind() int {
	return KIND_TABULAR_ROW
}

// Note: primitive is using a value receiver (all others are using pointer receivers)
type Primitive struct {
	X interface{}
}

func (p Primitive) Kind() int {
	return KIND_PRIMITIVE
}

type ImageElement struct {
	Bytes []byte
}

func (i *ImageElement) Kind() int {
	return KIND_IMAGE_ELEMENT
}

type TensorElement struct {
	// Note: this will be s slice of underlying data!
	Values interface{}
}

func (t *TensorElement) Kind() int {
	return KIND_TENSOR_ELEMENT
}

type EmbeddedTabularElement struct {
	Rows []*TabularRow
}

func (e *EmbeddedTabularElement) Kind() int {
	return KIND_EMBEDDED_TABULAR
}

type StreamWriter interface {
	Write(row Element) error
	io.Closer
}

// Interface for something which reads streams
type StreamReader interface {
	// Error may be EOF.
	Read() (Element, error)
}

// Create a StreamReader for an element slice
func CreateSliceStreamReader(elements []Element) StreamReader {
	return &sliceStreamReader{elements, 0}
}

type sliceStreamReader struct {
	elements []Element
	pos      int
}

func (s *sliceStreamReader) Read() (Element, error) {
	if s.pos >= len(s.elements) {
		return nil, io.EOF
	}
	result := s.elements[s.pos]
	s.pos += 1
	return result, nil
}

// Read a stream reader until exhausted.
func ReadAllFromStreamReader(reader StreamReader) ([]Element, error) {
	var buf []Element
	for {
		e, err := reader.Read()
		if err == io.EOF {
			return buf, nil
		}
		if err != nil {
			return buf, err
		}
		buf = append(buf, e)
	}
}
