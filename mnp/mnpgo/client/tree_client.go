package client

import (
	"context"
	"github.com/golang/protobuf/ptypes/any"
	"github.com/pkg/errors"
	"github.com/sirupsen/logrus"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/client/internal"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/protos/mantik/mnp"
	"golang.org/x/sync/errgroup"
	"sync"
)

/* A Client which can talk to multiple nodes and leverage data forwarding. */
type TreeClient struct {
	ctx           context.Context
	nodeAddresses map[string]string
}

func NewTreeClient(ctx context.Context) *TreeClient {
	return &TreeClient{
		ctx:           ctx,
		nodeAddresses: map[string]string{},
	}
}

/* Add a node to the tree. Will not yet connect to it, but just add idd */
func (t *TreeClient) AddNode(name string, address string) error {
	_, existing := t.nodeAddresses[name]
	if existing {
		return errors.New("Exists already")
	}
	t.nodeAddresses[name] = address
	return nil
}

/* Connect to all nodes in the tree. */
func (t *TreeClient) Connect() (*ConnectedTreeClient, error) {
	eg, childContext := errgroup.WithContext(t.ctx)
	m := sync.Mutex{}
	connections := map[string]*internal.SingleNodeClient{}

	for name, addr := range t.nodeAddresses {
		func(name string, addr string) {
			eg.Go(func() error {
				subClient, err := internal.ConnectSingleNode(childContext, addr)
				if err != nil {
					logrus.Warnf("Could not connect %s/%s: %s", name, addr, err.Error())
				} else {
					m.Lock()
					defer m.Unlock()
					connections[name] = subClient
				}
				return err
			})
		}(name, addr)
	}

	err := eg.Wait()
	if err != nil {
		// Closing other connections, a connection has not worked
		for _, c := range connections {

			c.Close()
		}
		return nil, err
	}
	return &ConnectedTreeClient{connections: connections}, nil
}

/* The tree client, connected to multiple nodes. */
type ConnectedTreeClient struct {
	connections map[string]*internal.SingleNodeClient
}

/* Prepare a session. */
func (c *ConnectedTreeClient) PrepareSession(sessionId string) (*PrepareSessionClient, error) {
	if len(sessionId) == 0 {
		return nil, errors.New("Invalid session id")
	}
	return &PrepareSessionClient{
		sessionId:   sessionId,
		initInfos:   map[string]*initInfo{},
		inputs:      map[portNode]*inputInfo{},
		outputs:     map[portNode]*outputInfo{},
		mainInputs:  []portNode{},
		mainOutputs: []portNode{},
		c:           c,
	}, nil
}

/* A Client for preparing a session. */
type PrepareSessionClient struct {
	sessionId   string
	initInfos   map[string]*initInfo
	inputs      map[portNode]*inputInfo
	outputs     map[portNode]*outputInfo
	mainInputs  []portNode
	mainOutputs []portNode
	c           *ConnectedTreeClient
}

type initInfo struct {
	config *any.Any
	ports  mnpgo.PortConfiguration
}

type portNode struct {
	node string
	port int
}

// Info about an output nde
type outputInfo struct {
	forwarding   *portNode
	mainOutputId int
}

type inputInfo struct {
	forwardedFrom *portNode
	mainInputId   int
}

/* Add an init configuration. Ports count will be taken from that. */
func (p *PrepareSessionClient) AddInit(node string, conf *any.Any, ports mnpgo.PortConfiguration) error {
	info := initInfo{
		config: conf,
		ports:  ports,
	}
	_, existing := p.initInfos[node]
	if existing {
		return errors.New("Init already set for that node")
	}
	_, nodeExists := p.c.connections[node]
	if !nodeExists {
		return errors.New("Node does not exist")
	}
	p.initInfos[node] = &info
	return nil
}

/* Add a port forwarding. This will remove one input and one output from the network. */
func (p *PrepareSessionClient) AddForwarding(from string, fromOutPort int, to string, toInPort int) error {
	source, err := p.resolveOutput(from, fromOutPort)
	if err != nil {
		return err
	}
	target, err := p.resolveInput(to, toInPort)
	if err != nil {
		return err
	}
	if source.forwarding != nil {
		return errors.New("There is already a port forwarding")
	}
	source.forwarding = &portNode{
		node: to,
		port: toInPort,
	}
	target.forwardedFrom = &portNode{
		node: from,
		port: fromOutPort,
	}
	return nil
}

func (p *PrepareSessionClient) resolveInput(node string, port int) (*inputInfo, error) {
	nodePort, err := p.resolveNodePort("in", true, node, port)
	if err != nil {
		return nil, err
	}
	entry, existing := p.inputs[nodePort]
	if existing {
		return entry, nil
	}
	newEntry := &inputInfo{
		mainInputId: -1,
	}
	p.inputs[nodePort] = newEntry
	return newEntry, nil
}

func (p *PrepareSessionClient) resolveOutput(node string, port int) (*outputInfo, error) {
	nodePort, err := p.resolveNodePort("out", false, node, port)
	if err != nil {
		return nil, err
	}
	entry, existing := p.outputs[nodePort]
	if existing {
		return entry, nil
	}
	newEntry := &outputInfo{
		mainOutputId: -1,
	}
	p.outputs[nodePort] = newEntry
	return newEntry, nil
}

