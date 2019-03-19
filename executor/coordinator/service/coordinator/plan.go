package coordinator

import "fmt"

type Node struct {
	Address string `json:"address"`
}

type NodeResourceRef struct {
	Node     string `json:"node"`
	Resource string `json:"resource"`
}

func (n *NodeResourceRef) Str() string {
	return fmt.Sprintf("%s:%s", n.Node, n.Resource)
}

/* Flow is from left to right. */
type Flow []NodeResourceRef

type Plan struct {
	Nodes       map[string]Node `json:"nodes"`
	Flows       []Flow          `json:"flows"`
	ContentType *string
}
