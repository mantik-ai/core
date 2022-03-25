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
	"github.com/mantik-ai/core/mnp/mnpgo"
	"github.com/mantik-ai/core/mnp/mnpgo/protos/mantik/mnp"
	"github.com/pkg/errors"
	"github.com/sirupsen/logrus"
	"google.golang.org/grpc"
	"net"
)

type Server struct {
	handler  mnpgo.Handler
	service  *MnpServiceServer
	server   *grpc.Server
	listener net.Listener
}

func NewServer(handler mnpgo.Handler) *Server {
	s := Server{
		handler: handler,
	}
	s.service = NewMnpServiceServer(handler)
	s.server = grpc.NewServer()
	mnp.RegisterMnpServiceServer(s.server, s.service)
	return &s
}

func (b *Server) Listen(address string) error {
	listener, err := net.Listen("tcp", address)
	if err != nil {
		return errors.Wrap(err, "Listening BridgeServer failed")
	}
	logrus.Info("Mnp Server listening on ", listener.Addr().String())
	b.listener = listener
	return nil
}

func (b *Server) Address() string {
	return b.listener.Addr().String()
}

func (b *Server) Port() int {
	return b.listener.Addr().(*net.TCPAddr).Port
}

func (b *Server) Serve() error {
	if b.listener == nil {
		return errors.New("Not listening")
	}
	return b.server.Serve(b.listener)
}

func (b *Server) Stop() error {
	logrus.Info("BridgeServer stopping")
	b.server.Stop()
	return nil
}
