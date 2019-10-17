package client

import (
	"cli/protos/mantik/engine"
	"context"
	"fmt"
	"github.com/golang/protobuf/ptypes/empty"
	"google.golang.org/grpc"
)

const DefaultPort = 8087
const DefaultHost = "127.0.0.1"

type ClientArguments struct {
	// Engine Address
	Host string
	Port int
}

/** Wraps RPC Commands to Engine. */
type EngineClient struct {
	context       context.Context
	con           *grpc.ClientConn
	aboutService  engine.AboutServiceClient
	LocalRegistry engine.LocalRegistryServiceClient
	Items         *ItemManager
}

func MakeEngineClientInsecure(args *ClientArguments) (*EngineClient, error) {
	address := fmt.Sprintf("%s:%d", args.Host, args.Port)
	con, err := grpc.Dial(address, grpc.WithInsecure())
	if err != nil {
		return nil, err
	}
	return NewEngineClient(con)
}

func NewEngineClient(con *grpc.ClientConn) (*EngineClient, error) {
	return &EngineClient{
		context.Background(),
		con,
		engine.NewAboutServiceClient(con),
		engine.NewLocalRegistryServiceClient(con),
		newItemManager(con),
	}, nil
}

func (e *EngineClient) Address() string {
	return e.con.Target()
}

func (e *EngineClient) Close() error {
	e.Items.close()
	return e.con.Close()
}

func (e *EngineClient) Version() (*engine.VersionResponse, error) {
	return e.aboutService.Version(e.context, &empty.Empty{})
}
