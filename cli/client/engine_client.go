package client

import (
	"cli/protos/mantik/engine"
	"context"
	"fmt"
	"github.com/golang/protobuf/ptypes/empty"
	"google.golang.org/grpc"
)

/** Wraps RPC Commands to Engine. */
type EngineClient struct {
	context      context.Context
	con          *grpc.ClientConn
	aboutService engine.AboutServiceClient
}

func MakeEngineClientInsecure(host string, port int) (*EngineClient, error) {
	address := fmt.Sprintf("127.0.0.1:%d", port)
	con, err := grpc.Dial(address, grpc.WithInsecure())
	if err != nil {
		return nil, err
	}
	return MakeEngineClient(con)
}

func MakeEngineClient(con *grpc.ClientConn) (*EngineClient, error) {
	return &EngineClient{
		context.Background(),
		con,
		engine.NewAboutServiceClient(con),
	}, nil
}

func (e *EngineClient) Address() string {
	return e.con.Target()
}

func (e *EngineClient) Close() error {
	return e.con.Close()
}

func (e *EngineClient) Version() (*engine.VersionResponse, error) {
	return e.aboutService.Version(e.context, &empty.Empty{})
}
