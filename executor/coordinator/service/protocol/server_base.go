package protocol

import (
	"coordinator/service/async"
	"coordinator/service/rpc_utils"
	"fmt"
	"github.com/pkg/errors"
	log "github.com/sirupsen/logrus"
	"net"
	"net/rpc"
	"net/rpc/jsonrpc"
)

// Base for SideCar and Coordinator Server
type ServerBase struct {
	server   *rpc.Server  // RPC Server
	listener net.Listener // TCP Listener
	Settings *Settings
	Ac       *async.AsyncCore
}

func createServerBase(settings *Settings) *ServerBase {
	result := ServerBase{
		server:   rpc.NewServer(),
		Settings: settings,
		Ac:       async.CreateAsyncCore(),
	}
	err := result.server.RegisterName("Generic", &genericHandler{&result})
	if err != nil {
		panic(err.Error())
	}
	return &result
}

func (s *ServerBase) listen() error {
	listener, err := s.Ac.Listen("tcp", fmt.Sprintf(":%d", s.Settings.Port))
	if err != nil {
		return errors.Wrap(err, "Could not listen")
	}
	s.listener = listener
	return nil
}

// The main accept function, should be started via StartGoroutine
func (s *ServerBase) Accept() {
	// using our own accept method, so that we can count the concurrent calls in a waiting group
	defer s.listener.Close()
	for {
		if s.Ac.IsTerminalState() {
			break
		}
		conn, err := s.Ac.Accept(s.listener)
		if err != nil {
			log.Print("rpc.Serve: accept:", err.Error())
			return
		}

		// Trick to catch the connection
		s.Ac.StartGoroutine(func(c net.Conn) func() {
			return func() {
				codec := jsonrpc.NewServerCodec(conn)
				// we serve one after another, as it's otherwise not easy cancellable
				for {
					if s.Ac.Context.Err() != nil {
						return
					}
					// TODO: ServeRequest is still not cancelable.
					err := s.server.ServeRequest(codec)
					if err != nil {
						return
					}
				}
			}
		}(conn))
	}
}

func (s *ServerBase) rpc(client *rpc.Client, serviceMethod string, args interface{}, reply interface{}) error {
	return rpc_utils.CallRpcWithTimeout(s.Ac.Context, s.Settings.RpcTimeout, client, serviceMethod, args, reply)
}

// Returns associated port
func (s *ServerBase) Port() int {
	if s.listener == nil {
		return s.Settings.Port
	} else {
		return s.listener.Addr().(*net.TCPAddr).Port
	}
}

// Force the Server to quit (for testcases)
func (s *ServerBase) ForceQuit() {
	s.listener.Close()
	s.Ac.Quit()
}

func (s *ServerBase) cancel(reason string) {
	log.Warnf("Got cancellation request %s", reason)
	s.Ac.Cancel(reason)
}

type genericHandler struct {
	s *ServerBase
}

func (h *genericHandler) Cancel(req *CancelRequest, res *CancelResponse) error {
	h.s.cancel(req.Reason)
	return nil
}
