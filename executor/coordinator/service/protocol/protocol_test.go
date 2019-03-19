package protocol

import (
	"fmt"
	"github.com/stretchr/testify/assert"
	"testing"
)

type sideCarDummy struct {
	requestStreamCalled         bool
	requestTransferCalled       bool
	RequestTransformationCalled bool
}

type coordinatorDummy struct {
	statusUpdateCalled bool
}

func (c *coordinatorDummy) StatusUpdate(req *StatusUpdate, res *StatusUpdateResponse) error {
	c.statusUpdateCalled = true
	return nil
}

func (s *sideCarDummy) RequestStream(req *RequestStream, res *RequestStreamResponse) error {
	res.Port = 100
	s.requestStreamCalled = true
	return nil
}

func (s *sideCarDummy) RequestTransfer(req *RequestTransfer, res *RequestTransferResponse) error {
	s.requestTransferCalled = true
	return nil
}

func (s *sideCarDummy) RequestTransformation(req *RequestTransformation, res *RequestTransformationResponse) error {
	res.Port = 101
	s.RequestTransformationCalled = true
	return nil
}

func TestSimpleABProtocol(t *testing.T) {
	sideCarSettings := CreateDefaultSettings()
	sideCarSettings.Port = 50001
	coordinatorSettings := CreateDefaultSettings()
	coordinatorSettings.Port = 50002

	c, err := CreateCoordinatorServer("localhost:50002", &coordinatorDummy{}, coordinatorSettings)
	assert.NoError(t, err)
	assert.Equal(t, 50002, c.Port())

	s, err := CreateSideCarServer(sideCarSettings, &sideCarDummy{})
	assert.Equal(t, 50001, s.Port())

	c.RunAsync()
	s.RunAsync()

	err = c.ClaimSideCar("node1", "localhost:50001")
	assert.NoError(t, err)

	c.Ac.Quit()
	s.Ac.Quit()
}

func TestQuitting(t *testing.T) {
	randomPortSettings := CreateRandomPortSettings()

	c, err := CreateCoordinatorServer("localhost", &coordinatorDummy{}, randomPortSettings)
	assert.NoError(t, err)

	s, err := CreateSideCarServer(randomPortSettings, &sideCarDummy{})

	c.RunAsync()
	s.RunAsync()

	err = c.ClaimSideCar("node1", fmt.Sprintf("localhost:%d", s.Port()))
	assert.NoError(t, err)

	c.QuitSideCars()
	_, err = c.GetSideCar("node1")
	assert.Error(t, err) // node1 is not existant anymore

	c.Ac.Quit()
	s.Ac.Quit()
	s.Ac.WaitGoroutines()
	assert.Equal(t, SideCarDone, s.state)
}

func TestMultipleClients(t *testing.T) {
	randomPortSettings := CreateRandomPortSettings()

	c, err := CreateCoordinatorServer("localhost", &coordinatorDummy{}, randomPortSettings)
	assert.NoError(t, err)

	s, err := CreateSideCarServer(randomPortSettings, &sideCarDummy{})
	s2, err := CreateSideCarServer(randomPortSettings, &sideCarDummy{})

	c.RunAsync()
	s.RunAsync()
	s2.RunAsync()

	err = c.ClaimSideCarWithPort("node1", "localhost", s.Port())
	assert.NoError(t, err)

	err = c.ClaimSideCarWithPort("node2", "localhost", s2.Port())
	assert.NoError(t, err)

	c.Ac.Quit()
	s.Ac.Quit()
	s2.Ac.Quit()
}

func TestDoubleAddFailure(t *testing.T) {
	randomPortSettings := CreateRandomPortSettings()

	c, err := CreateCoordinatorServer("localhost", &coordinatorDummy{}, randomPortSettings)
	assert.NoError(t, err)

	c2, err := CreateCoordinatorServer("localhost", &coordinatorDummy{}, randomPortSettings)
	assert.NoError(t, err)

	s, err := CreateSideCarServer(randomPortSettings, &sideCarDummy{})

	c.RunAsync()
	c2.RunAsync()
	s.RunAsync()

	err = c.ClaimSideCarWithPort("node1", "localhost", s.Port())
	assert.NoError(t, err)

	// This must fail, the node is already attached to the first coordinator
	err = c2.ClaimSideCarWithPort("node1", "localhost", s.Port())
	assert.Error(t, err)

	c.Ac.Quit()
	c2.Ac.Quit()
	s.Ac.Quit()
}

func TestProtocolCalls(t *testing.T) {
	randomPortSettings := CreateRandomPortSettings()

	c, err := CreateCoordinatorServer("localhost", &coordinatorDummy{}, randomPortSettings)
	assert.NoError(t, err)

	var sideCarMock sideCarDummy

	s, err := CreateSideCarServer(randomPortSettings, &sideCarMock)

	c.RunAsync()
	s.RunAsync()

	err = c.ClaimSideCarWithPort("node1", "localhost", s.Port())
	assert.NoError(t, err)

	representation, err := c.GetSideCar("node1")
	assert.NoError(t, err)

	// Calling coordinator --> Service
	{
		var req RequestStream
		var resp RequestStreamResponse
		err = representation.RequestStream(&req, &resp)
		assert.NoError(t, err)
		assert.Equal(t, 100, resp.Port)
		assert.True(t, sideCarMock.requestStreamCalled)
	}

	{
		var req RequestTransformation
		var resp RequestTransformationResponse
		err = representation.RequestTransformation(&req, &resp)
		assert.NoError(t, err)
		assert.Equal(t, 101, resp.Port)
		assert.True(t, sideCarMock.RequestTransformationCalled)
	}

	{
		var req RequestTransfer
		var resp RequestTransferResponse
		err = representation.RequestTransfer(&req, &resp)
		assert.NoError(t, err)
		assert.True(t, sideCarMock.requestTransferCalled)
	}

	c.Ac.Quit()
	s.Ac.Quit()
}

func TestCallingCoordinator(t *testing.T) {
	randomPortSettings := CreateRandomPortSettings()

	var coordinatorMock coordinatorDummy

	c, err := CreateCoordinatorServer("localhost", &coordinatorMock, randomPortSettings)
	assert.NoError(t, err)

	s, err := CreateSideCarServer(randomPortSettings, &sideCarDummy{})

	c.RunAsync()
	s.RunAsync()

	err = c.ClaimSideCarWithPort("node1", "localhost", s.Port())
	assert.NoError(t, err)

	representation, err := s.Coordinator()
	assert.NoError(t, err)
	var req StatusUpdate
	var res StatusUpdateResponse
	err = representation.StatusUpdate(&req, &res)
	assert.NoError(t, err)
	assert.True(t, coordinatorMock.statusUpdateCalled)
	assert.Equal(t, req.Name, "node1")     // the name is updated
	assert.True(t, len(req.Challenge) > 0) // the challenge is inserted

	c.Ac.Quit()
	s.Ac.Quit()
}
