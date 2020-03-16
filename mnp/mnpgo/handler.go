package mnpgo

import (
	"context"
	"github.com/golang/protobuf/ptypes/any"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/protos/mantik/mnp"
	"io"
)

/* An mnp service. */
type Handler interface {
	// Returns identity
	About() (AboutResponse, error)

	// Request a quit
	Quit() error

	// Request starting a new session
	Init(
		sessionId string,
		configuration *any.Any,
		contentTypes *PortConfiguration,
		stateCallback func(state mnp.SessionState),
	) (SessionHandler, error)
}

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
}
