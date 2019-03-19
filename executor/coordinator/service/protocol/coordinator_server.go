package protocol

import (
	"context"
	"coordinator/service/rpc_utils"
	"crypto/rand"
	"fmt"
	"github.com/pkg/errors"
	log "github.com/sirupsen/logrus"
	"net/rpc"
	"reflect"
	"strings"
	"sync"
)

// Implements the Server for the coordinator
type CoordinatorServer struct {
	ServerBase
	service            CoordinatorService
	handler            *CoordinatorProtocolHandler
	sideCars           map[string]*sideCarInfo
	CoordinatorAddress string
	mux                sync.Mutex
}

type sideCarInfo struct {
	Address     string
	s           *CoordinatorServer
	client      *rpc.Client
	challenge   []byte
	gotCallBack bool
}

func (i *sideCarInfo) RequestStream(req *RequestStream, res *RequestStreamResponse) error {
	return i.s.rpc(i.client, "SideCar.RequestStream", req, res)
}

func (i *sideCarInfo) RequestTransfer(req *RequestTransfer, res *RequestTransferResponse) error {
	return i.s.rpc(i.client, "SideCar.RequestTransfer", req, res)
}

func (i *sideCarInfo) RequestTransformation(req *RequestTransformation, res *RequestTransformationResponse) error {
	return i.s.rpc(i.client, "SideCar.RequestTransformation", req, res)
}

type CoordinatorService interface {
	StatusUpdate(req *StatusUpdate, res *StatusUpdateResponse) error
}

type CoordinatorProtocolHandler struct {
	server *CoordinatorServer
}

// Create a coordinator server
// Coordinator address is the advertised coordinator address
// It may omit the port, in which it will be automatically added
func CreateCoordinatorServer(coordinatorAddress string, service CoordinatorService, settings *Settings) (*CoordinatorServer, error) {
	log.Infof("Starting coordinator server for port %d (expected address=%s)", settings.Port, coordinatorAddress)
	var s CoordinatorServer
	s.ServerBase = *createServerBase(settings)
	s.handler = &CoordinatorProtocolHandler{&s}
	s.sideCars = make(map[string]*sideCarInfo)
	s.service = service
	err := s.server.RegisterName("Coordinator", s.handler)
	if err != nil {
		return nil, err
	}
	err = s.listen()
	if err != nil {
		return nil, err
	}
	s.CoordinatorAddress = addPortIfNotExisting(coordinatorAddress, s.Port())
	log.Printf("Coordinator started listening on %d", s.Port())
	return &s, nil
}

func (s *CoordinatorServer) ClaimSideCarWithPort(name string, host string, port int) error {
	return s.ClaimSideCar(name, fmt.Sprintf("%s:%d", host, port))
}

func (s *CoordinatorServer) ClaimSideCar(name string, address string) error {
	s.mux.Lock()
	_, exists := s.sideCars[name]
	s.mux.Unlock()

	if exists {
		return errors.Errorf("Sidecar %s already exists", name)
	}
	info := sideCarInfo{Address: address, s: s}
	client, err := rpc_utils.ConnectRpcWithTimeoutMultiple(s.Ac.Context, address, s.Settings.ConnectTimeout, s.Settings.RetryTime)
	if err != nil {
		log.Warningf("Could not connect to sidecar %s %s", name, address)
		return err
	}
	info.client = client
	info.challenge = make([]byte, 32)
	_, err = rand.Read(info.challenge)
	if err != nil {
		panic("Could not generate challenge")
	}
	s.mux.Lock()
	s.sideCars[name] = &info
	s.mux.Unlock()

	req := ClaimSideCarRequest{}
	var res ClaimSideCarResponse
	req.CoordinatorAddress = s.CoordinatorAddress
	req.Challenge = info.challenge
	req.Name = name
	err = s.rpc(info.client, "SideCar.ClaimSideCar", &req, &res)
	if err != nil {
		log.Warnf("Could not send claim request for %s, %s", name, err.Error())
		s.mux.Lock()
		delete(s.sideCars, name)
		s.mux.Unlock()
		return err
	}
	if res.IsError() {
		log.Warnf("SideCar responded with error %s", res.Error)
		s.mux.Lock()
		delete(s.sideCars, name)
		s.mux.Unlock()
		return errors.Errorf("SideCar responded with error %s", res.Error)
	}
	s.mux.Lock()
	defer s.mux.Unlock()
	if !info.gotCallBack {
		log.Warn("Sidecar missed callback")
		delete(s.sideCars, name)
		return errors.New("Sidecar missed call back")
	}
	return nil
}

// Add a port to a string address, if not yet given.
func addPortIfNotExisting(addr string, port int) string {
	pos := strings.LastIndex(addr, ":")
	if pos < 0 {
		return fmt.Sprintf("%s:%d", addr, port)
	} else {
		return addr
	}
}

// Shut down sidecars, you won't be able to communicate afterwards
func (s *CoordinatorServer) QuitSideCars() error {
	for name, sideCar := range s.sideCars {
		log.Infof("Closing side car %s", name)
		var req QuitSideCarRequest
		var res QuitSideCarResponse
		// Note: we do not use our cancellable context here
		// As we want this requests to go through even when shutting down...
		err := rpc_utils.CallRpcWithTimeout(
			context.Background(),
			s.Settings.RpcTimeout,
			sideCar.client,
			"SideCar.QuitSideCar",
			&req,
			&res,
		)
		if err != nil {
			log.Warnf("Got an error on sending side car quit to %s, %s", name, err.Error())
		}
	}
	s.mux.Lock()
	s.sideCars = make(map[string]*sideCarInfo)
	s.mux.Unlock()
	return nil
}

func (s *CoordinatorServer) GetSideCar(name string) (SideCarService, error) {
	info, exists := s.sideCars[name]
	if !exists {
		return nil, errors.New("Sidecar is not attached")
	}
	return info, nil
}

func (s *CoordinatorProtocolHandler) HelloCoordinator(req *HelloCoordinatorRequest, res *HelloCoordinatorResponse) error {
	s.server.mux.Lock()
	defer s.server.mux.Unlock()

	sideCar, found := s.server.sideCars[req.Name]
	if !found {
		res.Error = "Unknown side car"
		return nil
	}
	if !reflect.DeepEqual(sideCar.challenge, req.Challenge) {
		res.Error = "Challenge mismatch"
		return nil
	}
	// all fine
	sideCar.gotCallBack = true
	return nil
}

func (s *CoordinatorProtocolHandler) StatusUpdate(req *StatusUpdate, res *StatusUpdateResponse) error {
	sideCar, found := s.server.sideCars[req.Name]
	if !found {
		res.Error = "Unknown side car"
		return nil
	}
	if !reflect.DeepEqual(sideCar.challenge, req.Challenge) {
		res.Error = "Challenge mismatch"
		return nil
	}
	return s.server.service.StatusUpdate(req, res)
}

func (s *CoordinatorServer) RunAsync() {
	s.Ac.StartGoroutine(s.Accept)
}
