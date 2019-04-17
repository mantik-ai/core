package coordinator

import "fmt"

type Node struct {
	// Address of the node's sidecar
	Address *string `json:"address"`
	// If address not set, URL of external service
	Url *string `json:"url"`
}

func (n *Node) Str() string {
	if n.Address != nil {
		return fmt.Sprintf("Address: %s", *n.Address)
	} else if n.Url != nil {
		return fmt.Sprintf("Url: %s", *n.Url)
	} else {
		return "Error: Address and URL not set"
	}
}

// Returns true, if this is a side car node
func (n *Node) IsSideCarNode() bool {
	return n.Address != nil
}

func (n *Node) IsExternalNode() bool {
	return n.Url != nil
}

// Creates a node from host and port
func MakeAddressNode(host string, port int) Node {
	addr := fmt.Sprintf("%s:%d", host, port)
	return Node{
		Address: &addr,
	}
}

// Creates a node for a external URL
func MakeExternalNode(url string) Node {
	return Node{
		Url: &url,
	}
}

type NodeResourceRef struct {
	Node        string  `json:"node"`
	Resource    string  `json:"resource"`
	ContentType *string `json:"contentType"`
}

func (n *NodeResourceRef) Str() string {
	return fmt.Sprintf("%s:%s", n.Node, n.Resource)
}

/* Flow is from left to right. */
type Flow []NodeResourceRef

type Plan struct {
	Nodes map[string]Node `json:"nodes"`
	Flows []Flow          `json:"flows"`
}
