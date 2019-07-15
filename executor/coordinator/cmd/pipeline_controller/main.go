package main

import (
	"coordinator/service/cli"
	"coordinator/service/pipeline"
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

	var pipe pipeline.Pipeline
	err := cli.ParseJsonFile("pipeline", *pipelineArg, PipelineEnv, &pipe)
	if err != nil {
		println("Could not parse pipeline", err.Error())
		os.Exit(1)
	}
	logrus.Infof("Initializing %s Pipeline size: %d", pipe.Name, len(pipe.Steps))
	for _, p := range pipe.Steps {
		logrus.Infof(" - Step %s", p.Url)
	}

	server, err := pipeline.CreateServer(&pipe, *port)
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
