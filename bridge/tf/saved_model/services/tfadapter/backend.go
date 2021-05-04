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
package tfadapter

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/serving"
)

type TensorflowBackend struct {
}

func (t *TensorflowBackend) LoadModel(payloadDir *string, mantikHeader serving.MantikHeader) (serving.Executable, error) {
	if payloadDir == nil {
		return nil, errors.New("Payload required")
	}
	model, err := LoadModel(*payloadDir)
	if err != nil {
		return nil, err
	}
	return model, err
}
