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
	"context"
	"github.com/mantik-ai/core/mnp/mnpgo"
	"github.com/mantik-ai/core/mnp/mnpgo/protos/mantik/mnp"
	"golang.org/x/sync/errgroup"
)

type ServerTask struct {
	session      mnpgo.SessionHandler
	ctx          context.Context
	taskId       string
	multiplexer  *StreamMultiplexer
	forwarder    []*Forwarder
	state        mnp.TaskState
	errorMessage string
}

func NewServerTask(
	session mnpgo.SessionHandler,
	taskId string,
	ctx context.Context,
	configuration *mnpgo.PortConfiguration,
) (*ServerTask, error) {

	var forwarders []*Forwarder

	multiplexer := NewStreamMultiplexer(
		len(configuration.Inputs),
		len(configuration.Outputs),
	)

	for i, o := range configuration.Outputs {
		if len(o.DestinationUrl) > 0 {
			forwarder2, err := MakeForwarder(ctx, multiplexer, i, taskId, o.DestinationUrl)
			if err != nil {
				return nil, err
			}
			forwarders = append(forwarders, forwarder2)
		}
	}

	return &ServerTask{
		session:     session,
		ctx:         ctx,
		taskId:      taskId,
		multiplexer: multiplexer,
		forwarder:   forwarders,
		state:       mnp.TaskState_TS_EXISTS,
	}, nil
}

func (t *ServerTask) Run() error {
	group := errgroup.Group{}

	for _, f := range t.forwarder {
		func(f *Forwarder) {
			group.Go(func() error {
				err := f.Run()
				return err
			})
		}(f)
	}
	group.Go(func() error {
		err := t.session.RunTask(t.ctx, t.taskId, t.multiplexer.InputReaders, t.multiplexer.OutputWrites)
		if err == nil {
			t.state = mnp.TaskState_TS_FINISHED
		} else {
			t.state = mnp.TaskState_TS_FAILED
			t.errorMessage = err.Error()
		}
		t.multiplexer.Finalize(err)
		return err
	})
	return group.Wait()
}

func (t *ServerTask) Write(port int, data []byte) error {
	return t.multiplexer.Write(port, data)
}

func (t *ServerTask) WriteEof(port int) error {
	return t.multiplexer.WriteEof(port)
}

func (t *ServerTask) WriteFailure(port int, err error) {
	t.multiplexer.WriteFailure(port, err)
}

func (t *ServerTask) Read(port int) ([]byte, error) {
	return t.multiplexer.Read(port)
}

func (t *ServerTask) Query() *mnp.QueryTaskResponse {
	inputStates, outputStates := t.multiplexer.QueryTaskPortStatus()

	return &mnp.QueryTaskResponse{
		State:   t.state,
		Error:   t.errorMessage,
		Inputs:  inputStates,
		Outputs: outputStates,
	}
}