func (p *PrepareSessionClient) resolveNodePort(role string, in bool, node string, port int) (portNode, error) {
	fromInfo, exists := p.initInfos[node]
	if !exists {
		return portNode{}, errors.Errorf("%s Node %s doesn't exist", role, node)
	}
	var portCount int
	if in {
		portCount = len(fromInfo.ports.Inputs)
	} else {
		portCount = len(fromInfo.ports.Outputs)
	}
	if port < 0 || port >= portCount {
		return portNode{}, errors.Errorf("Port %d not found in node %s (role=%s)", port, node, role)
	}
	return portNode{
		node,
		port,
	}, nil
}

/* Register a combined input port number, is always increasing and starting from 0. */
func (p *PrepareSessionClient) MainInput(node string, inPort int) (int, error) {
	input, err := p.resolveInput(node, inPort)
	if err != nil {
		return 0, err
	}
	if input.forwardedFrom != nil {
		return 0, errors.New("Node is already feeded by input data")
	}
	if input.mainInputId >= 0 {
		return 0, errors.New("Node already has an input id associated")
	}
	id := len(p.mainInputs)
	np := portNode{
		node: node,
		port: inPort,
	}
	p.mainInputs = append(p.mainInputs, np)
	input.mainInputId = id
	return id, nil
}

/* Register a combiend ouput port number, is always incresing and starting from 0. */
func (p *PrepareSessionClient) MainOutput(node string, outPort int) (int, error) {
	output, err := p.resolveOutput(node, outPort)
	if err != nil {
		return 0, err
	}
	if output.forwarding != nil {
		return 0, errors.New("Node is already forwarding data")
	}
	if output.mainOutputId >= 0 {
		return 0, errors.New("Node already has an output id associated")
	}
	id := len(p.mainOutputs)
	np := portNode{
		node: node,
		port: outPort,
	}
	p.mainOutputs = append(p.mainOutputs, np)
	output.mainOutputId = id
	return id, nil
}

/* Execute init steps. Configuration must be present. */
func (p *PrepareSessionClient) Initialize() (*TreeClientSession, error) {
	if len(p.mainOutputs) == 0 {
		return nil, errors.New("No registered outputs")
	}

	// TODO: Check for unconnected nodes ?

	initRequests := map[string](*mnp.InitRequest){}
	for nodeName, _ := range p.c.connections {
		initRequest, err := p.prepareInitRequest(nodeName)
		if err != nil {
			return nil, errors.Wrapf(err, "Could not create init for %s", nodeName)
		}
		initRequests[nodeName] = initRequest
	}

	eg, childContext := errgroup.WithContext(context.Background()) /// TODO: Context?
	mutex := sync.Mutex{}
	initialized := []string{}
	for nodeName, connection := range p.c.connections {
		newNodeName := nodeName
		newConnection := connection
		eg.Go(func() error {
			err := newConnection.Init(childContext, initRequests[newNodeName], nil)
			mutex.Lock()
			defer mutex.Unlock()
			if err != nil {
				initialized = append(initialized, nodeName)
			}
			return err
		})
	}
	err := eg.Wait()
	if err != nil {
		logrus.Warn("Init failed, cleaning up, asynchronously...")
		wg := sync.WaitGroup{}
		for _, nodeName := range initialized {
			newNodeName := nodeName
			con := p.c.connections[newNodeName]
			wg.Add(1)
			go func() {
				defer wg.Done()
				err := con.QuitSession(context.Background(), p.sessionId)
				if err != nil {
					logrus.Warnf("Could not quit session on node %s, %s", newNodeName, err.Error())
				}
			}()
		}
		wg.Wait()
		return nil, err
	}

	return &TreeClientSession{
		sessionId:   p.sessionId,
		mainInputs:  p.mainInputs,
		mainOutputs: p.mainOutputs,
		c:           p.c,
	}, nil
}

// Prepare the init request, not thread safe
func (p *PrepareSessionClient) prepareInitRequest(node string) (*mnp.InitRequest, error) {
	initInfo := p.initInfos[node]
	initReq := mnp.InitRequest{
		SessionId:     p.sessionId,
		Configuration: initInfo.config,
	}
	for i, input := range initInfo.ports.Inputs {
		inputInfo, err := p.resolveInput(node, i)
		if err != nil {
			return nil, err
		}
		if inputInfo.mainInputId < 0 && inputInfo.forwardedFrom == nil {
			// TODO: Support empty input
			return nil, errors.New("Input without main id not yet supported")
		}
		configured := mnp.ConfigureInputPort{
			ContentType: input.ContentType,
		}
		initReq.Inputs = append(initReq.Inputs, &configured)
	}
	for i, output := range initInfo.ports.Outputs {
		outputInfo, err := p.resolveOutput(node, i)
		if err != nil {
			return nil, err
		}
		if outputInfo.mainOutputId < 0 && outputInfo.forwarding == nil {
			// TODO: Support ignored output
			return nil, errors.New("Output without main id and no forwarding not yet supported")
		}
		configured := mnp.ConfigureOutputPort{
			ContentType: output.ContentType,
		}
		if outputInfo.forwarding != nil {
			url := mnpgo.MnpUrl{
				Address:   p.c.connections[outputInfo.forwarding.node].Address(),
				SessionId: p.sessionId,
				Port:      outputInfo.forwarding.port,
			}
			configured.DestinationUrl = url.String()
		}
		initReq.Outputs = append(initReq.Outputs, &configured)
	}
	return &initReq, nil
}
