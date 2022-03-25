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
package natural

import (
	"github.com/mantik-ai/core/go_shared/ds/util/serializer"
	"github.com/pkg/errors"
)

type FundamentalCodec interface {
	Write(backend serializer.SerializingBackend, value interface{}) error
	WriteArray(backend serializer.SerializingBackend, value interface{}) error

	Read(backend serializer.DeserializingBackend) (interface{}, error)
	ReadArray(backend serializer.DeserializingBackend) (interface{}, error)

	Compare(left interface{}, right interface{}) int
	CompareArray(left interface{}, right interface{}) int
}

var badType = errors.New("Unexpected type")

//go:generate sh -c "go run gen/fundamentals.go > fundamentals_generated.go"

func (c boolCodec) Compare(left interface{}, right interface{}) int {
	lv := left.(bool)
	rv := right.(bool)
	if !lv && rv {
		return -1
	} else if lv && !rv {
		return 1
	} else {
		return 0
	}
}
