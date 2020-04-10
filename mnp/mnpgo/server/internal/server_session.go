package internal

import (
	"context"
	"github.com/pkg/errors"
	"github.com/sirupsen/logrus"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/protos/mantik/mnp"
	"sync"
)

type ServerSession struct {
	sessionId string
	handler   mnpgo.SessionHandler

	portConfiguration *mnpgo.PortConfiguration
	forwarders        []*Forwarder // one for each output port, nil if there is no forwarding

	cancelFn context.CancelFunc
	ctx      context.Context

	tasks map[string]*ServerTask
	// protecting tasks
	mutex sync.Mutex
}

func CreateSession(req *mnp.InitRequest, handler mnpgo.Handler, progress func(state mnp.SessionState)) (*ServerSession, error) {
	contentTypes, err := extractPortConfiguration(req)
	if err != nil {
		return nil, err
	}

	forwarders, err := createForwarders(context.Background(), req)
	if err != nil {
		return nil, err
	}
	sessionHandler, err := handler.Init(
		req.SessionId,
		req.Configuration,
		contentTypes,
		progress,
	)
	if err != nil {
		shutdownForwarders(forwarders)
		return nil, err
	}

	ctx, cancelFn := context.WithCancel(context.Background())

	serverSession := ServerSession{
		sessionId:         req.SessionId,
		handler:           sessionHandler,
		portConfiguration: contentTypes,
		forwarders:        forwarders,
		cancelFn:          cancelFn,
		ctx:               ctx,
		tasks:             make(map[string]*ServerTask),
		mutex:             sync.Mutex{},
	}

	return &serverSession, nil
}

func extractPortConfiguration(req *mnp.InitRequest) (*mnpgo.PortConfiguration, error) {
	portConfiguration := mnpgo.PortConfiguration{
		Inputs:  make([]mnpgo.InputPortConfiguration, len(req.Inputs), len(req.Inputs)),
		Outputs: make([]mnpgo.OutputPortConfiguration, len(req.Outputs), len(req.Outputs)),
	}

	for i, c := range req.Inputs {
		portConfiguration.Inputs[i] = mnpgo.InputPortConfiguration{
			ContentType: c.ContentType,
		}
	}

	for i, c := range req.Outputs {
		portConfiguration.Outputs[i] = mnpgo.OutputPortConfiguration{
			ContentType: c.ContentType,
		}
	}
	return &portConfiguration, nil
}

func createForwarders(ctx context.Context, req *mnp.InitRequest) ([]*Forwarder, error) {
	forwarders := make([]*Forwarder, len(req.Outputs), len(req.Outputs))
	var forwardConnectError error
	for i, c := range req.Outputs {
		// TODO: Parallelize?
		if len(c.DestinationUrl) > 0 {
			forwarder, err := ConnectForwarder(ctx, c.DestinationUrl)
			if err != nil {
				forwardConnectError = err
				break
			} else {
				forwarders[i] = forwarder
			}
		}
	}
	if forwardConnectError != nil {
		shutdownForwarders(forwarders)
		return nil, errors.Wrap(forwardConnectError, "Could not connect forwarders")
	}
	return forwarders, nil
}

func shutdownForwarders(forwarders []*Forwarder) {
	for _, f := range forwarders {
		if f != nil {
			f.Close()
		}
	}
}

func (s *ServerSession) Shutdown() error {
	err := s.handler.Quit()
	if err != nil {
		logrus.Warn("Could not quit session", err)
	}
	return err
}

func (s *ServerSession) GetOrCreateTask(taskId string) (*ServerTask, error) {
	s.mutex.Lock()
	defer s.mutex.Unlock()
	task, found := s.tasks[taskId]
	if found {
		return task, nil
	}
	logrus.Infof("Creating task %s", taskId)

	task, err := NewServerTask(s.handler, taskId, s.ctx, s.portConfiguration, s.forwarders)
	if err != nil {
		return nil, err
	}

	s.runTask(task)

	s.tasks[taskId] = task
	return task, nil
}

func (s *ServerSession) runTask(task *ServerTask) {
	go func() {
		err := task.Run()
		if err != nil {
			logrus.Warnf("Task %s failed %s", task.taskId, err.Error())
		} else {
			logrus.Infof("Task %s finished", task.taskId)
		}
		task.Finalize(err)
		s.mutex.Lock()
		defer s.mutex.Unlock()
		delete(s.tasks, task.taskId)
	}()
}

func (s *ServerSession) GetTasks() []string {
	s.mutex.Lock()
	defer s.mutex.Unlock()
	result := make([]string, 0, 0)
	for k, _ := range s.tasks {
		result = append(result, k)
	}
	return result
}
