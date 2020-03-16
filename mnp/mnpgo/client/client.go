package client

import (
	"context"
	"github.com/golang/protobuf/ptypes/any"
	"github.com/golang/protobuf/ptypes/empty"
	"github.com/pkg/errors"
	"github.com/sirupsen/logrus"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/protos/mantik/mnp"
	"google.golang.org/grpc"
	"io"
)

// A Simple client, which behaves like a Handler
// Only responsible for one client. Cannot do forwarding
type Client struct {
	con           *grpc.ClientConn
	ctx           context.Context
	serviceClient mnp.MnpServiceClient
}

func ConnectClient(address string) (*Client, error) {
	clientCon, err := grpc.Dial(address, grpc.WithInsecure())
	if err != nil {
		return nil, err
	}
	return &Client{
		con:           clientCon,
		serviceClient: mnp.NewMnpServiceClient(clientCon),
		ctx:           context.Background(),
	}, nil
}

func (c *Client) About() (mnpgo.AboutResponse, error) {
	aboutResponse, err := c.serviceClient.About(c.ctx, &empty.Empty{})
	if err != nil {
		logrus.Warn("Got error on about call", err)
		return mnpgo.AboutResponse{}, err
	}
	return mnpgo.AboutResponse{
		Name:  aboutResponse.Name,
		Extra: aboutResponse.Extra,
	}, nil
}

func (c *Client) Quit() error {
	_, err := c.serviceClient.Quit(c.ctx, &mnp.QuitRequest{})
	if err != nil {
		logrus.Warn("Error on quit call", err)
	}
	return err
}

func (c *Client) Init(
	sessionId string,
	configuration *any.Any,
	contentTypes *mnpgo.PortConfiguration,
	stateCallback func(state mnp.SessionState),
) (mnpgo.SessionHandler, error) {
	request := mnp.InitRequest{
		SessionId:     sessionId,
		Configuration: configuration,
	}
	if contentTypes != nil {
		request.Inputs = make([]*mnp.ConfigureInputPort, len(contentTypes.Inputs), len(contentTypes.Inputs))
		for i, inputConfiguration := range contentTypes.Inputs {
			request.Inputs[i] = &mnp.ConfigureInputPort{
				ContentType: inputConfiguration.ContentType,
			}
		}
		request.Outputs = make([]*mnp.ConfigureOutputPort, len(contentTypes.Outputs), len(contentTypes.Outputs))
		for i, outputConfiguration := range contentTypes.Outputs {
			// No support for fowrading here
			request.Outputs[i] = &mnp.ConfigureOutputPort{
				ContentType: outputConfiguration.ContentType,
			}
		}
	}

	initClient, err := c.serviceClient.Init(c.ctx, &request)

	if err != nil {
		return nil, err
	}

	err = waitForReady(sessionId, initClient, stateCallback)
	if err != nil {
		return nil, err
	}

	return NewClientSession(sessionId, c.ctx, c.serviceClient), nil
}

func waitForReady(sessionId string, initClient mnp.MnpService_InitClient, stateCallback func(state mnp.SessionState)) error {
	for {
		resp, err := initClient.Recv()
		if err == io.EOF {
			return errors.New("Received no final session state but EOF")
		}
		if err != nil {
			return err
		}
		state := resp.GetState()
		if stateCallback != nil {
			stateCallback(state)
		}
		logrus.Debugf("Session %s state %s", sessionId, state.String())
		if state == mnp.SessionState_SS_READY {
			return nil
		}
		if state == mnp.SessionState_SS_FAILED {
			return errors.Errorf("Init session failed: %s", resp.Error)
		}
	}
}
