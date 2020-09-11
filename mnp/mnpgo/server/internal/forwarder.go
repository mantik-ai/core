package internal

import (
	"context"
	"github.com/pkg/errors"
	"github.com/sirupsen/logrus"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/protos/mantik/mnp"
	"google.golang.org/grpc"
	"io"
)

type Forwarder struct {
	ctx         context.Context
	url         *mnpgo.MnpUrl
	multiplexer *StreamMultiplexer
	clientCon   *grpc.ClientConn
	sourcePort  int
	taskId      string
}

func MakeForwarder(ctx context.Context, multiplexer *StreamMultiplexer, sourcePort int, taskId string, destinationUrl string) (*Forwarder, error) {
	parsedUrl, err := mnpgo.ParseMnpUrl(destinationUrl)
	if err != nil {
		return nil, err
	}
	return &Forwarder{
		ctx:         ctx,
		url:         parsedUrl,
		multiplexer: multiplexer,
		sourcePort:  sourcePort,
		taskId:      taskId,
	}, nil
}

func (f *Forwarder) Run() error {
	clientCon, err := grpc.DialContext(f.ctx, f.url.Address, grpc.WithInsecure(), grpc.WithBlock())
	if err != nil {
		return f.fail(errors.Wrapf(err, "Could not dial next node for forwarding, url=%s", f.url.String()))
	}
	f.clientCon = clientCon
	service := mnp.NewMnpServiceClient(clientCon)
	pushClient, err := service.Push(f.ctx)
	if err != nil {
		return f.fail(errors.Wrap(err, "Could not init push client"))
	}
	err = pushClient.Send(&mnp.PushRequest{
		SessionId: f.url.SessionId,
		TaskId:    f.taskId,
		Port:      (int32)(f.url.Port),
	})
	if err != nil {
		return f.fail(errors.Wrap(err, "Could not send first push"))
	}
	for {
		bytes, err := f.multiplexer.Read(f.sourcePort)
		if len(bytes) == 0 && err == io.EOF {
			break
		}
		if err != nil && err != io.EOF {
			return f.fail(errors.Wrap(err, "Reading error"))
		}
		err = pushClient.Send(&mnp.PushRequest{
			Data: bytes,
		})
		if err != nil {
			return f.fail(errors.Wrap(err, "Writing error"))
		}
	}
	err = pushClient.Send(&mnp.PushRequest{
		Done: true,
	})
	if err != nil {
		return f.fail(errors.Wrap(err, "Writing EOF error"))
	}
	_, err = pushClient.CloseAndRecv()
	if err != nil {
		logrus.Warn("Error on closing forward push")
	}
	f.clientCon.Close()
	return nil
}

func (f *Forwarder) fail(err error) error {
	logrus.Warn("Forwarding failed", err)
	if f.clientCon != nil {
		f.clientCon.Close()
		f.clientCon = nil
	}
	f.multiplexer.ReadFailure(f.sourcePort, err)
	return err
}
