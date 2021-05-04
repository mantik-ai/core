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
package mnpbridge

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/formats/natural"
	"gl.ambrosys.de/mantik/go_shared/serving"
	"gl.ambrosys.de/mantik/go_shared/serving/server"
	"gl.ambrosys.de/mantik/go_shared/util/dirzip"
	"io"
)

type ExecutableRunner interface {
	RunTask(taskId string, inputs []io.Reader, outputs []io.WriteCloser) error
}

func NewExecutableRunner(executable serving.Executable, ports *mnpgo.PortConfiguration) (ExecutableRunner, error) {
	switch e := executable.(type) {
	case serving.ExecutableAlgorithm:
		if len(ports.Inputs) != 1 {
			return nil, errors.Errorf("Invalid input count, expected 1, got %d", len(ports.Inputs))
		}
		inputDecoder, err := natural.NewStreamDecoder(ports.Inputs[0].ContentType, e.Type().Input.Underlying)
		if err != nil {
			return nil, errors.Wrap(err, "Could not build input decoder")
		}
		resultEncoder, err := natural.NewStreamEncoder(ports.Outputs[0].ContentType, e.Type().Output.Underlying)
		if err != nil {
			return nil, errors.Wrap(err, "Could not build output encoder")
		}
		return &executableAlgorithmRunner{
			algorithm:     e,
			inputDecoder:  inputDecoder,
			resultEncoder: resultEncoder,
		}, nil
	case serving.ExecutableDataSet:
		if len(ports.Inputs) != 0 {
			return nil, errors.New("Expected no input")
		}
		if len(ports.Outputs) != 1 {
			return nil, errors.Errorf("Expected exactly one output, got %d", len(ports.Outputs))
		}
		resultEncoder, err := natural.NewStreamEncoder(ports.Outputs[0].ContentType, e.Type().Underlying)
		if err != nil {
			return nil, errors.Wrap(err, "Could not build output decoder")
		}
		return &executableDatasetRunner{
			dataset:       e,
			resultEncoder: resultEncoder,
		}, nil
	case serving.TrainableAlgorithm:
		if len(ports.Inputs) != 1 {
			return nil, errors.Errorf("Expected one input, got %d", len(ports.Inputs))
		}
		if len(ports.Outputs) != 2 {
			return nil, errors.Errorf("Expected two outputs, got %d", len(ports.Outputs))
		}
		inputDecoder, err := natural.NewStreamDecoder(ports.Inputs[0].ContentType, e.TrainingType().Underlying)
		if err != nil {
			return nil, errors.Wrap(err, "Could not create input decoder")
		}
		// output0: result (as ZIP)
		// output1: stats
		if ports.Outputs[0].ContentType != server.MimeZip {
			return nil, errors.Errorf("Expected %s got %s", server.MimeZip, ports.Outputs[0].ContentType)
		}
		statsEncoder, err := natural.NewStreamEncoder(ports.Outputs[1].ContentType, e.StatType().Underlying)
		if err != nil {
			return nil, errors.Wrap(err, "Could not create output encoder")
		}
		return &executableTrainableAlgorithmRunner{
			trainable:    e,
			inputDecoder: inputDecoder,
			statsEncoder: statsEncoder,
		}, nil
	case serving.ExecutableTransformer:
		if len(ports.Inputs) != len(e.Inputs()) {
			return nil, errors.Errorf("Expected %d inputs, got %d", len(e.Inputs()), len(ports.Inputs))
		}
		if len(ports.Outputs) != len(e.Outputs()) {
			return nil, errors.Errorf("Expected %d outputs, got %d", len(e.Outputs()), len(ports.Outputs))
		}
		streamDecoders := make([]natural.StreamDecoder, len(ports.Inputs), len(ports.Inputs))
		for i, p := range ports.Inputs {
			decoder, err := natural.NewStreamDecoder(p.ContentType, e.Inputs()[i].Underlying)
			if err != nil {
				return nil, errors.Wrapf(err, "Could not build decoder for input port %d", i)
			}
			streamDecoders[i] = decoder
		}
		streamEncoders := make([]natural.StreamEncoder, len(ports.Outputs), len(ports.Outputs))
		for i, p := range ports.Outputs {
			encoder, err := natural.NewStreamEncoder(p.ContentType, e.Outputs()[i].Underlying)
			if err != nil {
				return nil, errors.Wrapf(err, "Could not build encoder for output port %d", i)
			}
			streamEncoders[i] = encoder
		}
		return &executableTransformerRunner{
			executable:     e,
			streamDecoders: streamDecoders,
			streamEncoders: streamEncoders,
		}, nil
	default:
		panic("Unsupported executable type")
	}
}

