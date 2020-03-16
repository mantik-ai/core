package server

import (
	"bytes"
	"context"
	"fmt"
	"github.com/sirupsen/logrus"
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/client"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/protos/mantik/mnp"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/server/internal"
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
		Outputs: []mnpgo.OutputPortConfiguration{{"xyz"}, {"bar"}},
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

func TestDataTransformation(t *testing.T) {
	var dummy internal.DummyHandler
	s := setupServer(t, &dummy)
	defer tearDown(s)

	c, err := client.ConnectClient(fmt.Sprintf("127.0.0.1:%d", s.Port()))
	assert.NoError(t, err)

	contentTypes := mnpgo.PortConfiguration{
		Inputs:  []mnpgo.InputPortConfiguration{{"abc"}},
		Outputs: []mnpgo.OutputPortConfiguration{{"xyz"}, {"bar"}},
	}

	session, err := c.Init("session1", nil, &contentTypes, nil)
	assert.NoError(t, err)

	input := bytes.NewBuffer([]byte{1, 2, 3, 4})
	output1 := CloseableBuffer{}
	output2 := CloseableBuffer{}

	err = session.RunTask(
		context.Background(),
		"task1",
		[]io.Reader{input},
		[]io.WriteCloser{&output1, &output2},
	)

	assert.NoError(t, err)
	assert.True(t, output1.Closed)
	assert.True(t, output2.Closed)
	assert.Equal(t, []byte{1, 2, 3, 4}, output1.Buffer.Bytes())
	assert.Equal(t, []byte{2, 4, 6, 8}, output2.Buffer.Bytes())

	serverSession, _ := s.service.getSession("session1")
	assert.Empty(t, serverSession.GetTasks())

	err = session.Quit()
	assert.NoError(t, err)
}

type CloseableBuffer struct {
	bytes.Buffer
	Closed bool
}

func (c *CloseableBuffer) Close() error {
	c.Closed = true
	return nil
}
