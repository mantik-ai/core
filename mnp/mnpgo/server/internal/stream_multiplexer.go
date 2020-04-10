package internal

import (
	"bufio"
	"io"
)

// A Buffered Stream Multiplexer/Demultiplexer
// Used for handling different input/output channels into a group of readers and writers
// Readers and writes are generated using pipes

type StreamMultiplexer struct {
	bufSize int

	inputStates  []error
	outputStates []error

	inputs       []io.WriteCloser
	InputReaders []io.Reader
	outputs      []io.Reader
	OutputWrites []io.WriteCloser
}

func NewStreamMultiplexer(inputChannels int, outputChannels int, forwarders []io.WriteCloser) *StreamMultiplexer {
	bufSize := 65536 // Configurable?

	r := StreamMultiplexer{
		bufSize:      bufSize,
		inputStates:  make([]error, inputChannels, inputChannels),
		outputStates: make([]error, outputChannels, outputChannels),
		inputs:       make([]io.WriteCloser, inputChannels, inputChannels),
		InputReaders: make([]io.Reader, inputChannels, inputChannels),
		outputs:      make([]io.Reader, outputChannels, outputChannels),
		OutputWrites: make([]io.WriteCloser, outputChannels, outputChannels),
	}

	for i := 0; i < inputChannels; i++ {
		inputReader, inputWriter := io.Pipe()
		bufferedInputReader := bufio.NewReaderSize(inputReader, bufSize)
		r.inputs[i] = inputWriter
		r.InputReaders[i] = bufferedInputReader
	}

	for i := 0; i < outputChannels; i++ {
		if forwarders[i] != nil {
			r.OutputWrites[i] = forwarders[i]
		} else {
			outputReader, outputWriter := io.Pipe()
			bufferedOutputReader := bufio.NewReaderSize(outputReader, bufSize)
			r.outputs[i] = bufferedOutputReader
			r.OutputWrites[i] = outputWriter
		}
	}
	return &r
}

func (s *StreamMultiplexer) InputReader(id int) io.Reader {
	return s.InputReaders[id]
}

func (s *StreamMultiplexer) OutputWriter(id int) io.Writer {
	return s.OutputWrites[id]
}

// Write something on the input side
func (s *StreamMultiplexer) Write(id int, bytes []byte) error {
	_, err := s.inputs[id].Write(bytes)
	if err != nil {
		s.inputStates[id] = err
	}
	return err
}

func (s *StreamMultiplexer) WriteEof(id int) error {
	err := s.inputs[id].Close()
	state := err
	if state == nil {
		state = io.EOF
	}
	s.inputStates[id] = state
	return err
}

func (s *StreamMultiplexer) WriteFailure(id int, err error) {
	s.inputs[id].Close()
	s.inputStates[id] = err
}

// Read something on the output side
func (s *StreamMultiplexer) Read(id int) ([]byte, error) {
	buffer := make([]byte, s.bufSize, s.bufSize)
	n, err := s.outputs[id].Read(buffer)
	if n <= 0 && err != nil {
		if s.outputStates[id] == nil {
			s.outputStates[id] = err
		} else {
			err = s.outputStates[id]
		}
		return []byte{}, err
	}
	return buffer[:n], nil
}

func (s *StreamMultiplexer) Finalize(err error) {
	for i, inputState := range s.inputStates {
		if inputState == nil {
			s.inputStates[i] = err
			s.inputs[i].Close()
		}
	}
	for i, outputState := range s.outputStates {
		if outputState == nil {
			s.outputStates[i] = err
			s.OutputWrites[i].Close()
		}
	}
}
