package coordinator

import (
	"context"
	"coordinator/service/protocol"
	"coordinator/service/sidecar"
	"fmt"
	"github.com/pkg/errors"
	log "github.com/sirupsen/logrus"
	"net/url"
	"strings"
	"sync"
)

type FlowNodeState struct {
	// Debug, where the data is coming from (may be empty)
	input string
	// URL providing the stream
	// Can be tcp://[host:port] or http://
	url      string
	isAction bool
	// runtime
	in              int64
	out             int64
	done            bool
	err             error
	countAsFinished bool // if true, we see this node as being finished (either error or done)
}

type FlowState struct {
	nodeStates []*FlowNodeState
}

type Coordinator struct {
	server *protocol.CoordinatorServer
	plan   *Plan

	// Protecting flow state and pending actions
	mux            sync.Mutex
	flowStates     []*FlowState
	pendingActions int // actions (sinks) which wait to be completed.

	// Embedded sidecar, for flows consisting only of external nodes
	embeddedSideCar *sidecar.PlainSidecar
}

func CreateCoordinator(coordinatorAddress string, settings *protocol.Settings, plan *Plan) (*Coordinator, error) {
	log.Printf("Initializing Coordinator")
	var coordinator Coordinator
	coordinator.plan = plan
	server, err := protocol.CreateCoordinatorServer(coordinatorAddress, &coordinator, settings)
	if err != nil {
		return nil, errors.Wrapf(err, "Could not create coordinator server")
	}
	coordinator.server = server
	coordinator.logPlan()
	coordinator.embeddedSideCar = sidecar.CreateHeadlessSideCar(settings, &coordinator)
	return &coordinator, nil
}

func (c *Coordinator) Address() string {
	return c.server.CoordinatorAddress
}

// Dumps relevant parts of the plan to the log.
func (c *Coordinator) logPlan() {
	log.Printf("Node Count: %d", len(c.plan.Nodes))
	for name, n := range c.plan.Nodes {
		log.Printf("  - %s : %s", name, n.Str())
	}
	log.Printf("Flow Count: %d", len(c.plan.Flows))
	for id, flow := range c.plan.Flows {
		log.Printf("  Flow %d (len=%d)", id, len(flow))
		for subId, ref := range flow {
			log.Printf("  - %d (node %s -> %s (%s))", subId, ref.Node, ref.Resource, optionalString(ref.ContentType))
		}
	}
}

func optionalString(s *string) string {
	if s == nil {
		return "<nil>"
	} else {
		return *s
	}
}

func (c *Coordinator) Run() error {
	c.server.RunAsync()

	defer c.server.QuitSideCars()
	log.Info("Initializing Connections...")
	err := c.initializeConnections()
	if err != nil {
		return c.server.Ac.CombineErr(errors.Wrap(err, "Could not initialize connections"))
	}
	log.Info("Initializing Flows...")
	err = c.initializeFlows()
	if err != nil {
		return c.server.Ac.CombineErr(errors.Wrap(err, "Could not initialize flows"))
	}
	log.Infof("Wait until done, pending actions=%d...", c.pendingActions)
	err = c.waitUntilDone()
	if err != nil {
		if err == context.Canceled {
			return c.server.Ac.Result()
		}
		return c.server.Ac.CombineErr(errors.Wrap(err, "Error upon waiting until finish"))
	}
	finalStatus := c.server.Ac.CombineErr(c.finalStatus())
	log.Info("Final Status ", finalStatus)
	// do not care for the result, as we are interested in the stream result
	return finalStatus
}

func (c *Coordinator) initializeConnections() error {
	// Initializing all Nodes in parallel
	p := c.server.Ac.Parallel()

	sideCarNodeCount := 0
	for nodeName, node := range c.plan.Nodes {
		if !node.IsSideCarNode() {
			continue
		}
		sideCarNodeCount += 1

		func(nodeName string, addr string) {

			p.Add(func() error {
				err := c.server.ClaimSideCar(nodeName, addr)
				if err != context.Canceled {
					err = errors.Wrapf(err, "Could not claim node")
				}
				return err
			})

		}(nodeName, *node.Address)
	}
	err := p.Result()

	if err != nil {
		return err
	}

	log.Infof("Connected to %d SideCar Nodes (of %d Nodes)", sideCarNodeCount, len(c.plan.Nodes))
	return nil
}

