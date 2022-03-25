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
package internal

import (
	"bufio"
	"github.com/mantik-ai/core/mnp/mnpgo/protos/mantik/mnp"
	"io"
	"sync"
)

// A Buffered Stream Multiplexer/Demultiplexer
// Used for handling different input/output channels into a group of readers and writers
// Readers and writes are generated using pipes

type portInfo struct {
	err   error
	calls int
	bytes int64
}

type StreamMultiplexer struct {
	bufSize int

	inputStates  []portInfo
	outputStates []portInfo
	mutex        sync.Mutex

	inputs       []io.WriteCloser
	InputReaders []io.Reader
	outputs      []io.Reader
	OutputWrites []io.WriteCloser
}

func NewStreamMultiplexer(inputChannels int, outputChannels int) *StreamMultiplexer {
	bufSize := 65536 // Configurable?

	r := StreamMultiplexer{
		bufSize:      bufSize,
		inputStates:  make([]portInfo, inputChannels, inputChannels),
		outputStates: make([]portInfo, outputChannels, outputChannels),
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
		outputReader, outputWriter := io.Pipe()
		bufferedOutputReader := bufio.NewReaderSize(outputReader, bufSize)
		r.outputs[i] = bufferedOutputReader
		r.OutputWrites[i] = outputWriter
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
	s.mutex.Lock()
	defer s.mutex.Unlock()
	s.inputStates[id].calls += 1
	s.inputStates[id].bytes += (int64)(len(bytes))
	if err != nil {
		s.inputStates[id].err = err
	}
	return err
}

func (s *StreamMultiplexer) WriteEof(id int) error {
	err := s.inputs[id].Close()
	s.mutex.Lock()
	defer s.mutex.Unlock()
	state := err
	if state == nil {
		state = io.EOF
	}
	s.inputStates[id].err = state
	return err
}

func (s *StreamMultiplexer) WriteFailure(id int, err error) {
	s.inputs[id].Close()
	s.mutex.Lock()
	defer s.mutex.Unlock()
	s.inputStates[id].err = err
}

func (s *StreamMultiplexer) ReadFailure(id int, err error) {
	s.OutputWrites[id].Close()
	s.mutex.Lock()
	defer s.mutex.Unlock()
	s.outputStates[id].err = err
}

// Read something on the output side
func (s *StreamMultiplexer) Read(id int) ([]byte, error) {
	buffer := make([]byte, s.bufSize, s.bufSize)

	n, err := io.ReadFull(s.outputs[id], buffer)

	s.mutex.Lock()
	defer s.mutex.Unlock()

	s.outputStates[id].calls += 1

	if n <= 0 && err != nil {
		if err == io.ErrUnexpectedEOF {
			err = io.EOF
		}
		if s.outputStates[id].err == nil {
			s.outputStates[id].err = err
		} else {
			err = s.outputStates[id].err
		}
		return []byte{}, err
	}
	s.outputStates[id].bytes += (int64)(n)
	return buffer[:n], nil
}

func (s *StreamMultiplexer) Finalize(err error) {
	s.mutex.Lock()
	defer s.mutex.Unlock()
	for i, inputState := range s.inputStates {
		if inputState.err == nil {
			s.inputStates[i].err = err
			s.inputs[i].Close()
		}
	}
	for i, outputState := range s.outputStates {
		if outputState.err == nil {
			s.outputStates[i].err = err
			s.OutputWrites[i].Close()
		}
	}
}

// Returns port status for input and ouptut
func (s *StreamMultiplexer) QueryTaskPortStatus() ([]*mnp.TaskPortStatus, []*mnp.TaskPortStatus) {
	s.mutex.Lock()
	defer s.mutex.Unlock()
	return convertStates(s.inputStates), convertStates(s.outputStates)
}

func convertStates(info []portInfo) []*mnp.TaskPortStatus {
	result := make([]*mnp.TaskPortStatus, len(info), len(info))
	for i, v := range info {
		state := convertState(v)
		result[i] = &state
	}
	return result
}

func convertState(info portInfo) mnp.TaskPortStatus {
	var result mnp.TaskPortStatus
	if info.err != nil && info.err != io.EOF {
		result.Error = info.err.Error()
	}
	result.MsgCount = (int32)(info.calls)
	result.Data = info.bytes
	result.Done = info.err == io.EOF
	return result
}
