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
	"net/http"
)

type DataSetServer struct {
	*Server
	dataset serving.ExecutableDataSet
	encoder OutputEncoder
}

func CreateDataSetServer(dataset serving.ExecutableDataSet, address string) (*DataSetServer, error) {
	encoder, err := GenerateOutputEncoder(dataset.Type().Underlying)
	if err != nil {
		return nil, err
	}

	mainServer, err := CreateServer(address)
	s := DataSetServer{
		Server:  mainServer,
		dataset: dataset,
	}

	s.encoder = encoder

	s.serveMux.HandleFunc("/type", CreateFixedJsonHandler(s.dataset.Type()))
	s.serveMux.HandleFunc("/get", s.GetElements)
	return &s, err
}

func (d *DataSetServer) GetElements(w http.ResponseWriter, r *http.Request) {
	d.encoder(d.dataset.Get(), w, r)
}
