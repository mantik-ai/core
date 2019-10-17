package client

import (
	"cli/protos/mantik/engine"
	"context"
)

// Simplifies handling of items
type ItemWrapper struct {
	manager  *ItemManager
	response *engine.NodeResponse
}

// Deploy an Item.
// Ingress and nameHint can be empty
func (i *ItemWrapper) Deploy(ingress string, nameHint string) (*engine.DeployItemResponse, error) {
	request := engine.DeployItemRequest{
		SessionId:   i.manager.sessionId,
		ItemId:      i.response.ItemId,
		IngressName: ingress,
		NameHint:    nameHint,
	}
	return i.manager.executor.DeployItem(context.Background(), &request)
}
