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
	"context"
	"github.com/google/uuid"
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/mantik-ai/core/go_shared/ds/element"
	"github.com/mantik-ai/core/go_shared/ds/formats/natural"
	"github.com/mantik-ai/core/go_shared/ds/util/serializer"
	"github.com/mantik-ai/core/go_shared/serving"
	"github.com/mantik-ai/core/go_shared/serving/server"
	"github.com/mantik-ai/core/mnp/mnpgo"
	"github.com/mantik-ai/core/mnp/mnpgo/client"
	"github.com/pkg/errors"
	"github.com/sirupsen/logrus"
	"golang.org/x/sync/errgroup"
	"io"
	"sync"
)

type RequestRunner struct {
	pipeline *MnpPipeline
	// Encodes Input into Mantik Format
	inputSerializer natural.ElementSerializer
	// Decodes Output from Mantik Format
	outputDeserializer natural.ElementDeserializer
	// Lazy initialized mnpClients
	clientSessions []*client.ClientSession
	mutex          sync.Mutex
}

// Port Configuration assumed for all Nodes
var assumedPortConfig = mnpgo.PortConfiguration{
	Inputs:  []mnpgo.InputPortConfiguration{{ContentType: server.MimeMantikBundle}},
	Outputs: []mnpgo.OutputPortConfiguration{{ContentType: server.MimeMantikBundle, DestinationUrl: ""}},
}

func CreateRequestRunner(pipeline *MnpPipeline) (*RequestRunner, error) {
	inputSerializer, err := natural.LookupRootElementSerializer(pipeline.InputType.Underlying)
	if err != nil {
		return nil, err
	}
	outputDeserializer, err := natural.LookupRootElementDeserializer(pipeline.OutputType.Underlying)
	if err != nil {
		return nil, err
	}
	return &RequestRunner{
		pipeline,
		inputSerializer,
		outputDeserializer,
		make([]*client.ClientSession, len(pipeline.Steps), len(pipeline.Steps)),
		sync.Mutex{},
	}, nil
}

// Request Runner implements ExecutableAlgorithm

func (r *RequestRunner) Cleanup() {
	// Nothing to do
}

func (r *RequestRunner) ExtensionInfo() interface{} {
	return nil
}

func (r *RequestRunner) Type() *serving.AlgorithmType {
	return &serving.AlgorithmType{
		Input:  r.pipeline.InputType,
		Output: r.pipeline.OutputType,
	}
}

func (r *RequestRunner) NativeType() *serving.AlgorithmType {
	return r.Type()
}

/** Run a request. */
func (r *RequestRunner) Execute(input []element.Element) ([]element.Element, error) {
	if len(r.pipeline.Steps) == 0 {
		// Empty case, just return data.
		return input, nil
	}

	inputReader, err := r.buildInputReader(input)
	if err != nil {
		return nil, errors.WithStack(err)
	}

	var currentReader = inputReader

	taskId := uuid.New().String()

	errgroup := errgroup.Group{}

	// Readers we got from MnpNodes (for error handling)
	var nodeReaders []io.Reader

	for stepId, _ := range r.pipeline.Steps {
		nextReader, err := r.callMnpNode(&errgroup, taskId, stepId, currentReader)
		if err != nil {
			// there are still open readers. let's close them all.
			throwAwayReaders(nodeReaders)
			return nil, err
		}
		nodeReaders = append(nodeReaders, nextReader)
		currentReader = nextReader
	}

	resultRows, err := r.decodeOutput(currentReader, r.pipeline.OutputType.Underlying)
	if err != nil {
		return nil, errors.WithStack(err)
	}

	err = errgroup.Wait()
	if err != nil {
		throwAwayReaders(nodeReaders)
		return nil, err
	}

	return resultRows, nil
}

// Consume Reader until all is consumed. Ignores errors
func throwAwayReaders(readers []io.Reader) {
	for _, r := range readers {
		go func() {
			throwAwayReader(r)
		}()
	}
}

func throwAwayReader(reader io.Reader) {
	buf := make([]byte, 8192, 8192)
	for {
		_, err := reader.Read(buf)
		if err != nil {
			return
		}
	}
}

