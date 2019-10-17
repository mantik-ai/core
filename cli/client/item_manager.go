package client

import (
	"cli/protos/mantik/engine"
	"context"
	"github.com/sirupsen/logrus"
	"google.golang.org/grpc"
)

// Simplified handling of placing items in a session
// Together with ItemWrapper
type ItemManager struct {
	sessionService engine.SessionServiceClient
	builder        engine.GraphBuilderServiceClient
	executor       engine.GraphExecutorServiceClient
	sessionId      string
}

func newItemManager(con *grpc.ClientConn) *ItemManager {
	return &ItemManager{
		engine.NewSessionServiceClient(con),
		engine.NewGraphBuilderServiceClient(con),
		engine.NewGraphExecutorServiceClient(con),
		"",
	}
}

func (i *ItemManager) close() {
	if i.sessionId != "" {
		_, err := i.sessionService.CloseSession(
			context.Background(),
			&engine.CloseSessionRequest{
				SessionId: i.sessionId,
			})
		if err != nil {
			logrus.Warn("Could not close session", err.Error())
		}
		i.sessionId = ""
	}
}

func (i *ItemManager) ensureSession() error {
	if i.sessionId == "" {
		response, err := i.sessionService.CreateSession(
			context.Background(),
			&engine.CreateSessionRequest{},
		)
		if err != nil {
			return err
		}
		i.sessionId = response.SessionId
	}
	return nil
}

func (i *ItemManager) wrapNodeResponse(response *engine.NodeResponse, err error) (*ItemWrapper, error) {
	if err != nil {
		return nil, err
	}
	return &ItemWrapper{
		i,
		response,
	}, nil
}

func (i *ItemManager) Get(itemId string) (*ItemWrapper, error) {
	err := i.ensureSession()
	if err != nil {
		return nil, err
	}
	itemResponse, err := i.builder.Get(context.Background(), &engine.GetRequest{
		SessionId: i.sessionId,
		Name:      itemId,
	})
	return i.wrapNodeResponse(itemResponse, err)
}
