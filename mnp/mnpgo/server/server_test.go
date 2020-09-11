package server

import (
	"bytes"
	"context"
	"fmt"
	"github.com/pkg/errors"
	"github.com/sirupsen/logrus"
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/client"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/protos/mantik/mnp"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/server/internal"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/util"
	"google.golang.org/grpc"
	"io"
	"testing"
)

func setupServer(t *testing.T, handler mnpgo.Handler) *Server {
	s := NewServer(handler)
	err := s.Listen("127.0.0.1:0")
	assert.NoError(t, err)

	go func() {
		mainErr := s.Serve()
		if mainErr != nil {
			logrus.Error("Error stopping?", mainErr)
		}
	}()
	return s
}

func tearDown(s *Server) {
	s.Stop()
}

func TestConnectAboutQuit(t *testing.T) {
	var dummy internal.DummyHandler
	s := setupServer(t, &dummy)
	defer tearDown(s)

	c, err := client.ConnectClient(fmt.Sprintf("127.0.0.1:%d", s.Port()))
	assert.NoError(t, err)

	about, err := c.About()
	assert.Equal(t, "TestHandler1", about.Name)

	err = c.Quit()
	assert.NoError(t, err)
	assert.True(t, dummy.QuitCalled)
}

func TestInit(t *testing.T) {
	var dummy internal.DummyHandler
	s := setupServer(t, &dummy)
	defer tearDown(s)

	c, err := client.ConnectClient(fmt.Sprintf("127.0.0.1:%d", s.Port()))
	assert.NoError(t, err)

	contentTypes := mnpgo.PortConfiguration{
		Inputs:  []mnpgo.InputPortConfiguration{{"abc"}},
		Outputs: []mnpgo.OutputPortConfiguration{{"xyz", ""}, {"bar", ""}},
	}

	callback := func(state mnp.SessionState) {

	}

	session, err := c.Init("session1", nil, &contentTypes, callback)
	assert.NoError(t, err)

	originalSession := dummy.Sessions[0]

	assert.Equal(t, &contentTypes, originalSession.ContentTypes)

	assert.Equal(t, []string{"session1"}, s.service.GetSessions())

	err = session.Quit()
	assert.NoError(t, err)

	assert.True(t, originalSession.Quitted)
	assert.Equal(t, []string{}, s.service.GetSessions())
}

func TestEnsureTask(t *testing.T) {
	var dummy internal.DummyHandler
	s := setupServer(t, &dummy)
	defer tearDown(s)

	addr := fmt.Sprintf("127.0.0.1:%d", s.Port())

	c, err := client.ConnectClient(addr)
	assert.NoError(t, err)
	defer c.Close()

	contentTypes := mnpgo.PortConfiguration{
		Inputs:  []mnpgo.InputPortConfiguration{{"abc"}},
		Outputs: []mnpgo.OutputPortConfiguration{{"xyz", ""}, {"bar", ""}},
	}

	_, err = c.Init("session1", nil, &contentTypes, nil)
	assert.NoError(t, err)

	clientCon, err := grpc.Dial(addr, grpc.WithInsecure())
	defer clientCon.Close()
	assert.NoError(t, err)
	mnpServiceClient := mnp.NewMnpServiceClient(clientCon)

	response, err := mnpServiceClient.QueryTask(context.Background(), &mnp.QueryTaskRequest{
		SessionId: "session1",
		TaskId:    "task1",
		Ensure:    false,
	})
	assert.NoError(t, err)
	assert.Equal(t, mnp.TaskState_TS_UNKNOWN, response.State)

	response, err = mnpServiceClient.QueryTask(context.Background(), &mnp.QueryTaskRequest{
		SessionId: "session1",
		TaskId:    "task1",
		Ensure:    true,
	})
	assert.NoError(t, err)
	assert.Equal(t, mnp.TaskState_TS_EXISTS, response.State)

	response, err = mnpServiceClient.QueryTask(context.Background(), &mnp.QueryTaskRequest{
		SessionId: "session1",
		TaskId:    "task1",
		Ensure:    false,
	})
	assert.NoError(t, err)
	assert.Equal(t, mnp.TaskState_TS_EXISTS, response.State)
}

