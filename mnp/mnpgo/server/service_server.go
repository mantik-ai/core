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
package server

import (
	"context"
	"github.com/golang/protobuf/ptypes/empty"
	"github.com/mantik-ai/core/mnp/mnpgo"
	"github.com/mantik-ai/core/mnp/mnpgo/protos/mantik/mnp"
	"github.com/mantik-ai/core/mnp/mnpgo/server/internal"
	"github.com/pkg/errors"
	"github.com/sirupsen/logrus"
	"io"
	"sync"
)

// Implements gRpc protocol for mnp
type MnpServiceServer struct {
	handler  mnpgo.Handler
	sessions sync.Map
}

func NewMnpServiceServer(handler mnpgo.Handler) *MnpServiceServer {
	return &MnpServiceServer{
		handler:  handler,
		sessions: sync.Map{},
	}
}

func (s *MnpServiceServer) About(context.Context, *empty.Empty) (*mnp.AboutResponse, error) {
	a, err := s.handler.About()
	if err != nil {
		logrus.Warn("Could not run about call", err)
		return nil, err
	}
	return &mnp.AboutResponse{
		Name:  a.Name,
		Extra: a.Extra,
	}, nil
}

func (s *MnpServiceServer) Init(req *mnp.InitRequest, res mnp.MnpService_InitServer) error {
	_, existing := s.sessions.Load(req.SessionId)
	if existing {
		return errors.New("Session already exists")
	}

	// Note: we must respond to the stream in the same goroutine, otherwise
	// gRpc closes the connection

	stateHandler := func(state mnp.SessionState) {
		if state == mnp.SessionState_SS_READY || state == mnp.SessionState_SS_FAILED {
			// ignoring final states, we send them by ourself
			return
		}
		err := res.Send(&mnp.InitResponse{
			State: state,
		})
		if err != nil {
			logrus.Warn("Could not send init response", err)
		}
	}
	session, err := internal.CreateSession(req, s.handler, stateHandler)

	if err != nil {
		err = res.Send(&mnp.InitResponse{
			State: mnp.SessionState_SS_FAILED,
			Error: err.Error(),
		})
		if err != nil {
			logrus.Warn("Could not send session init error cause", err)
		}
		return nil
	}

	_, existing = s.sessions.LoadOrStore(req.SessionId, session)
	if existing {
		logrus.Error("Session race condition, already exists", req.SessionId)
		session.Shutdown()
		err = res.Send(&mnp.InitResponse{
			State: mnp.SessionState_SS_FAILED,
			Error: "SessionId race condition",
		})
		if err != nil {
			logrus.Warn("Could not send session init error cause", err)
		}
		return nil
	}

	err = res.Send(&mnp.InitResponse{
		State: mnp.SessionState_SS_READY,
	})

	if err != nil {
		logrus.Warn("Could not send init ready", err)
	}

	return nil
}

func (s *MnpServiceServer) Quit(context.Context, *mnp.QuitRequest) (*mnp.QuitResponse, error) {
	err := s.handler.Quit()
	if err != nil {
		logrus.Warn("Could not run quit call", err)
		return nil, err
	}
	return &mnp.QuitResponse{}, nil
}

func (s *MnpServiceServer) QuitSession(ctx context.Context, req *mnp.QuitSessionRequest) (*mnp.QuitSessionResponse, error) {
	session, err := s.getSession(req.SessionId)
	if err != nil {
		return nil, err
	}
	err = session.Shutdown()
	if err != nil {
		return nil, err
	}
	s.sessions.Delete(req.SessionId)
	return &mnp.QuitSessionResponse{}, nil
}

func (s *MnpServiceServer) Push(req mnp.MnpService_PushServer) error {
	first, err := req.Recv()
	if err != nil {
		return err
	}

	session, err := s.getSession(first.SessionId)
	if err != nil {
		return err
	}

	taskHandler, err := session.GetOrCreateTask(
		first.TaskId,
	)
	if err != nil {
		logrus.Errorf("Could not get/create task %s", err.Error())
		return err
	}

	port := (int)(first.Port)

	current := first
	for {
		if len(current.Data) > 0 {
			err := taskHandler.Write(port, current.Data)
			if err != nil {
				logrus.Warnf("Could not feed more data on port %d, err %s", first.Port, err.Error())
				return err
			}
		}
		if current.Done || err == io.EOF {
			err = taskHandler.WriteEof(port)
			return s.finishPush(nil, req)
		}
		current, err = req.Recv()

		if err != nil {
			logrus.Warnf("Could not read additional data %s", err.Error())
			taskHandler.WriteFailure(port, err)
			return s.finishPush(err, req)
		}
	}
}

func (s *MnpServiceServer) finishPush(err error, req mnp.MnpService_PushServer) error {
	err2 := req.SendAndClose(&mnp.PushResponse{})
	if err2 != nil {
		return err
	}
	return err
}

func (s *MnpServiceServer) Pull(req *mnp.PullRequest, res mnp.MnpService_PullServer) error {
	session, err := s.getSession(req.SessionId)
	if err != nil {
		return err
	}

	task, err := session.GetOrCreateTask(req.TaskId)
	if err != nil {
		logrus.Errorf("Could not get/create task %s", err.Error())
		return err
	}

	for {
		data, err := task.Read((int)(req.Port))
		if len(data) > 0 {
			err2 := res.Send(&mnp.PullResponse{
				Done: false,
				Data: data,
			})
			if err2 != nil {
				logrus.Warn("Error writing data", err2.Error())
				return err2
			}
		}
		if err == io.EOF {
			return s.finishPull(res)
		}
		if err != nil {
			logrus.Warn("Could not pull data", err.Error())
			return err
		}
	}
}

func (s *MnpServiceServer) QueryTask(ctx context.Context, req *mnp.QueryTaskRequest) (*mnp.QueryTaskResponse, error) {
	session, err := s.getSession(req.SessionId)
	if err != nil {
		return nil, err
	}
	var task *internal.ServerTask
	if req.Ensure {
		task, err = session.GetOrCreateTask(req.TaskId)
	} else {
		task, err = session.GetTask(req.TaskId)
	}
	if err != nil {
		return nil, err
	}
	if task == nil {
		return &mnp.QueryTaskResponse{
			State: mnp.TaskState_TS_UNKNOWN,
		}, nil
	}

	return task.Query(), nil
}

func (s *MnpServiceServer) finishPull(res mnp.MnpService_PullServer) error {
	return res.Send(&mnp.PullResponse{
		Size: 0,
		Done: true,
		Data: nil,
	})
}

func (s *MnpServiceServer) getSession(sessionId string) (*internal.ServerSession, error) {
	session, found := s.sessions.Load(sessionId)
	if !found {
		return nil, errors.Errorf("Session %s not found", sessionId)
	}
	return session.(*internal.ServerSession), nil
}

func (s *MnpServiceServer) GetSessions() []string {
	result := make([]string, 0, 0)
	s.sessions.Range(func(key, value interface{}) bool {
		result = append(result, key.(string))
		return true
	})
	return result
}
