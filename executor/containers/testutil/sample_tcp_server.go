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
package testutil

import (
	"fmt"
	"io"
	"io/ioutil"
	"net"
)

type SampleTcpSource struct {
	l        net.Listener
	Port     int
	Requests int
	reader   io.Reader
}

func CreateSampleTcpSource(reader io.Reader) *SampleTcpSource {
	var r SampleTcpSource
	var err error
	r.l, err = net.Listen("tcp", "localhost:0")
	if err != nil {
		panic(err)
	}
	r.Port = r.l.Addr().(*net.TCPAddr).Port
	r.reader = reader

	go r.run()

	return &r
}

func (s *SampleTcpSource) Close() {
	s.l.Close()
}

func (s *SampleTcpSource) Url() string {
	return fmt.Sprintf("tcp://localhost:%d", s.Port)
}

func (s *SampleTcpSource) run() {
	for {
		c, err := s.l.Accept()
		s.Requests++
		if err != nil {
			return
		}
		io.Copy(c, s.reader)
		c.Close()
	}
}

func TcpPullData(address string) ([]byte, error) {
	resp, err := net.Dial("tcp", address)
	if err != nil {
		return nil, err
	}
	defer resp.Close()
	result, err := ioutil.ReadAll(resp)
	return result, err
}
