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
	"github.com/mantik-ai/core/mnp/mnpgo/protos/mantik/mnp"
	"github.com/pkg/errors"
	"github.com/sirupsen/logrus"
	"google.golang.org/grpc"
	"io"
)

/* Holds the raw mnp connection to a node. */
type SingleNodeClient struct {
	con           *grpc.ClientConn
	serviceClient mnp.MnpServiceClient
	address       string
}

func ConnectSingleNode(ctx context.Context, address string) (*SingleNodeClient, error) {
	logrus.Debugf("Connecting %s", address)
	clientCon, err := grpc.DialContext(ctx, address, grpc.WithInsecure(), grpc.WithBlock())
	if err != nil {
		logrus.Warnf("Connection to %s failed, %s", address, err.Error())
		return nil, err
	} else {
		logrus.Debugf("Connected to %s", address)
	}
	return &SingleNodeClient{
		con:           clientCon,
		serviceClient: mnp.NewMnpServiceClient(clientCon),
		address:       address,
	}, nil
}

func (s *SingleNodeClient) ServiceClient() mnp.MnpServiceClient {
	return s.serviceClient
}

func (s *SingleNodeClient) Address() string {
	return s.address
}

func (s *SingleNodeClient) Close() error {
	return s.con.Close()
}

func (c *SingleNodeClient) QuitSession(ctx context.Context, sessionId string) error {
	_, err := c.serviceClient.QuitSession(ctx, &mnp.QuitSessionRequest{
		SessionId: sessionId,
	})
	return err
}

func (c *SingleNodeClient) Init(ctx context.Context, req *mnp.InitRequest, callback func(state mnp.SessionState)) error {
	initClient, err := c.serviceClient.Init(ctx, req)

	if err != nil {
		return err
	}

	return waitForReady(c.address, req.SessionId, initClient, callback)
}

func waitForReady(address string, sessionId string, initClient mnp.MnpService_InitClient, stateCallback func(state mnp.SessionState)) error {
	for {
		resp, err := initClient.Recv()
		if err == io.EOF {
			return errors.New("Received no final session state but EOF")
		}
		if err != nil {
			return err
		}
		state := resp.GetState()
		if stateCallback != nil {
			stateCallback(state)
		}
		logrus.Debugf("Client %s Session %s state %s", address, sessionId, state.String())
		if state == mnp.SessionState_SS_READY {
			return nil
		}
		if state == mnp.SessionState_SS_FAILED {
			return errors.Errorf("Init session failed: %s", resp.Error)
		}
	}
}
