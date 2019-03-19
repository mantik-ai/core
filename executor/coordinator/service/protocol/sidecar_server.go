package protocol

import (
	"coordinator/service/rpc_utils"
	"fmt"
	"github.com/pkg/errors"
	log "github.com/sirupsen/logrus"
	"net/rpc"
	"sync"
)

type SideCarServerState int

const (
	WaitCoordinator SideCarServerState = iota
	CoordinatorAttaching
	CoordinatorAttached
	SideCarFailed
	SideCarDone
)

func (state SideCarServerState) String() string {
	return [...]string{"WaitCoordinator", "CoordinatorAttaching", "CoordinatorAttached", "SideCarFailed", "SideCarDone"}[state]
}

// External Implementation of the side car service
type SideCarService interface {
	RequestStream(req *RequestStream, res *RequestStreamResponse) error
	RequestTransfer(req *RequestTransfer, res *RequestTransferResponse) error
	RequestTransformation(req *RequestTransformation, res *RequestTransformationResponse) error
}

type sideCarApi struct {
	server *SideCarServer
}

// Implements the protocol server for the Sidecar
type SideCarServer struct {
	ServerBase
	state   SideCarServerState // Current State
	api     *sideCarApi        // The API Bound to the RPC Service
	service SideCarService

	mux       sync.Mutex
	lastError error

	// Coordinator information
	coordinatorAddress string
	coordinatorPort    int
	coordinatorClient  *rpc.Client
	// the name the coordinator gave us
	coordinatorOwnName string
	// the challenge the coordinator gave us
	coordinatorChallenge []byte
}

func CreateSideCarServer(settings *Settings, service SideCarService) (*SideCarServer, error) {
	log.Printf("Starting side server for port %d", settings.Port)
	var s SideCarServer
	s.ServerBase = *createServerBase(settings)
	s.state = WaitCoordinator
	s.api = &sideCarApi{&s}
	s.service = service
	err := s.server.RegisterName("SideCar", s.api)
	if err != nil {
		return nil, err
	}
	err = s.listen()
	if err != nil {
		return nil, err
	}
	log.Printf("SideCar started listening on %d", s.Port())
	return &s, nil
}

func (s *SideCarServer) RunAsync() {
	s.Ac.SetTimeout(s.Settings.WaitForCoordinatorTimeout, func() {
		s.mux.Lock()
		defer s.mux.Unlock()
		if s.state == WaitCoordinator {
			log.Warnf("Timeout when waiting for coordinator")
			s.state = SideCarFailed
			s.Ac.Fail(errors.New("Timeout while waiting for coordinator"))
		}
	})
	s.Ac.StartGoroutine(func() {
		s.Accept()
	})
}

func (s *SideCarServer) stateChange(expected SideCarServerState, newState SideCarServerState) error {
	s.mux.Lock()
	defer s.mux.Unlock()
	if s.state == expected {
		s.state = newState
		return nil
	} else {
		return errors.Errorf("Expected state %s got %s", expected.String(), s.state.String())
	}
}

func (s *SideCarServer) fail(msg string) {
	log.Warnf("Going into failure state %s", msg)
	s.mux.Lock()
	defer s.mux.Unlock()
	s.state = SideCarFailed
	s.Ac.Fail(errors.New(msg))
}

func (a *sideCarApi) ClaimSideCar(request *ClaimSideCarRequest, response *ClaimSideCarResponse) error {
	err := a.server.stateChange(WaitCoordinator, CoordinatorAttaching)
	if err != nil {
		response.Error = err.Error()
		return nil
	}

	log.Infof("Got call from coordinator %s naming me %s, calling back", request.CoordinatorAddress, request.Name)

	a.server.coordinatorClient, err = rpc_utils.ConnectRpcWithTimeoutMultiple(a.server.Ac.Context, request.CoordinatorAddress, a.server.Settings.ConnectTimeout, a.server.Settings.RetryTime)
	if err != nil {
		a.server.fail("Could not reach Coordinator")
		response.Error = "Could not reach coordinator"
		return nil
	}

	var hello HelloCoordinatorRequest
	var helloResponse HelloCoordinatorResponse
	hello.Challenge = request.Challenge
	hello.Name = request.Name
	err = a.server.coordinatorClient.Call("Coordinator.HelloCoordinator", &hello, &helloResponse)

	if err != nil {
		a.server.fail("Could not call Coordinator Hello")
		response.Error = "Internal error: Could not call coordinator"
		return nil
	}

	if helloResponse.IsError() {
		a.server.fail(fmt.Sprintf("Coordinator responded with error on hello: %s", helloResponse.Error))
		response.Error = "Coordinator responded with error"
		return nil
	}

	err = a.server.stateChange(CoordinatorAttaching, CoordinatorAttached)
	if err != nil {
		a.server.fail("Could not go Coordinator Attached")
		response.Error = "Internal error"
		return nil
	}

	a.server.mux.Lock()
	defer a.server.mux.Unlock()

	a.server.coordinatorAddress = request.CoordinatorAddress
	a.server.coordinatorOwnName = request.Name
	a.server.coordinatorChallenge = request.Challenge
	return nil
}

func (s *SideCarServer) checkConnected() error {
	s.mux.Lock()
	defer s.mux.Unlock()
	if s.state != CoordinatorAttached {
		return errors.New("Bad State")
	}
	return nil
}

func (a *sideCarApi) RequestStream(req *RequestStream, res *RequestStreamResponse) error {
	err := a.server.checkConnected()
	if err != nil {
		return err
	}
	// Forwarding call.
	return a.server.service.RequestStream(req, res)
}

func (a *sideCarApi) RequestTransformation(req *RequestTransformation, res *RequestTransformationResponse) error {
	err := a.server.checkConnected()
	if err != nil {
		return err
	}
	// Forwarding call.
	return a.server.service.RequestTransformation(req, res)
}

func (a *sideCarApi) RequestTransfer(req *RequestTransfer, res *RequestTransferResponse) error {
	err := a.server.checkConnected()
	if err != nil {
		return err
	}
	// Forwarding call.
	return a.server.service.RequestTransfer(req, res)
}

func (a *sideCarApi) QuitSideCar(req *QuitSideCarRequest, res *QuitSideCarResponse) error {
	a.server.Ac.Quit()
	log.Info("Quit Request received")
	a.server.mux.Lock()
	defer a.server.mux.Unlock()
	a.server.state = SideCarDone
	return nil
}

func (s *SideCarServer) Coordinator() (CoordinatorService, error) {
	if s.state == CoordinatorAttached {
		return &coordinatorInfo{s}, nil
	} else {
		return nil, errors.New("Not attached")
	}
}

type coordinatorInfo struct {
	s *SideCarServer
}

func (i *coordinatorInfo) StatusUpdate(req *StatusUpdate, res *StatusUpdateResponse) error {
	req.Challenge = i.s.coordinatorChallenge
	req.Name = i.s.coordinatorOwnName
	return i.s.coordinatorClient.Call("Coordinator.StatusUpdate", req, res)
}
