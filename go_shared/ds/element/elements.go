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
package element

import (
	"io"
)

const KIND_TABULAR_ROW = 1
const KIND_PRIMITIVE = 2
const KIND_IMAGE_ELEMENT = 3
const KIND_TENSOR_ELEMENT = 4
const KIND_EMBEDDED_TABULAR = 5
const KIND_ARRAY = 6
const KIND_STRUCT = 7

type Element interface {
	Kind() int
}

// An Element which can behave like a tabular data
// (EmbeddedTabular, Record)
type TabularLikeElement interface {
	// The Kind of the element
	Kind() int
	// The number of Rows
	RowCount() int
	// Returns an Element at the given position
	Get(row int, column int) Element
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

func (e *EmbeddedTabularElement) RowCount() int {
	return len(e.Rows)
}

func (e *EmbeddedTabularElement) Get(row int, column int) Element {
	return e.Rows[row].Columns[column]
}

type ArrayElement struct {
	Elements []Element
}

func (a *ArrayElement) Kind() int {
	return KIND_ARRAY
}

type StructElement struct {
	Elements []Element
}

func (s *StructElement) Kind() int {
	return KIND_STRUCT
}

func (s *StructElement) RowCount() int {
	return 1
}

func (s *StructElement) Get(row int, column int) Element {
	return s.Elements[column]
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
