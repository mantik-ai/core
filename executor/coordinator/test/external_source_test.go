package test

import (
	"coordinator/service/coordinator"
	"coordinator/service/sidecar"
	"coordinator/testutil"
	"github.com/stretchr/testify/assert"
	"sync"
	"testing"
)

func TestSimpleExternalABCopy(t *testing.T) {
	// Copy External --> Node

	testData := []byte("Hello World")

	ts1 := testutil.CreateSampleSource("Source", testData)
	defer ts1.Close()

	ts2 := testutil.CreateSampleSink("Sink")
	defer ts2.Close()

	sideCar2, err := sidecar.CreateSideCar(randomPortSettings, ts2.URL, false)
	assert.NoError(t, err)

	plan := coordinator.Plan{
		Nodes: map[string]coordinator.Node{
			"A": coordinator.MakeExternalNode(ts1.URL),
			"B": coordinator.MakeAddressNode("localhost", sideCar2.Port()),
		},
		Flows: []coordinator.Flow{
			coordinator.Flow{
				coordinator.NodeResourceRef{"A", "Source", nil},
				coordinator.NodeResourceRef{"B", "Sink", nil},
			},
		},
	}

	c, err := coordinator.CreateCoordinator("localhost", randomPortSettings, &plan)
	assert.NoError(t, err)

	var group sync.WaitGroup
	// SideCars are stopped by the coordinator
	group.Add(1)
	go func() {
		defer group.Done()
		sideCar2.Run()
	}()
	err = c.Run()
	group.Wait()
	assert.NoError(t, err)
	assert.Equal(t, 1, ts1.Requests)
	assert.Equal(t, 1, ts2.Requests)
	assert.Equal(t, testData, ts2.RequestData[0])
}

func TestSimpleExternalABCopy2(t *testing.T) {
	// Copy Node --> External

	testData := []byte("Hello World")

	ts1 := testutil.CreateSampleSource("Source", testData)
	defer ts1.Close()

	ts2 := testutil.CreateSampleSink("Sink")
	defer ts2.Close()

	sideCar1, err := sidecar.CreateSideCar(randomPortSettings, ts1.URL, false)
	assert.NoError(t, err)

	plan := coordinator.Plan{
		Nodes: map[string]coordinator.Node{
			"A": coordinator.MakeAddressNode("localhost", sideCar1.Port()),
			"B": coordinator.MakeExternalNode(ts2.URL),
		},
		Flows: []coordinator.Flow{
			coordinator.Flow{
				coordinator.NodeResourceRef{"A", "Source", nil},
				coordinator.NodeResourceRef{"B", "Sink", nil},
			},
		},
	}

	c, err := coordinator.CreateCoordinator("localhost", randomPortSettings, &plan)
	assert.NoError(t, err)

	var group sync.WaitGroup
	// SideCars are stopped by the coordinator
	group.Add(1)
	go func() {
		defer group.Done()
		sideCar1.Run()
	}()
	err = c.Run()
	group.Wait()
	assert.NoError(t, err)
	assert.Equal(t, 1, ts1.Requests)
	assert.Equal(t, 1, ts2.Requests)
	assert.Equal(t, testData, ts2.RequestData[0])
}

func TestExternalAbcFlow(t *testing.T) {
	// External --> Internal ---> External

	testData := []byte("Hello World")
	abc := CreateAbcFlowNodes(testData)
	abc.plan.Nodes["A"] = coordinator.MakeExternalNode(abc.sourceServer.ResourceUrl())
	abc.plan.Nodes["C"] = coordinator.MakeExternalNode(abc.sinkServer.ResourceUrl())

	c, err := coordinator.CreateCoordinator("localhost", randomPortSettings, &abc.plan)
	assert.NoError(t, err)

	abc.runSideCars()
	err = c.Run()
	assert.NoError(t, err)
	assert.Equal(t, 1, abc.sourceServer.Requests)
	assert.Equal(t, 1, abc.transformServer.Requests)
	assert.Equal(t, 1, abc.sinkServer.Requests)
	assert.Equal(t, testData, abc.sinkServer.RequestData[0])
	assert.Equal(t, testData, abc.transformServer.RequestData[0])
	// this side cars are not needed.
	abc.sourceSideCar.ForceQuit()
	abc.sinkSideCar.ForceQuit()
	abc.waitUntilSideCarEnd()
}

func TestExternalAbcFlow2(t *testing.T) {
	// Internal --> External --> Internal

	testData := []byte("Hello World")
	abc := CreateAbcFlowNodes(testData)
	abc.plan.Nodes["B"] = coordinator.MakeExternalNode(abc.transformServer.ResourceUrl())

	c, err := coordinator.CreateCoordinator("localhost", randomPortSettings, &abc.plan)
	assert.NoError(t, err)

	abc.runSideCars()
	err = c.Run()
	assert.NoError(t, err)
	assert.Equal(t, 1, abc.sourceServer.Requests)
	assert.Equal(t, 1, abc.transformServer.Requests)
	assert.Equal(t, 1, abc.sinkServer.Requests)
	assert.Equal(t, testData, abc.sinkServer.RequestData[0])
	assert.Equal(t, testData, abc.transformServer.RequestData[0])
	// this side cars are not needed.
	abc.transformSideCar.ForceQuit()
	abc.waitUntilSideCarEnd()
}

func TestExternalOnlyAbFlow(t *testing.T) {
	// External -> External
	// The coordinator will have to do the work by itself as it can't offload it to a node.

	testData := []byte("Hello World")

	ts1 := testutil.CreateSampleSource("Source", testData)
	defer ts1.Close()

	ts2 := testutil.CreateSampleSink("Sink")
	defer ts2.Close()

	plan := coordinator.Plan{
		Nodes: map[string]coordinator.Node{
			"A": coordinator.MakeExternalNode(ts1.URL),
			"B": coordinator.MakeExternalNode(ts2.URL),
		},
		Flows: []coordinator.Flow{
			coordinator.Flow{
				coordinator.NodeResourceRef{"A", "Source", nil},
				coordinator.NodeResourceRef{"B", "Sink", nil},
			},
		},
	}

	c, err := coordinator.CreateCoordinator("localhost", randomPortSettings, &plan)
	assert.NoError(t, err)

	var group sync.WaitGroup
	err = c.Run()
	group.Wait()
	assert.NoError(t, err)
	assert.Equal(t, 1, ts1.Requests)
	assert.Equal(t, 1, ts2.Requests)
	assert.Equal(t, testData, ts2.RequestData[0])
}

func TestFullExternalAbcFlow(t *testing.T) {
	// External --> External ---> External

	testData := []byte("Hello World")
	abc := CreateAbcFlowNodes(testData)
	abc.plan.Nodes["A"] = coordinator.MakeExternalNode(abc.sourceServer.ResourceUrl())
	abc.plan.Nodes["B"] = coordinator.MakeExternalNode(abc.transformServer.ResourceUrl())
	abc.plan.Nodes["C"] = coordinator.MakeExternalNode(abc.sinkServer.ResourceUrl())

	c, err := coordinator.CreateCoordinator("localhost", randomPortSettings, &abc.plan)
	assert.NoError(t, err)

	err = c.Run()
	assert.NoError(t, err)
	assert.Equal(t, 1, abc.sourceServer.Requests)
	assert.Equal(t, 1, abc.transformServer.Requests)
	assert.Equal(t, 1, abc.sinkServer.Requests)
	assert.Equal(t, testData, abc.sinkServer.RequestData[0])
	assert.Equal(t, testData, abc.transformServer.RequestData[0])
	abc.waitUntilSideCarEnd()
}
