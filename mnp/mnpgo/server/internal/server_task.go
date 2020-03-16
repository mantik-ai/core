package internal

import (
	"context"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo"
	"io"
)

type ServerTask struct {
	session     mnpgo.SessionHandler
	ctx         context.Context
	taskId      string
	multiplexer *StreamMultiplexer
}

func NewServerTask(
	session mnpgo.SessionHandler,
	taskId string,
	ctx context.Context,
	configuration *mnpgo.PortConfiguration,
	forwarders []*Forwarder,
) (*ServerTask, error) {

	initializedForwarders := make([]io.WriteCloser, len(configuration.Outputs), len(configuration.Outputs))
	for i := 0; i < len(configuration.Outputs); i++ {
		if forwarders[i] != nil {
			initalized, err := forwarders[i].RunTask(ctx, taskId)
			if err != nil {
				for _, c := range initializedForwarders {
					// Close them, there was an error
					if c != nil {
						c.Close()
					}
				}
				return nil, err
			}
			initializedForwarders[i] = initalized
		}
	}

	multiplexer := NewStreamMultiplexer(
		len(configuration.Inputs),
		len(configuration.Outputs),
		initializedForwarders,
	)

	return &ServerTask{
		session:     session,
		ctx:         ctx,
		taskId:      taskId,
		multiplexer: multiplexer,
	}, nil
}

func (t *ServerTask) Run() error {
	return t.session.RunTask(t.ctx, t.taskId, t.multiplexer.InputReaders, t.multiplexer.OutputWrites)
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
