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
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/mantik-ai/core/go_shared/ds/element"
	"github.com/mantik-ai/core/go_shared/ds/formats/natural"
)

type ElementMultiHashMap struct {
	keySerializer natural.ElementSerializer
	m             map[string][]*element.TabularRow
	size          int
}

func NewElementMultiHashMap(key *ds.TabularData) (*ElementMultiHashMap, error) {
	serializer, err := natural.LookupRootElementSerializer(key)
	if err != nil {
		return nil, err
	}
	return &ElementMultiHashMap{
		keySerializer: serializer,
		m:             make(map[string][]*element.TabularRow),
	}, nil
}

func (e *ElementMultiHashMap) Add(key *element.TabularRow, value *element.TabularRow) {
	keyBytes, err := natural.SerializeToBytes(e.keySerializer, key)
	if err != nil {
		panic("Could not serialize keys")
	}
	keyString := string(keyBytes)
	e.m[keyString] = append(e.m[keyString], value)
	e.size += 1
}

func (e *ElementMultiHashMap) Get(key *element.TabularRow) []*element.TabularRow {
	keyBytes, err := natural.SerializeToBytes(e.keySerializer, key)
	if err != nil {
		panic("Could not serialize keys")
	}
	keyString := string(keyBytes)
	return e.m[keyString]
}

func (e *ElementMultiHashMap) Gets(keyString string) []*element.TabularRow {
	return e.m[keyString]
}

func (e *ElementMultiHashMap) ForEach(f func(string, []*element.TabularRow) error) error {
	for k, values := range e.m {
		err := f(k, values)
		if err != nil {
			return err
		}
	}
	return nil
}

func (e *ElementMultiHashMap) Size() int {
	return e.size
}
