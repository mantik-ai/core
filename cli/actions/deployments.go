package actions

import (
	"cli/client"
	"fmt"
)

// Deploy a Mantik Item
type DeployArguments struct {
	MantikId    string
	IngressName string
	NameHint    string
}

func Deploy(engine *client.EngineClient, deploy *DeployArguments) error {
	fmt.Println("Deploying", deploy.MantikId, "...")
	item, err := engine.Items.Get(deploy.MantikId)
	if err != nil {
		return err
	}
	response, err := item.Deploy(deploy.IngressName, deploy.NameHint)
	if err != nil {
		return err
	}
	fmt.Printf("Deployed Item %s\n", deploy.MantikId)
	fmt.Printf("External Url: %s\n", formatOptionalString(response.ExternalUrl))
	fmt.Printf("Internal Url: %s\n", formatOptionalString(response.InternalUrl))
	fmt.Printf("Name:         %s\n", formatOptionalString(response.Name))
	return nil
}
