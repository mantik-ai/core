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
package util

import (
	"bytes"
	"context"
	"github.com/mantik-ai/core/mnp/mnpgo/protos/mantik/mnp"
	"github.com/sirupsen/logrus"
	"io"
)

// Wraps a Push series to a io.WriteCloser
func WrapPushToWriter(dataClient mnp.MnpServiceClient, ctx context.Context, sessionId string, taskId string, port int) (io.WriteCloser, error) {
	pushClient, err := dataClient.Push(ctx)
	if err != nil {
		return nil, err
	}
	return &wrappedInput{
		ctx:        ctx,
		pushClient: pushClient,
		sessionId:  sessionId,
		taskId:     taskId,
		port:       port,
	}, nil
}

// Wrap a pull series to a io.Reader
func WrapPullToReader(dataClient mnp.MnpServiceClient, ctx context.Context, sessionId string, taskId string, port int) (io.Reader, error) {
	pullClient, err := dataClient.Pull(ctx, &mnp.PullRequest{
		SessionId: sessionId,
		TaskId:    taskId,
		Port:      (int32)(port),
	})
	if err != nil {
		return nil, err
	}
	return &wrappedOutput{
		ctx:    ctx,
		client: pullClient,
	}, nil
}

type wrappedInput struct {
	ctx        context.Context
	pushClient mnp.MnpService_PushClient
	sessionId  string
	taskId     string
	port       int
}

func (w *wrappedInput) Write(p []byte) (int, error) {
	err := w.pushClient.Send(&mnp.PushRequest{
		SessionId: w.sessionId,
		TaskId:    w.taskId,
		Port:      (int32)(w.port),
		DataSize:  0, // unknown
		Done:      false,
		Data:      p,
	})
	if err != nil {
		return 0, err
	}
	return len(p), nil
}

func (w *wrappedInput) Close() error {
	err := w.pushClient.Send(&mnp.PushRequest{
		SessionId: w.sessionId,
		TaskId:    w.taskId,
		Port:      (int32)(w.port),
		DataSize:  0,
		Done:      true,
	})
	if err != nil {
		return err
	}
	_, err = w.pushClient.CloseAndRecv()
	return err
}

type wrappedOutput struct {
	ctx    context.Context
	client mnp.MnpService_PullClient
	done   bool
	buffer bytes.Buffer
}

func (w *wrappedOutput) Read(p []byte) (n int, err error) {
	if w.buffer.Len() > 0 {
		return w.buffer.Read(p)
	} else {
		if w.done {
			return 0, io.EOF
		}
	}

	response, err := w.client.Recv()
	if err != nil {
		return 0, err
	}

	if len(response.Data) > 0 {
		_, err = w.buffer.Write(response.Data)
		if err != nil {
			logrus.Errorf("Could not forward %d bytes received data to buffer", len(response.Data))
			return 0, err
		}
	}

	if response.Done {
		w.done = true
	}

	return w.buffer.Read(p)
}