func (r *RequestRunner) callMnpNode(group *errgroup.Group, taskId string, stepId int, input io.Reader) (io.Reader, error) {
	client, err := r.ensureMnpClientSession(stepId)
	if err != nil {
		return nil, errors.WithStack(err)
	}
	outReader, outWriter := io.Pipe()
	// Das hier funktioniert nicht so einfach!
	group.Go(func() error {
		err = client.RunTask(context.Background(), taskId, []io.Reader{input}, []io.WriteCloser{outWriter})
		if err != nil {
			outReader.Close()
			outWriter.Close()
			return errors.WithStack(err)
		}
		return err
	})
	return outReader, nil
}

func (r *RequestRunner) ensureMnpClientSession(stepId int) (*client.ClientSession, error) {
	r.mutex.Lock()
	defer r.mutex.Unlock()

	clientSession := r.clientSessions[stepId]
	if clientSession != nil {
		return clientSession, nil
	}

	step := r.pipeline.Steps[stepId]
	parsedUrl, err := mnpgo.ParseMnpUrl(step.Url)
	if err != nil {
		return nil, errors.Wrapf(err, "Could not parse MNP Url %s", step.Url)
	}
	logrus.Infof("Connecting to %s", parsedUrl.String())
	mnpClient, err := client.ConnectClient(parsedUrl.Address)
	if err != nil {
		return nil, errors.Wrap(err, "Could not connect")
	}
	aboutResponse, err := mnpClient.About()
	if err != nil {
		mnpClient.Close()
		return nil, errors.Wrap(err, "Could not send about")
	}
	logrus.Infof("Connected to %s, %s", parsedUrl.String(), aboutResponse.Name)
	clientSession = mnpClient.JoinSession(parsedUrl.SessionId, &assumedPortConfig)
	r.clientSessions[stepId] = clientSession

	return clientSession, nil
}

// Encodes incoming elements into a Reader for the first Pipeline Element
func (r *RequestRunner) buildInputReader(input []element.Element) (io.Reader, error) {
	reader, writer := io.Pipe()
	serializingBackend, err := serializer.CreateSerializingBackend(serializer.BACKEND_MSGPACK, writer)
	if err != nil {
		return nil, err
	}
	header := &serializer.Header{
		r.pipeline.InputType,
	}
	_, isTabular := r.pipeline.InputType.Underlying.(*ds.TabularData)

	go func() {
		err = serializingBackend.EncodeHeader(header)
		if err != nil {
			logrus.Warn("Could not encode header", err.Error())
			return
		}
		if isTabular {
			err := serializingBackend.StartTabularValues()
			if err != nil {
				logrus.Warn("Could not send tabular start")
				return
			}
		}
		for _, ie := range input {
			err := serializingBackend.NextRow()
			if err != nil {
				logrus.Warn("Could not write next row start", err.Error())
				return
			}
			err = r.inputSerializer.Write(serializingBackend, ie)
			if err != nil {
				logrus.Warn("Could not write element", err.Error())
				return
			}
		}
		err = serializingBackend.Finish()
		if err != nil {
			logrus.Warn("Could not finish stream")
		}
		err = writer.Close()
		if err != nil {
			logrus.Warn("Could not finish stream")
		}
	}()
	return reader, nil
}

// Decodes outgoing elements from a reader for the last pipeline element.
func (r *RequestRunner) decodeOutput(reader io.Reader, expectedType ds.DataType) ([]element.Element, error) {
	deserializingBackend, err := serializer.CreateDeserializingBackend(serializer.BACKEND_MSGPACK, reader)
	if err != nil {
		return nil, err
	}

	header, err := deserializingBackend.DecodeHeader()
	if err != nil {
		logrus.Errorf("Could not decode header %s", err.Error())
		return nil, err
	}
	if !ds.DataTypeEquality(header.Format.Underlying, expectedType) {
		logrus.Errorf("Type mismatch, expected: %s got %s", ds.ToJsonString(expectedType), ds.ToJsonString(header.Format.Underlying))
		return nil, errors.New("Type mismatch!")
	}

	var resultRows []element.Element
	for {
		row, err := r.outputDeserializer.Read(deserializingBackend)
		if err == io.EOF {
			break
		}
		if err != nil {
			logrus.Warn("Could not decode response on last pipeline step")
			return nil, err
		}
		resultRows = append(resultRows, row)
	}
	return resultRows, nil
}
