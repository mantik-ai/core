package util

import (
	"bytes"
	"context"
	"github.com/sirupsen/logrus"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/protos/mantik/mnp"
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