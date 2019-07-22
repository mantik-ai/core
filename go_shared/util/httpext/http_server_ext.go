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
