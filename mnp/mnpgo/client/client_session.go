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
package client

import (
	"context"
	"github.com/sirupsen/logrus"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/protos/mantik/mnp"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/util"
	"golang.org/x/sync/errgroup"
	"io"
)

type ClientSession struct {
	sessionId string
	ctx       context.Context
	service   mnp.MnpServiceClient
	ports     *mnpgo.PortConfiguration
}

func NewClientSession(sessionId string, ctx context.Context, ports *mnpgo.PortConfiguration, service mnp.MnpServiceClient) *ClientSession {
	return &ClientSession{
		sessionId: sessionId,
		ctx:       ctx,
		ports:     ports,
		service:   service,
	}
}

func (c *ClientSession) Quit() error {
	_, err := c.service.QuitSession(c.ctx, &mnp.QuitSessionRequest{
		SessionId: c.sessionId,
	})
	return err
}

func (c *ClientSession) RunTask(
	ctx context.Context,
	taskId string,
	input []io.Reader,
	output []io.WriteCloser,
) error {
	errgroup, childContext := errgroup.WithContext(ctx)

	for i, input := range input {
		func(id int, input io.Reader) {
			errgroup.Go(func() error {
				wrapped, err := util.WrapPushToWriter(c.service, childContext, c.sessionId, taskId, id)
				if err != nil {
					return err
				}
				n, err := io.Copy(wrapped, input)
				if err != nil {
					logrus.Warnf("Writing input %d failed in task %s", id, taskId)
					return err
				}
				logrus.Debugf("Written %d bytes to input channel %d", n, id)
				err = wrapped.Close()
				if err != nil {
					return err
				}
				return nil
			})
		}(i, input)
	}

	for i, output := range output {
		func(id int, output io.WriteCloser) {
			errgroup.Go(func() error {
				wrapped, err := util.WrapPullToReader(c.service, childContext, c.sessionId, taskId, id)
				if err != nil {
					logrus.Warnf("Reading output %d failed in task %s", id, taskId)
					return err
				}
				defer output.Close()
				n, err := io.Copy(output, wrapped)
				if err != nil {
					return err
				}
				logrus.Debugf("Written %d bytes to output channel %d", n, id)
				return nil
			})
		}(i, output)
	}
	err := errgroup.Wait()
	if err != nil {
		logrus.Warnf("Task %s failed: %s", taskId, err.Error())
	}
	return err
}

func (c *ClientSession) Ports() *mnpgo.PortConfiguration {
	return c.ports
}
