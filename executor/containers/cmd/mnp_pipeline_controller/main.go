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
package main

import (
	"coordinator/service/cli"
	"coordinator/service/mnp_pipeline"
	"flag"
	"fmt"
	"github.com/sirupsen/logrus"
	"os"
)

// Environment variable where the pipeline definition is.
var PipelineEnv = "PIPELINE"

func main() {
	options := flag.NewFlagSet("pipeline_controller", flag.ExitOnError)
	pipelineArg := options.String("pipeline", "", fmt.Sprintf("Pipeline definition, otherwise read from %s. Can start with @ to read from file.", PipelineEnv))
	port := options.Int("port", 9001, "Listening Port")

	options.Parse(os.Args[1:])

	var pipe mnp_pipeline.MnpPipeline
	err := cli.ParseJsonFile("pipeline", *pipelineArg, PipelineEnv, &pipe)
	if err != nil {
		println("Could not parse pipeline", err.Error())
		os.Exit(1)
	}
	logrus.Infof("Initializing %s Pipeline size: %d", pipe.Name, len(pipe.Steps))
	for _, p := range pipe.Steps {
		logrus.Infof(" - Step %s", p.Url)
	}

	server, err := mnp_pipeline.CreateServer(&pipe, *port)
	if err != nil {
		println("Could not create server", err.Error())
		os.Exit(2)
	}

	logrus.Infof("Start listening on %d", *port)
	err = server.ListenAndServe()
	if err != nil {
		println("Server failed", err.Error())
		os.Exit(3)
	}
}
