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
package server

import (
	"github.com/mantik-ai/core/go_shared/serving"
)

type AlgorithmServer struct {
	*Server
	algorithm serving.ExecutableAlgorithm
}

func CreateAlgorithmServer(algorithm serving.ExecutableAlgorithm, address string) (*AlgorithmServer, error) {
	mainServer, err := CreateServer(address)
	s := AlgorithmServer{
		Server:    mainServer,
		algorithm: algorithm,
	}
	s.serveMux.HandleFunc("/type", CreateFixedJsonHandler(s.algorithm.Type()))
	applyHandler, err := GenerateTypedStreamHandler(
		algorithm.Type().Input.Underlying,
		algorithm.Type().Output.Underlying,
		algorithm.Execute,
	)
	if err != nil {
		return nil, err
	}
	s.serveMux.HandleFunc("/apply", applyHandler)

	return &s, err
}
