package pipeline

import (
	"github.com/pkg/errors"
	"github.com/sirupsen/logrus"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/formats/natural"
	"gl.ambrosys.de/mantik/go_shared/ds/util/serializer"
	"gl.ambrosys.de/mantik/go_shared/serving"
	"gl.ambrosys.de/mantik/go_shared/serving/server"
	"io"
	"net/http"
)

type RequestRunner struct {
	pipeline *Pipeline
	// Encodes Input into Mantik Format
	inputSerializer natural.ElementSerializer
	// Decodes Output from Mantik Format
	outputDeserializer natural.ElementDeserializer
}

func CreateRequestRunner(pipeline *Pipeline) (*RequestRunner, error) {
	inputSerializer, err := natural.LookupRootElementSerializer(pipeline.InputType.Underlying)
	if err != nil {
		return nil, err
	}
	outputDeserializer, err := natural.LookupRootElementDeserializer(pipeline.OutputType().Underlying)
	if err != nil {
		return nil, err
	}
	return &RequestRunner{
		pipeline,
		inputSerializer,
		outputDeserializer,
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
		Output: r.pipeline.OutputType(),
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
		return nil, err
	}

	var currentReader = inputReader
	var currentType = r.pipeline.InputType.Underlying
	for stepId, step := range r.pipeline.Steps {
		req, err := http.NewRequest(http.MethodPost, step.Url, currentReader)
		req.Header.Add(server.HeaderContentType, server.MimeMantikBundle)
		res, err := http.DefaultClient.Do(req)

		if err != nil {
			logrus.Warn("Request failed for step", stepId, err.Error())
			return nil, err
		}

		if res.StatusCode != 200 {
			logrus.Warn("Received non 200 status code on step", stepId)
			return nil, errors.New("Pipeline step returned non 200")
		}
		currentReader = res.Body
		currentType = step.OutputType.Underlying
	}

	resultRows, err := r.decodeOutput(currentReader, currentType)
	if err != nil {
		return nil, err
	}
	return resultRows, nil
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
