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
package selectbridge

import (
	"github.com/mantik-ai/core/go_shared/serving"
	"github.com/pkg/errors"
	"select/services/selectbridge/runner"
)

type SelectMantikHeader struct {
	serving.CombinerMantikHeader
	Program runner.TableGeneratorProgramRef `json:"program"`
	// Human readable statement
	Query *string
}

func ParseSelectMantikHeader(data []byte) (*SelectMantikHeader, error) {
	var mf SelectMantikHeader
	err := serving.UnmarshallMetaYaml(data, &mf)
	if err != nil {
		return nil, err
	}
	if (len(mf.Input) == 0) || (len(mf.Output) < 1) {
		return nil, errors.New("Invalid type")
	}
	if mf.Program.Underlying == nil {
		return nil, errors.New("No program found")
	}
	return &mf, err
}