func (c *Coordinator) initializeFlows() error {
	for id, flow := range c.plan.Flows {
		if len(flow) == 0 {
			return errors.New("Empty flow")
		}
		if len(flow) == 1 {
			return errors.New("Single node flow")
		}
		flowState, err := c.initializeFlow(id, flow)
		if err != nil {
			return err
		}
		c.flowStates = append(c.flowStates, flowState)
	}
	return nil
}

func (c *Coordinator) initializeFlow(flowId int, flow Flow) (*FlowState, error) {
	c.mux.Lock()
	defer c.mux.Unlock()

	var flowState FlowState
	var state *FlowNodeState
	var lastExistingNode *string
	var responsibleSidecar protocol.SideCarService = c.embeddedSideCar
	var responsibleSidecarAddress = c.Address()
	for subId, nodeRef := range flow {
		var err error
		encodedFlowSubId := encodeFlowIdSubId(flowId, subId)
		node, exists := c.plan.Nodes[nodeRef.Node]
		if !exists {
			return nil, errors.Errorf("Could not resolve node %s", nodeRef.Node)
		}
		if node.IsSideCarNode() {
			responsibleSidecar, err = c.server.GetSideCar(nodeRef.Node)
			if err != nil {
				return nil, errors.Wrapf(err, "Could not find sidecar %s", nodeRef.Node)
			}
			responsibleSidecarAddress = *node.Address
		}

		if subId == 0 {
			// First node (Source)
			state, err = c.initializeSource(&node, responsibleSidecar, encodedFlowSubId, nodeRef)
			if err != nil {
				return nil, errors.Wrapf(err, "Could not initialize source %s", nodeRef.Str())
			}
		} else if subId == len(flow)-1 {
			// Last Node (Sink)
			state, err = c.initializeSink(&node, responsibleSidecar, encodedFlowSubId, state, lastExistingNode, nodeRef)
			if err != nil {
				return nil, errors.Wrapf(err, "Could not initialize sink %s", nodeRef.Str())
			}
		} else {
			state, err = c.initializeTransformation(&node, responsibleSidecarAddress, responsibleSidecar, encodedFlowSubId, state, lastExistingNode, nodeRef)
			if err != nil {
				return nil, errors.Wrapf(err, "Could not initialize transformation %s", nodeRef.Str())
			}
		}
		if state.isAction {
			c.pendingActions++
		}
		flowState.nodeStates = append(flowState.nodeStates, state)
		if node.IsSideCarNode() {
			foo := nodeRef.Node
			lastExistingNode = &foo
		}
	}
	return &flowState, nil
}

func encodeFlowIdSubId(flowId int, subId int) string {
	return fmt.Sprintf("%d:%d", flowId, subId)
}

func decodeFlowIdSubId(id string) (flowId int, subId int) {
	_, err := fmt.Sscanf(id, "%d:%d", &flowId, &subId)
	if err != nil {
		log.Errorf("Got an illegal encoded flow id %s", id)
		flowId = -1
		subId = -1
	}
	return
}

func formatTcpStreamUrl(sideCarAddress string, port int) string {
	idx := strings.LastIndex(sideCarAddress, ":")
	var host string
	if idx < 0 {
		host = sideCarAddress
	} else {
		host = sideCarAddress[:idx]
	}
	return fmt.Sprintf("tcp://%s:%d", host, port)
}

func (c *Coordinator) initializeSource(node *Node, sideCar protocol.SideCarService, encodedFlowSubId string, ref NodeResourceRef) (*FlowNodeState, error) {
	if node.IsExternalNode() {
		// do not go via sidecar, let the next one load it.
		fullUrl, err := resolveUrl(*node.Url, ref.Resource)
		if err != nil {
			return nil, err
		}
		return &FlowNodeState{
			url: fullUrl,
		}, nil
	}

	var resp protocol.RequestStreamResponse
	req := protocol.RequestStream{
		Id:          encodedFlowSubId,
		Resource:    ref.Resource,
		ContentType: ref.ContentType,
	}
	err := sideCar.RequestStream(&req, &resp)
	if err = handleCommunicationErrors(ref.Node, err, resp.ResponseBase); err != nil {
		return nil, err
	}
	return &FlowNodeState{
		url: formatTcpStreamUrl(*node.Address, resp.Port),
	}, nil
}

func resolveUrl(base string, resource string) (string, error) {
	root, err := url.Parse(base)
	if err != nil {
		return "", err
	}
	sub, err := url.Parse(resource)
	if err != nil {
		return "", err
	}
	resolved := root.ResolveReference(sub)
	return resolved.String(), nil
}

