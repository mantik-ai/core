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
package internal

import (
	"context"
	"github.com/mantik-ai/core/mnp/mnpgo"
	"github.com/mantik-ai/core/mnp/mnpgo/protos/mantik/mnp"
	"github.com/pkg/errors"
	"github.com/sirupsen/logrus"
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
