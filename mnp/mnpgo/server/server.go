package server

import (
	"github.com/pkg/errors"
	"github.com/sirupsen/logrus"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/protos/mantik/mnp"
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