func (c *Coordinator) initializeSink(node *Node, sideCar protocol.SideCarService, encodedFlowSubId string, lastNode *FlowNodeState, lastExistingNode *string, ref NodeResourceRef) (*FlowNodeState, error) {
	var err error
	var destination string

	if node.IsExternalNode() {
		destination, err = resolveUrl(*node.Url, ref.Resource)
	} else {
		destination = ref.Resource
	}

	if err != nil {
		return nil, err
	}

	var resp protocol.RequestTransferResponse

	req := protocol.RequestTransfer{
		Destination: destination,
		Source:      lastNode.url,
		Id:          encodedFlowSubId,
		ContentType: ref.ContentType,
	}

	err = sideCar.RequestTransfer(&req, &resp)
	if err = handleCommunicationErrors(ref.Node, err, resp.ResponseBase); err != nil {
		return nil, err
	}
	return &FlowNodeState{
		input:    lastNode.url,
		isAction: true,
	}, nil
}

func (c *Coordinator) initializeTransformation(node *Node, sideCarAddress string, sideCar protocol.SideCarService, encodedFlowSubId string, lastNode *FlowNodeState, lastExistingNode *string, ref NodeResourceRef) (*FlowNodeState, error) {
	var err error
	var destination string

	if node.IsExternalNode() {
		destination, err = resolveUrl(*node.Url, ref.Resource)
	} else {
		destination = ref.Resource
	}

	if err != nil {
		return nil, err
	}

	var resp protocol.RequestTransformationResponse
	req := protocol.RequestTransformation{
		Destination: destination,
		Source:      lastNode.url,
		Id:          encodedFlowSubId,
		ContentType: ref.ContentType,
	}
	err = sideCar.RequestTransformation(&req, &resp)
	if err = handleCommunicationErrors(ref.Node, err, resp.ResponseBase); err != nil {
		return nil, err
	}
	return &FlowNodeState{
		input:    lastNode.url,
		url:      formatTcpStreamUrl(sideCarAddress, resp.Port),
		isAction: false,
	}, nil
}

func handleCommunicationErrors(nodeName string, rpcCallResult error, response protocol.ResponseBase) error {
	if rpcCallResult != nil {
		log.Warnf("Communication error with side car %s: %s", nodeName, rpcCallResult.Error())
		return rpcCallResult
	}
	if response.IsError() {
		log.Warnf("Sidecar %s reported error %s", nodeName, response.Error)
		return response.Err()
	}
	return nil
}

func (c *Coordinator) StatusUpdate(req *protocol.StatusUpdate, res *protocol.StatusUpdateResponse) error {
	flowId, subId := decodeFlowIdSubId(req.Id)
	c.mux.Lock()
	defer c.mux.Unlock()
	if flowId < 0 || flowId >= len(c.flowStates) {
		log.Warnf("Got status update for not existing flow %d", flowId)
		return nil
	}
	flow := c.flowStates[flowId]
	if subId < 0 || subId >= len(flow.nodeStates) {
		log.Warnf("Got status update for not existing flow sub id %d:%d", flowId, subId)
		return nil
	}
	nodeState := flow.nodeStates[subId]
	countAsFinishedBefore := nodeState.countAsFinished
	nodeState.in = req.Ingress
	nodeState.out = req.Outgress
	if req.Done {
		nodeState.countAsFinished = true
		nodeState.done = true
	}
	if len(req.Error) > 0 {
		nodeState.err = errors.New(req.Error)
		nodeState.countAsFinished = true
		log.Infof("Node %d:%d failed, %s", flowId, subId, req.Error)
	}
	if nodeState.countAsFinished && !countAsFinishedBefore {
		log.Infof("Node %d:%d is done (isAction=%t, err=%s)", flowId, subId, nodeState.isAction, req.Error)
		if nodeState.isAction {
			c.pendingActions--
		}
	}
	return nil
}

func (c *Coordinator) waitUntilDone() error {
	return c.server.Ac.BlockUntil(c.server.Settings.JobExecutionTimeout, func() bool {
		c.mux.Lock()
		defer c.mux.Unlock()
		return c.pendingActions <= 0
	})
}

func (c *Coordinator) finalStatus() error {
	c.mux.Lock()
	defer c.mux.Unlock()
	for flowId, flow := range c.flowStates {
		for nodeId, node := range flow.nodeStates {
			if node.isAction && node.err != nil {
				return errors.Wrapf(node.err, "Failed action node %d:%d, %s", flowId, nodeId, node.err.Error())
			}
			if node.isAction && !node.done {
				return errors.Errorf("Unfinished node %d:%d", flowId, nodeId)
			}
		}
	}
	return nil
}
