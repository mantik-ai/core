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
