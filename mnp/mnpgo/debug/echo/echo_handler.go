package echo

import (
	"context"
	"github.com/golang/protobuf/ptypes/any"
	"github.com/pkg/errors"
	"github.com/sirupsen/logrus"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/protos/mantik/mnp"
	"golang.org/x/sync/errgroup"
	"io"
)

/* A Simple handler which just responds incoming data and prints out
   incoming data. Used for debugging. */
type EchoHandler struct {
}

func (e *EchoHandler) About() (*mnpgo.AboutResponse, error) {
	logrus.Info("About Requested")
	return &mnpgo.AboutResponse{Name: "EchoHandler"}, nil
}

func (e *EchoHandler) Quit() error {
	logrus.Info("Quit Requested")
	return nil
}

func (e *EchoHandler) Init(sessionId string, configuration *any.Any, contentTypes *mnpgo.PortConfiguration, stateCallback mnpgo.StateCallback) (mnpgo.SessionHandler, error) {
	logrus.Infof("Session requested %s", sessionId)
	logrus.Info("Input Config")
	for i, input := range contentTypes.Inputs {
		logrus.Infof("Port %d %s", i, input.ContentType)
	}
	logrus.Info("Output Config")
	for i, output := range contentTypes.Outputs {
		logrus.Infof("Port %d %s (forward=%s)", i, output.ContentType, output.DestinationUrl)
	}
	if len(contentTypes.Inputs) != len(contentTypes.Outputs) {
		return nil, errors.New("Input/Output count mismatch")
	}
	if stateCallback != nil {
		stateCallback(mnp.SessionState_SS_INITIALIZING)
		stateCallback(mnp.SessionState_SS_STARTING_UP)
	}
	return &EchoSession{
		sessionId:    sessionId,
		contentTypes: contentTypes,
	}, nil
}

type EchoSession struct {
	sessionId    string
	contentTypes *mnpgo.PortConfiguration
}

func (e *EchoSession) Quit() error {
	logrus.Info("Quit Session Request")
	return nil
}

func (e *EchoSession) RunTask(ctx context.Context, taskId string, input []io.Reader, output []io.WriteCloser) error {
	logrus.Infof("Running Task %s", taskId)
	errgroup, _ := errgroup.WithContext(ctx)
	for i := 0; i < len(e.contentTypes.Inputs); i++ {
		func(i int) {
			errgroup.Go(func() error {
				_, err := io.Copy(output[i], input[i])
				if err == nil {
					err = output[i].Close()
				}
				return err
			})
		}(i)
	}
	err := errgroup.Wait()
	logrus.Infof("Task %s finished", taskId)
	return err
}

func (e *EchoSession) Ports() *mnpgo.PortConfiguration {
	return e.contentTypes
}