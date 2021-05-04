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
	"cli/protos/mantik/engine"
	"context"
	"fmt"
	"github.com/golang/protobuf/ptypes/empty"
	"google.golang.org/grpc"
)

const DefaultPort = 8087
const DefaultHost = "127.0.0.1"

type ClientArguments struct {
	// Engine Address
	Host string
	Port int
}

/** Wraps RPC Commands to Engine. */
type EngineClient struct {
	context        context.Context
	con            *grpc.ClientConn
	aboutService   engine.AboutServiceClient
	LocalRegistry  engine.LocalRegistryServiceClient
	RemoteRegistry engine.RemoteRegistryServiceClient
	Items          *ItemManager
}

func MakeEngineClientInsecure(args *ClientArguments) (*EngineClient, error) {
	address := fmt.Sprintf("%s:%d", args.Host, args.Port)
	con, err := grpc.Dial(address, grpc.WithInsecure())
	if err != nil {
		return nil, err
	}
	return NewEngineClient(con)
}

func NewEngineClient(con *grpc.ClientConn) (*EngineClient, error) {
	return &EngineClient{
		context.Background(),
		con,
		engine.NewAboutServiceClient(con),
		engine.NewLocalRegistryServiceClient(con),
		engine.NewRemoteRegistryServiceClient(con),
		newItemManager(con),
	}, nil
}

func (e *EngineClient) Address() string {
	return e.con.Target()
}

func (e *EngineClient) Close() error {
	e.Items.close()
	return e.con.Close()
}

func (e *EngineClient) Version() (*engine.VersionResponse, error) {
	return e.aboutService.Version(e.context, &empty.Empty{})
}
