package test

import (
	"coordinator/service/coordinator"
	"coordinator/service/protocol"
	"coordinator/service/sidecar"
	"coordinator/testutil"
	"fmt"
	"github.com/stretchr/testify/assert"
	"net/rpc/jsonrpc"
	"sync"
	"testing"
	"time"
)

func TestUnreachableNode(t *testing.T) {
	var shortTimeoutSettings = protocol.CreateRandomPortSettings()
	shortTimeoutSettings.RetryTime = time.Millisecond * 10
	shortTimeoutSettings.ConnectTimeout = time.Millisecond * 100

	ts2 := testutil.CreateSampleSink("Sink")
	defer ts2.Close()

	// Sidecar 1 doesn't exist.
	sideCar1FakePort := testutil.GetFreeTcpListeningPort()

	sideCar2, err := sidecar.CreateSideCar(shortTimeoutSettings, ts2.URL, false)
	assert.NoError(t, err)

	plan := coordinator.Plan{
		Nodes: map[string]coordinator.Node{
			"A": {fmt.Sprintf("localhost:%d", sideCar1FakePort)},
			"B": {fmt.Sprintf("localhost:%d", sideCar2.Port())},
		},
		Flows: []coordinator.Flow{
			coordinator.Flow{
				coordinator.NodeResourceRef{"A", "Source"},
				coordinator.NodeResourceRef{"B", "Sink"},
			},
		},
	}

	c, err := coordinator.CreateCoordinator("localhost", shortTimeoutSettings, &plan)
	assert.NoError(t, err)

	var group sync.WaitGroup
	// SideCars are stopped by the coordinator
	group.Add(1)
	go func() {
		defer group.Done()
		sideCar2.Run()
	}()
	err = c.Run()
	assert.Error(t, err)
	group.Wait()
}

func TestOrphanedSideCar(t *testing.T) {
	var shortTimeoutSettings = protocol.CreateRandomPortSettings()
	shortTimeoutSettings.WaitForCoordinatorTimeout = time.Millisecond * 100

	httpBackend := testutil.CreateSampleSink("foo")
	defer httpBackend.Close()
	sideCar, err := sidecar.CreateSideCar(shortTimeoutSettings, httpBackend.URL, false)
	assert.NoError(t, err)
	t0 := time.Now()
	err = sideCar.Run()
	t1 := time.Now()
	assert.Error(t, err)
	dif := t1.Sub(t0)
	assert.True(t, dif < time.Millisecond*150)
	assert.True(t, dif > time.Millisecond*75)
}

func TestUnrachableWebService(t *testing.T) {
	var shortTimeoutSettings = protocol.CreateRandomPortSettings()
	shortTimeoutSettings.WaitForWebServiceReachable = time.Millisecond * 100
	shortTimeoutSettings.RetryTime = time.Millisecond * 10

	sideCar, err := sidecar.CreateSideCar(shortTimeoutSettings, "http://localhost/not-reachable", false)
	assert.NoError(t, err)
	t0 := time.Now()
	err = sideCar.Run()
	t1 := time.Now()
	assert.Error(t, err)
	dif := t1.Sub(t0)
	assert.True(t, dif < time.Millisecond*150)
	assert.True(t, dif > time.Millisecond*75)
}

func TestDisabledUnreachableWebServiceWaiting(t *testing.T) {
	var disabledWaitings = protocol.CreateRandomPortSettings()
	disabledWaitings.WaitForWebServiceReachable = 0
	disabledWaitings.WaitForCoordinatorTimeout = time.Millisecond * 100

	sideCar, err := sidecar.CreateSideCar(disabledWaitings, "http://localhost/not-reachable", false)
	assert.NoError(t, err)

	// It will instead fail for a missing coordinator...

	t0 := time.Now()
	err = sideCar.Run()
	t1 := time.Now()
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "coordinator")
	dif := t1.Sub(t0)
	assert.True(t, dif < time.Millisecond*150)
	assert.True(t, dif > time.Millisecond*75)
}

func TestAllNodesUnreachable(t *testing.T) {
	shortTimeouts := protocol.CreateRandomPortSettings()
	shortTimeouts.ConnectTimeout = time.Millisecond * 100
	shortTimeouts.RetryTime = time.Millisecond * 10

	abc := CreateAbcFlowNodes([]byte("nothing"))
	c, err := coordinator.CreateCoordinator("localhost", shortTimeouts, &abc.plan)
	assert.NoError(t, err)

	// Directly close them all, so that they do not accept RPC Calls..
	abc.sinkSideCar.ForceQuit()
	abc.sourceSideCar.ForceQuit()
	abc.transformSideCar.ForceQuit()

	// Side cars not started, usually it would wait some time...
	abc.group.Add(1)
	var result error
	go func() {
		defer abc.group.Done()
		result = c.Run()
	}()

	abc.group.Wait()
	assert.Error(t, result)
}

func TestServerCancellation(t *testing.T) {
	randomPortSettings := protocol.CreateRandomPortSettings()

	abc := CreateAbcFlowNodes([]byte("nothing"))
	c, err := coordinator.CreateCoordinator("localhost", randomPortSettings, &abc.plan)
	assert.NoError(t, err)

	// Directly close them all, so that they do not accept RPC Calls..
	abc.sinkSideCar.ForceQuit()
	abc.sourceSideCar.ForceQuit()
	abc.transformSideCar.ForceQuit()

	// Side cars not started, usually it would wait some time...
	abc.group.Add(1)
	var result error
	go func() {
		defer abc.group.Done()
		result = c.Run()
	}()

	t0 := time.Now()
	time.AfterFunc(time.Millisecond*10, func() {
		client, err := jsonrpc.Dial("tcp", c.Address())
		assert.NoError(t, err)
		req := protocol.CancelRequest{}
		var res protocol.CancelResponse
		err = client.Call("Generic.Cancel", &req, &res)
		assert.NoError(t, err)
	})

	abc.group.Wait()
	t1 := time.Now()
	assert.Error(t, result)
	assert.WithinDuration(t, t1, t0, 50*time.Millisecond)
}

func TestSideCarCancellation(t *testing.T) {
	// Note: this is a pending error
	randomPortSettings := protocol.CreateRandomPortSettings()
	s, err := sidecar.CreateSideCar(randomPortSettings, "http://localhost/not_existing", false)
	assert.NoError(t, err)
	var wg sync.WaitGroup
	wg.Add(1)
	var result error
	go func() {
		defer wg.Done()
		result = s.Run()
	}()

	address := fmt.Sprintf("localhost:%d", s.Port())

	t0 := time.Now()
	time.AfterFunc(time.Millisecond*10, func() {
		client, err := jsonrpc.Dial("tcp", address)
		assert.NoError(t, err)
		req := protocol.CancelRequest{}
		var res protocol.CancelResponse
		err = client.Call("Generic.Cancel", &req, &res)
		assert.NoError(t, err)
	})

	wg.Wait()
	t1 := time.Now()
	assert.WithinDuration(t, t1, t0, 50*time.Millisecond)
	assert.Error(t, result)
}
