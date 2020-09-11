package mnpgo

import (
	"bytes"
	"context"
	"github.com/golang/protobuf/ptypes/any"
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/protos/mantik/mnp"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/util"
	"io"
)

/* An mnp service. */
type Handler interface {
	// Returns identity
	About() (*AboutResponse, error)

	// Request a quit
	Quit() error

	// Request starting a new session
	Init(
		sessionId string,
		configuration *any.Any,
		contentTypes *PortConfiguration,
		stateCallback StateCallback,
	) (SessionHandler, error)
}

type StateCallback func(state mnp.SessionState)

type AboutResponse struct {
	Name  string
	Extra *any.Any
}

type PortConfiguration struct {
	Inputs  []InputPortConfiguration
	Outputs []OutputPortConfiguration
}

type InputPortConfiguration struct {
	// Selected content type or empty for default
	ContentType string
}

type OutputPortConfiguration struct {
	// Selected content type or empty for default
	ContentType string
	// Where data goes to (for debugging only, not managed by Handler)
	DestinationUrl string
}

/* A single session. */
type SessionHandler interface {
	Quit() error

	// Run a Task (sync)
	// Will be called in a go routine
	RunTask(
		ctx context.Context,
		taskId string,
		input []io.Reader,
		output []io.WriteCloser,
	) error

	// Returns the port configuration (the one used for initialization)
	// May refine content types if not given
	Ports() *PortConfiguration
}

func RunTaskWithBytes(sh SessionHandler, ctx context.Context, taskId string, input [][]byte) ([][]byte, error) {
	pc := sh.Ports()
	ic := len(pc.Inputs)
	if ic != len(input) {
		return nil, errors.Errorf("Wrong count of input ports, got %d expected %d", len(input), ic)
	}

	ins := make([]io.Reader, ic, ic)
	for i := 0; i < ic; i++ {
		ins[i] = bytes.NewBuffer(input[i])
	}

	oc := len(pc.Outputs)
	outs := make([]io.WriteCloser, oc, oc)
	for i := 0; i < oc; i++ {
		outs[i] = util.NewClosableBuffer()
	}

	err := sh.RunTask(ctx, taskId, ins, outs)

	outsUnpacked := make([][]byte, oc, oc)
	for i := 0; i < oc; i++ {
		outsUnpacked[i] = outs[i].(*util.CloseableBuffer).Bytes()
	}
	return outsUnpacked, err
}
