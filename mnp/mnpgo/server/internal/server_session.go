package internal

import (
	"context"
	"github.com/sirupsen/logrus"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/protos/mantik/mnp"
	"sync"
)

type ServerSession struct {
	sessionId string
	handler   mnpgo.SessionHandler

	portConfiguration *mnpgo.PortConfiguration

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

	sessionHandler, err := handler.Init(
		req.SessionId,
		req.Configuration,
		contentTypes,
		progress,
	)
	if err != nil {
		logrus.Error("Could not initialize session", err)
		return nil, err
	}

	ctx, cancelFn := context.WithCancel(context.Background())

	serverSession := ServerSession{
		sessionId:         req.SessionId,
		handler:           sessionHandler,
		portConfiguration: contentTypes,
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
			ContentType:    c.ContentType,
			DestinationUrl: c.DestinationUrl,
		}
	}
	return &portConfiguration, nil
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

	task, err := NewServerTask(s.handler, taskId, s.ctx, s.portConfiguration)
	if err != nil {
		return nil, err
	}

	s.runTask(task)

	s.tasks[taskId] = task
	return task, nil
}

// Returns nil if the task doesn't exist
func (s *ServerSession) GetTask(taskId string) (*ServerTask, error) {
	s.mutex.Lock()
	defer s.mutex.Unlock()
	task, _ := s.tasks[taskId]
	return task, nil
}

func (s *ServerSession) runTask(task *ServerTask) {
	go func() {
		err := task.Run()
		if err != nil {
			logrus.Warnf("Task %s/%s failed %s", s.sessionId, task.taskId, err.Error())
		} else {
			logrus.Infof("Task %s/%s finished", s.sessionId, task.taskId)
		}
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
