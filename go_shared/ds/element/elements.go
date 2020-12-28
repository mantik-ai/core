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

// Interface for something which reads element streams
type StreamReader interface {
	// Error may be EOF.
	Read() (Element, error)
}

type anonymousStreamReader struct {
	f func() (Element, error)
}

func (a *anonymousStreamReader) Read() (Element, error) {
	return a.f()
}

func NewStreamReader(f func() (Element, error)) StreamReader {
	return &anonymousStreamReader{f}
}

func NewFailedReader(err error) StreamReader {
	return NewStreamReader(func() (Element, error) {
		return nil, err
	})
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

/*
Returns an object which implements Reader and Writer
In contrast to ElementBuffer this piped reader blocks
on each read until something has been written.
*/
func NewPipeReaderWriter(size int) *PipeReaderWriter {
	pipe := &PipeReaderWriter{
		channel: make(chan Element, size),
	}
	return pipe
}

type PipeReaderWriter struct {
	channel chan Element
	err     error
}

func (p *PipeReaderWriter) Read() (Element, error) {
	element := <-p.channel
	if element == nil {
		close(p.channel)
		if p.err == nil {
			return nil, io.ErrUnexpectedEOF
		}
		return nil, p.err
	}
	return element, nil
}

func (p *PipeReaderWriter) Write(row Element) error {
	p.channel <- row
	return nil
}

func (p *PipeReaderWriter) Fail(err error) {
	p.err = err
	p.channel <- nil
}

func (p *PipeReaderWriter) Close() error {
	p.Fail(io.EOF)
	return nil
}