type executableAlgorithmRunner struct {
	algorithm     serving.ExecutableAlgorithm
	inputDecoder  natural.StreamDecoder
	resultEncoder natural.StreamEncoder
}

func (e *executableAlgorithmRunner) RunTask(taskId string, inputs []io.Reader, outputs []io.WriteCloser) error {
	/* TODO Note: this is bad that we read everything, however this is due the old ExecutableAlgorithm Interface
	   and will be replaced in future.
	*/
	streamReader := e.inputDecoder.StartDecoding(inputs[0])
	streamWriter := e.resultEncoder.StartEncoding(outputs[0])

	all, err := element.ReadAllFromStreamReader(streamReader)
	if err != nil {
		return errors.Wrap(err, "Could not read full input")
	}

	result, err := e.algorithm.Execute(all)
	if err != nil {
		return err
	}

	for _, element := range result {
		err = streamWriter.Write(element)
		if err != nil {
			return errors.Wrap(err, "Could not write result")
		}
	}
	err = streamWriter.Close()
	if err != nil {
		return errors.Wrap(err, "Could not finish result")
	}
	return nil
}

type executableDatasetRunner struct {
	dataset       serving.ExecutableDataSet
	resultEncoder natural.StreamEncoder
}

func (e *executableDatasetRunner) RunTask(taskId string, inputs []io.Reader, outputs []io.WriteCloser) error {
	streamWriter := e.resultEncoder.StartEncoding(outputs[0])
	reader := e.dataset.Get()
	for {
		element, err := reader.Read()
		if err == io.EOF {
			break
		}
		if err != nil {
			return errors.Wrap(err, "Could not read next element")
		}
		err = streamWriter.Write(element)
		if err != nil {
			return errors.Wrap(err, "Could not write next element")
		}
	}
	err := streamWriter.Close()
	if err != nil {
		return errors.Wrap(err, "Could not close stream")
	}
	return nil
}

type executableTrainableAlgorithmRunner struct {
	trainable    serving.TrainableAlgorithm
	inputDecoder natural.StreamDecoder
	statsEncoder natural.StreamEncoder
}

func (e *executableTrainableAlgorithmRunner) RunTask(taskId string, inputs []io.Reader, outputs []io.WriteCloser) error {
	/* TODO Note: this is bad that we read everything, however this is due the old ExecutableAlgorithm Interface
	   and will be replaced in future.
	*/
	streamReader := e.inputDecoder.StartDecoding(inputs[0])
	statWriter := e.statsEncoder.StartEncoding(outputs[1])

	all, err := element.ReadAllFromStreamReader(streamReader)
	if err != nil {
		return errors.Wrap(err, "Could not read full input")
	}

	result, err := e.trainable.Train(all)

	for _, element := range result {
		err = statWriter.Write(element)
		if err != nil {
			return errors.Wrap(err, "Could not write result")
		}
	}

	err = statWriter.Close()
	if err != nil {
		return errors.Wrap(err, "Could not close stat output")
	}

	resultDir, err := e.trainable.LearnResultDirectory()
	if err != nil {
		return errors.Wrap(err, "Could not get result directory")
	}

	err = dirzip.ZipDirectoryToStream(resultDir, false, outputs[0])
	if err != nil {
		return errors.Wrap(err, "Could not zip directory")
	}

	err = outputs[0].Close()
	if err != nil {
		return errors.Wrap(err, "Could not close port 0")
	}
	return nil
}

type executableTransformerRunner struct {
	executable     serving.ExecutableTransformer
	streamDecoders []natural.StreamDecoder
	streamEncoders []natural.StreamEncoder
}

func (e executableTransformerRunner) RunTask(taskId string, inputs []io.Reader, outputs []io.WriteCloser) error {
	inputReaders := make([]element.StreamReader, len(e.streamDecoders), len(e.streamDecoders))
	for i, r := range e.streamDecoders {
		inputReaders[i] = r.StartDecoding(inputs[i])
	}
	outputWriters := make([]element.StreamWriter, len(e.streamEncoders), len(e.streamEncoders))
	for i, e := range e.streamEncoders {
		outputWriters[i] = e.StartEncoding(outputs[i])
	}

	err := e.executable.Run(inputReaders, outputWriters)

	for _, w := range outputWriters {
		w.Close()
	}

	return err
}
