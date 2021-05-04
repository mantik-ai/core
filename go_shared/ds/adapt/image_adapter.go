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
package adapt

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/adapt/construct"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"io"
)

func lookupImageToTensor(from *ds.Image, to *ds.Tensor) (*Cast, error) {
	packedCount := to.PackedElementCount()

	if packedCount != from.Width*from.Height {
		return nil, errors.Errorf(
			"Tensor shape packed element count is incompatible to image packed element count %d, got %d",
			packedCount,
			from.Width*from.Height,
		)
	}

	imageReader, err := construct.CreateImageReader(from)
	if err != nil {
		return nil, err
	}
	tensorWriter, err := construct.CreateTensorWriter(to)
	if err != nil {
		return nil, err
	}
	return constructComponentCast(imageReader, tensorWriter)
}

func lookupTensorToImage(from *ds.Tensor, to *ds.Image) (*Cast, error) {
	packedCount := from.PackedElementCount()

	if packedCount != to.Width*to.Height {
		return nil, errors.Errorf(
			"Tensor shape packed element count is incompatible to image packed element count %d, got %d",
			packedCount,
			to.Width*to.Height,
		)
	}

	tensorReader, err := construct.CreateTensorReader(from)
	if err != nil {
		return nil, err
	}
	imageWriter, err := construct.CreateImageWriter(to)
	if err != nil {
		return nil, err
	}
	return constructComponentCast(tensorReader, imageWriter)
}

func lookupImageToImage(from *ds.Image, to *ds.Image) (*Cast, error) {
	if from.Width != to.Width || from.Height != to.Height {
		return nil, errors.New("Incompatible image size")
	}

	reader, err := construct.CreateImageReader(from)
	if err != nil {
		return nil, err
	}

	writer, err := construct.CreateImageWriter(to)
	if err != nil {
		return nil, err
	}

	return constructComponentCast(reader, writer)
}

func constructComponentCast(reader construct.ComponentReader, writer construct.ComponentWriter) (*Cast, error) {
	componentCast, err := LookupCast(reader.UnderlyingType(), writer.UnderlyingType())
	if err != nil {
		return nil, err
	}
	var adapter Adapter = func(input element.Element) (element.Element, error) {
		components, err := reader.ReadComponents(input)
		if err != nil {
			return nil, err
		}
		singleWriter := writer.Start()
		for {
			element, err := components.Read()
			if element != nil {
				translated, err := componentCast.Adapter(element)
				if err != nil {
					return nil, err
				}
				err = singleWriter.Write(translated)
				if err != nil {
					return nil, err
				}
			}
			if err == io.EOF {
				break
			}
			if err != nil {
				return nil, err
			}
		}
		err = singleWriter.Close()
		if err != nil {
			return nil, err
		}
		result := singleWriter.Result()
		return result, nil
	}
	return &Cast{
		From:    reader.Type(),
		To:      writer.Type(),
		Loosing: componentCast.Loosing,
		CanFail: componentCast.CanFail,
		Adapter: adapter,
	}, nil
}
