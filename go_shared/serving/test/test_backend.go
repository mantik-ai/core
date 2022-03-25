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

import "github.com/mantik-ai/core/go_shared/serving"

/* A Backend for testing. */
type TestBackend struct {
	Executable     serving.Executable
	Instantiations []TestBackendInstatiation
	Closed         bool
}

type TestBackendInstatiation struct {
	MantikHeader serving.MantikHeader
	Payload      *string
}

func NewTestBackend(executable serving.Executable) *TestBackend {
	return &TestBackend{
		Executable: executable,
	}
}

func (t *TestBackend) Shutdown() {
	t.Closed = true
}

func (t *TestBackend) LoadModel(payload *string, mantikHeader serving.MantikHeader) (serving.Executable, error) {
	t.Instantiations = append(t.Instantiations, TestBackendInstatiation{
		MantikHeader: mantikHeader,
		Payload:      payload,
	})
	return t.Executable, nil
}