func TestDataTransformation(t *testing.T) {
	var dummy internal.DummyHandler
	s := setupServer(t, &dummy)
	defer tearDown(s)

	c, err := client.ConnectClient(fmt.Sprintf("127.0.0.1:%d", s.Port()))
	assert.NoError(t, err)

	contentTypes := mnpgo.PortConfiguration{
		Inputs:  []mnpgo.InputPortConfiguration{{"abc"}},
		Outputs: []mnpgo.OutputPortConfiguration{{"xyz", ""}, {"bar", ""}},
	}

	session, err := c.Init("session1", nil, &contentTypes, nil)
	assert.NoError(t, err)

	input := bytes.NewBuffer([]byte{1, 2, 3, 4})
	output1 := util.NewClosableBuffer()
	output2 := util.NewClosableBuffer()

	err = session.RunTask(
		context.Background(),
		"task1",
		[]io.Reader{input},
		[]io.WriteCloser{output1, output2},
	)

	assert.NoError(t, err)
	assert.True(t, output1.Closed)
	assert.True(t, output2.Closed)
	assert.Equal(t, []byte{1, 2, 3, 4}, output1.Buffer.Bytes())
	assert.Equal(t, []byte{2, 4, 6, 8}, output2.Buffer.Bytes())

	serverSession, _ := s.service.getSession("session1")
	assert.Empty(t, serverSession.GetTasks(true))
	assert.Equal(t, []string{"task1"}, serverSession.GetTasks(false))

	queryResponse, err := s.service.QueryTask(context.Background(), &mnp.QueryTaskRequest{
		SessionId: "session1",
		TaskId:    "task1",
	})
	assert.NoError(t, err)
	assert.Equal(t, mnp.TaskState_TS_FINISHED, queryResponse.State)
	assert.Empty(t, queryResponse.Error)
	assert.Empty(t, queryResponse.Inputs[0].Error)
	assert.Empty(t, queryResponse.Outputs[0].Error)
	assert.True(t, queryResponse.Inputs[0].MsgCount > 0)
	assert.True(t, queryResponse.Inputs[0].Data > 0)
	assert.True(t, queryResponse.Inputs[0].Done)
	assert.True(t, queryResponse.Outputs[0].MsgCount > 0)
	assert.True(t, queryResponse.Outputs[0].Data > 0)
	assert.True(t, queryResponse.Outputs[0].Done)

	err = session.Quit()
	assert.NoError(t, err)
}

func TestFailingTransformation(t *testing.T) {
	var dummy internal.DummyHandler
	s := setupServer(t, &dummy)
	defer tearDown(s)

	c, err := client.ConnectClient(fmt.Sprintf("127.0.0.1:%d", s.Port()))
	assert.NoError(t, err)

	contentTypes := mnpgo.PortConfiguration{
		Inputs:  []mnpgo.InputPortConfiguration{{"abc"}},
		Outputs: []mnpgo.OutputPortConfiguration{{"xyz", ""}, {"bar", ""}},
	}

	session, err := c.Init("session1", nil, &contentTypes, nil)
	assert.NoError(t, err)

	dummy.Sessions[0].Fail = errors.New("I am failed")

	input := bytes.NewBuffer([]byte{1, 2, 3, 4})
	output1 := util.NewClosableBuffer()
	output2 := util.NewClosableBuffer()

	err = session.RunTask(
		context.Background(),
		"task1",
		[]io.Reader{input},
		[]io.WriteCloser{output1, output2},
	)

	assert.Error(t, err)

	assert.True(t, output1.Closed)
	assert.True(t, output2.Closed)

	serverSession, _ := s.service.getSession("session1")
	assert.Empty(t, serverSession.GetTasks(true))
	assert.Equal(t, []string{"task1"}, serverSession.GetTasks(false))

	err = session.Quit()
	assert.NoError(t, err)
}
