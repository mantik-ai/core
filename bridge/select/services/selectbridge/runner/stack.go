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
