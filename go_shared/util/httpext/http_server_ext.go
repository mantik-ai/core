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
package httpext

import (
	"net"
	"net/http"
)

/*
Extends Go Http Server.
- Listen/Serve Method.
*/
type ServerExt struct {
	http.Server
	listener   net.Listener
	ListenPort int
}

func (s *ServerExt) Listen() error {
	listener, err := net.Listen("tcp", s.Server.Addr)
	if err != nil {
		return err
	}
	s.listener = listener
	s.ListenPort = listener.Addr().(*net.TCPAddr).Port
	return nil
}

func (s *ServerExt) Serve() error {
	err := s.Server.Serve(s.listener)
	if err == http.ErrServerClosed {
		// this is not an error
		return nil
	}
	return err
}

func (s *ServerExt) ListenAndServe() error {
	if err := s.Listen(); err != nil {
		return err
	}
	return s.Serve()
}
