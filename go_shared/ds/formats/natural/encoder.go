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
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/util/serializer"
)

type naturalEncoder struct {
	backend               serializer.SerializingBackend
	rootElementSerializer ElementSerializer
}

func (n naturalEncoder) Write(row element.Element) error {
	err := n.backend.NextRow()
	if err != nil {
		return err
	}
	return n.rootElementSerializer.Write(n.backend, row)
}

func (n naturalEncoder) Close() error {
	err := n.backend.Finish()
	if err != nil {
		return err
	}
	return n.backend.Flush()
}

/*
Create an encoder for natural data format.
*/
func CreateEncoder(format ds.DataType, backend serializer.SerializingBackend) (element.StreamWriter, error) {
	rootElementSerializer, err := LookupRootElementSerializer(format)
	if err != nil {
		return nil, errors.Wrap(err, "Could not create Root element serializer")
	}

	header := serializer.Header{ds.Ref(format)}
	err = backend.EncodeHeader(&header)

	if err != nil {
		return nil, err
	}

	_, isTabular := format.(*ds.TabularData)
	if isTabular {
		err = backend.StartTabularValues()
		if err != nil {
			return nil, err
		}
	}

	result := naturalEncoder{
		backend,
		rootElementSerializer,
	}
	return result, nil
}
