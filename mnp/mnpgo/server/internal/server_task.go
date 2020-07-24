package internal

import (
	"context"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo"
	"golang.org/x/sync/errgroup"
)

type ServerTask struct {
	session     mnpgo.SessionHandler
	ctx         context.Context
	taskId      string
	multiplexer *StreamMultiplexer
	forwarder   []*Forwarder
}

func NewServerTask(
	session mnpgo.SessionHandler,
	taskId string,
	ctx context.Context,
	configuration *mnpgo.PortConfiguration,
) (*ServerTask, error) {

	var forwarders2 []*Forwarder

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
			forwarders2 = append(forwarders2, forwarder2)
		}
	}

	return &ServerTask{
		session:     session,
		ctx:         ctx,
		taskId:      taskId,
		multiplexer: multiplexer,
		forwarder:   forwarders2,
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
