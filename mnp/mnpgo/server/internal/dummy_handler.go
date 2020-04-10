package internal

import (
	"context"
	"github.com/golang/protobuf/ptypes/any"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo"
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
