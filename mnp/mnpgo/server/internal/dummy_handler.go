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
	"github.com/golang/protobuf/ptypes/any"
	"github.com/mantik-ai/core/mnp/mnpgo"
	"io"
	"io/ioutil"
)

/*
A Simple dummy handler with one input and one output.
The first input is forwarded, the second is multiplied by two per byte.
*/
type DummyHandler struct {
	QuitCalled bool
	Sessions   []*DummySession
}

func (d *DummyHandler) About() (*mnpgo.AboutResponse, error) {
	return &mnpgo.AboutResponse{
		Name:  "TestHandler1",
		Extra: nil,
	}, nil
}

func (d *DummyHandler) Quit() error {
	d.QuitCalled = true
	return nil
}

func (d *DummyHandler) Init(
	sessionId string,
	configuration *any.Any,
	contentTypes *mnpgo.PortConfiguration,
	stateCallback mnpgo.StateCallback,
) (mnpgo.SessionHandler, error) {
	r := &DummySession{
		SessionId:    sessionId,
		ContentTypes: contentTypes,
	}
	d.Sessions = append(d.Sessions, r)
	return r, nil
}

type DummySession struct {
	SessionId    string
	Quitted      bool
	ContentTypes *mnpgo.PortConfiguration
	// If set, tasks wil fail
	Fail error
}

func (d *DummySession) Quit() error {
	d.Quitted = true
	return nil
}

func (d *DummySession) RunTask(
	ctx context.Context,
	taskId string,
	input []io.Reader,
	output []io.WriteCloser,
) error {
	// simple tasks, output1 is input, output2 is 2*input
	if len(input) != 1 {
		panic("Wrong input count")
	}
	if len(output) != 2 {
		panic("Wrong output count")
	}
	data, err := ioutil.ReadAll(input[0])
	if err != nil {
		return err
	}
	_, err = output[0].Write(data)
	if err != nil {
		return err
	}
	for i, b := range data {
		data[i] = 2 * b
	}
	_, err = output[1].Write(data)
	if err != nil {
		return err
	}
	if d.Fail != nil {
		return d.Fail
	}
	output[0].Close()
	output[1].Close()
	return nil
}

func (d *DummySession) Ports() *mnpgo.PortConfiguration {
	return d.ContentTypes
}
