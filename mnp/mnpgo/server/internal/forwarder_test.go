package internal

import (
	"bytes"
	"context"
	"fmt"
	"github.com/golang/protobuf/ptypes/empty"
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/protos/mantik/mnp"
	"golang.org/x/sync/errgroup"
	"google.golang.org/grpc"
	"net"
	"testing"
)

// Implements MNP for checking correct push
type pushHandler struct {
	PushPort    int
	Received    bytes.Buffer
	Calls       int
	EofReceived bool
	TaskId      string
	SessionId   string
}

func (p *pushHandler) About(ctx context.Context, empty *empty.Empty) (*mnp.AboutResponse, error) {
	panic("implement me")
}

func (p *pushHandler) Init(request *mnp.InitRequest, server mnp.MnpService_InitServer) error {
	panic("implement me")
}

func (p *pushHandler) Quit(ctx context.Context, request *mnp.QuitRequest) (*mnp.QuitResponse, error) {
	panic("implement me")
}

func (p *pushHandler) AboutSession(ctx context.Context, request *mnp.AboutSessionRequest) (*mnp.AboutSessionResponse, error) {
	panic("implement me")
}

func (p *pushHandler) QuitSession(ctx context.Context, request *mnp.QuitSessionRequest) (*mnp.QuitSessionResponse, error) {
	panic("implement me")
}

func (p *pushHandler) Push(server mnp.MnpService_PushServer) error {
	first, err := server.Recv()
	if err != nil {
		return err
	}
	p.PushPort = (int)(first.Port)
	p.Calls = 1
	p.Received = bytes.Buffer{}
	p.TaskId = first.TaskId
	p.SessionId = first.SessionId
	for {
		next, err := server.Recv()
		if err != nil {
			return err
		}
		p.Calls += 1
		p.Received.Write(next.Data)
		if next.Done {
			p.EofReceived = true
			break
		}
	}
	return server.SendAndClose(&mnp.PushResponse{})
}

func (p *pushHandler) Pull(request *mnp.PullRequest, server mnp.MnpService_PullServer) error {
	panic("implement me")
}

func (p *pushHandler) QueryTask(ctx context.Context, request *mnp.QueryTaskRequest) (*mnp.QueryTaskResponse, error) {
	panic("implement me")
}

func makeDummyServer(service mnp.MnpServiceServer, t *testing.T) (string, *grpc.Server) {
	server := grpc.NewServer()
	mnp.RegisterMnpServiceServer(server, service)
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	addr := fmt.Sprintf("localhost:%d", listener.Addr().(*net.TCPAddr).Port)
	assert.NoError(t, err)
	go func() {
		server.Serve(listener)
	}()
	return addr, server
}

func TestForwarderRun(t *testing.T) {
	service := pushHandler{}
	addr, server := makeDummyServer(&service, t)
	defer server.Stop()
	multiplexer := NewStreamMultiplexer(0, 2)
	mnpUrl := fmt.Sprintf("mnp://%s/session1/2", addr)
	forwader, err := MakeForwarder(context.Background(), multiplexer, 1, "task1", mnpUrl)
	assert.NoError(t, err)
	eg := errgroup.Group{}
	eg.Go(func() error {
		return forwader.Run()
	})

	outWriter := multiplexer.OutputWrites[1]
	outWriter.Write([]byte("Hello "))
	outWriter.Write([]byte("World"))
	outWriter.Close()
	multiplexer.Finalize(nil)
	err = eg.Wait()
	assert.NoError(t, err)
	assert.True(t, service.Calls >= 2)
	assert.Equal(t, "session1", service.SessionId)
	assert.Equal(t, "task1", service.TaskId)
	assert.Equal(t, 2, service.PushPort)
	assert.True(t, service.EofReceived)
	assert.Equal(t, []byte("Hello World"), service.Received.Bytes())
}

func TestVerySmallSnippets(t *testing.T) {
	service := pushHandler{}
	addr, server := makeDummyServer(&service, t)
	defer server.Stop()
	multiplexer := NewStreamMultiplexer(0, 2)
	mnpUrl := fmt.Sprintf("mnp://%s/session1/2", addr)
	forwader, err := MakeForwarder(context.Background(), multiplexer, 1, "task1", mnpUrl)
	assert.NoError(t, err)
	eg := errgroup.Group{}
	eg.Go(func() error {
		return forwader.Run()
	})

	outWriter := multiplexer.OutputWrites[1]
	for i := 0; i < 10000; i++ {
		outWriter.Write([]byte("ABCD"))
	}
	outWriter.Close()
	multiplexer.Finalize(nil)
	err = eg.Wait()
	assert.NoError(t, err)

	assert.True(t, service.EofReceived)
	assert.True(t, service.Calls < 1000)
}
