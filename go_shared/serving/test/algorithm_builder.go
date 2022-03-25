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
package test

import (
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/mantik-ai/core/go_shared/ds/element"
	"github.com/mantik-ai/core/go_shared/serving"
)

// Tools for building Test Algorithms

/* Build an algorithm mapping a from type to a to type. */
func CreateExecutableAlgorithm(from ds.DataType, to ds.DataType, apply func(element.Element) (element.Element, error)) serving.ExecutableAlgorithm {
	return &primitiveAlgorithm{
		from, to, nil, apply,
	}
}

func CreateExecutableRowWiseTabularAlgorithm(from *ds.TabularData, to *ds.TabularData, apply func([]element.Element) ([]element.Element, error)) serving.ExecutableAlgorithm {
	wrapped := func(in element.Element) (element.Element, error) {
		unpacked := in.(*element.TabularRow)
		result, err := apply(unpacked.Columns)
		if err != nil {
			return nil, err
		}
		packed := element.TabularRow{result}
		return &packed, nil
	}
	return &primitiveAlgorithm{
		from, to, nil, wrapped,
	}
}

func CreateFailingAlgorithm(from ds.DataType, to ds.DataType, err error) serving.ExecutableAlgorithm {
	if err == nil {
		panic("Error must be defined")
	}
	return &primitiveAlgorithm{
		from, to, err, nil,
	}
}

type primitiveAlgorithm struct {
	from ds.DataType
	to   ds.DataType
	// if set, the algorithm will always fail
	failing error
	apply   func(element.Element) (element.Element, error)
}

func (p *primitiveAlgorithm) Cleanup() {
	// nothing to do
}

func (p *primitiveAlgorithm) ExtensionInfo() interface{} {
	// nothing
	return nil
}

func (p *primitiveAlgorithm) Type() *serving.AlgorithmType {
	return &serving.AlgorithmType{
		ds.Ref(p.from),
		ds.Ref(p.to),
	}
}

func (p *primitiveAlgorithm) NativeType() *serving.AlgorithmType {
	return p.Type()
}

func (p *primitiveAlgorithm) Execute(rows []element.Element) ([]element.Element, error) {
	if p.failing != nil {
		return nil, p.failing
	}
	result := make([]element.Element, len(rows))
	for i, r := range rows {
		v, err := p.apply(r)
		if err != nil {
			return nil, err
		}
		result[i] = v
	}
	return result, nil
}
