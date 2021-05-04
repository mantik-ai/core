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
package mnp_pipeline

import (
	"encoding/json"
	"fmt"
	"github.com/pkg/errors"
	"github.com/sirupsen/logrus"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/serving"
	"gl.ambrosys.de/mantik/go_shared/serving/server"
	"gl.ambrosys.de/mantik/go_shared/util/httpext"
	"net/http"
)

type Server struct {
	httpext.ServerExt
	pipeline *MnpPipeline
	runner   *RequestRunner
}

func CreateServer(pipeline *MnpPipeline, port int) (*Server, error) {
	addr := fmt.Sprintf(":%d", port)
	var result = Server{
		pipeline: pipeline,
		ServerExt: httpext.ServerExt{
			Server: http.Server{Addr: addr},
		},
	}

	serveMux := http.NewServeMux()
	serveMux.HandleFunc("/", result.indexHandler)

	requestRunner, err := CreateRequestRunner(pipeline)
	if err != nil {
		return nil, err
	}

	applyHandler, err := server.GenerateTypedStreamHandler(
		requestRunner.Type().Input.Underlying,
		requestRunner.Type().Output.Underlying,
		requestRunner.Execute,
	)
	logrus.Debug("Initialized Request pipeline")
	logrus.Debug("Input:  ", requestRunner.Type().Input.ToJsonString())
	logrus.Debug("Output: ", requestRunner.Type().Output.ToJsonString())

	serveMux.HandleFunc("/type", result.typeHandler)
	serveMux.HandleFunc("/apply", applyHandler)
	result.ServerExt.Handler = serveMux

	return &result, nil
}

func (s *Server) indexHandler(w http.ResponseWriter, r *http.Request) {
	if r.URL.Path != "/" {
		// See docu of ServeMux
		http.Error(w, fmt.Sprintf("Path %s not found", r.URL.Path), 404)
		return
	}
	w.WriteHeader(200)
	info := fmt.Sprintf(
		`Mantik Pipeline Controller: %s
Available Calls:
- GET  /       This Page
- POST /apply  Execute Pipeline
- GET  /type   Return Type information
`, s.pipeline.Name,
	)
	w.Write([]byte(info))
}

func (s *Server) typeHandler(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(200)
	ft := serving.AlgorithmType{s.pipeline.InputType, s.pipeline.OutputType()}
	serialized, _ := json.Marshal(ft)
	w.Write(serialized)
}

func (s *Server) apply([]element.Element) ([]element.Element, error) {
	return nil, errors.New("Not implemented")
}
