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
	"bytes"
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/mantik-ai/core/go_shared/ds/element"
	"github.com/mantik-ai/core/go_shared/ds/formats/natural"
)

// A Special set of elements which are all from the same type
// Not thread safe
// Used for Distinct generation
type ElementSet struct {
	serializer natural.ElementSerializer
	exists     byteSliceSet
	size       int
}

func NewElementSet(dt ds.DataType) (*ElementSet, error) {
	serializer, err := natural.LookupRootElementSerializer(dt)
	if err != nil {
		return nil, err
	}
	return &ElementSet{
		serializer: serializer,
		exists:     newByteSliceSet(),
	}, nil
}

// Returns true if element was added, false if already existing
func (e *ElementSet) Add(element element.Element) (bool, error) {
	encoded, err := natural.SerializeToBytes(e.serializer, element)
	if err != nil {
		return false, err
	}
	added := e.exists.Add(encoded)
	if added {
		e.size += 1
	}
	return added, nil
}

func (e *ElementSet) Size() int {
	return e.size
}

func (e *ElementSet) Clear() {
	e.size = 0
	e.exists = newByteSliceSet()
}

// A Simple set for byteSlices
type byteSliceSet struct {
	elements map[int][][]byte
}

func newByteSliceSet() byteSliceSet {
	return byteSliceSet{make(map[int][][]byte)}
}

// Adds an element to the byte slice set, returns true if it was not existing yet
func (b *byteSliceSet) Add(e []byte) bool {
	h := hash(e)
	rows, exists := b.elements[h]
	if !exists {
		b.elements[h] = [][]byte{e}
		return true
	} else {
		for _, row := range rows {
			if bytes.Equal(e, row) {
				return false
			}
		}
		b.elements[h] = append(rows, e)
		return true
	}
}

func hash(b []byte) int {
	x := 1
	for _, b := range b {
		x = (x * 31) + (int)(b)
	}
	return x
}
