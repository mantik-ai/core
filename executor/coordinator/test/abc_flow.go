package test

import (
	"coordinator/service/coordinator"
	"coordinator/service/protocol"
	"coordinator/service/sidecar"
	"coordinator/testutil"
	"fmt"
	"sync"
)

// Test setup with Source, Transformer, Sink
type ABCFlowNodes struct {
	sourceServer    *testutil.SampleServer
	transformServer *testutil.SampleServer
	sinkServer      *testutil.SampleServer

	sourceSideCar    *sidecar.SideCar
	transformSideCar *sidecar.SideCar
	sinkSideCar      *sidecar.SideCar

	group sync.WaitGroup
	plan  coordinator.Plan
}

func CreateAbcFlowNodes(testData []byte) *ABCFlowNodes {
	randomPortSettings := protocol.CreateRandomPortSettings()

	var r ABCFlowNodes

	r.sourceServer = testutil.CreateSampleSource("Source", testData)
	r.transformServer = testutil.CreateSampleTransformation("Transformer")
	r.sinkServer = testutil.CreateSampleSink("Sink")

	var err error
	r.sourceSideCar, err = sidecar.CreateSideCar(randomPortSettings, r.sourceServer.URL, false)
	if err != nil {
		panic(err)
	}

	r.transformSideCar, err = sidecar.CreateSideCar(randomPortSettings, r.transformServer.URL, false)
	if err != nil {
		panic(err)
	}

	r.sinkSideCar, err = sidecar.CreateSideCar(randomPortSettings, r.sinkServer.URL, false)
	if err != nil {
		panic(err)
	}

	r.plan = coordinator.Plan{
		Nodes: map[string]coordinator.Node{
			"A": {fmt.Sprintf("localhost:%d", r.sourceSideCar.Port())},
			"B": {fmt.Sprintf("localhost:%d", r.transformSideCar.Port())},
			"C": {fmt.Sprintf("localhost:%d", r.sinkSideCar.Port())},
		},
		Flows: []coordinator.Flow{
			coordinator.Flow{
				coordinator.NodeResourceRef{"A", "Source", nil},
				coordinator.NodeResourceRef{"B", "Transformer", nil},
				coordinator.NodeResourceRef{"C", "Sink", nil},
			},
		},
	}

	return &r
}

func (n *ABCFlowNodes) runSideCars() {
	n.group.Add(3)
	go func() {
		defer n.group.Done()
		n.sourceSideCar.Run()
	}()
	go func() {
		defer n.group.Done()
		n.transformSideCar.Run()
	}()
	go func() {
		defer n.group.Done()
		n.sinkSideCar.Run()
	}()
}

func (n *ABCFlowNodes) waitUntilSideCarEnd() {
	n.group.Wait()
}
