package actions

import (
	"cli/client"
	"cli/protos/mantik/engine"
	"context"
	"fmt"
)

type PullArguments struct {
	MantikId string
}

// Arguments for pushing
type PushArguments struct {
	MantikId string
}

func PullItem(client *client.EngineClient, debug bool, args *PullArguments) error {
	response, err := client.RemoteRegistry.PullArtifact(context.Background(), &engine.PullArtifactRequest{
		MantikId: args.MantikId,
		Token:    readLoginToken(),
	})
	if err != nil {
		return err
	}
	fmt.Printf("Pulled %s with a hull of %d elements\n", args.MantikId, len(response.Hull))
	PrintItem(response.Artifact, false, false)
	if debug {
		fmt.Printf("Dependency Hull")
		for _, i := range response.Hull {
			PrintItem(i, false, false)
		}
	}
	return nil
}

func PushItem(client *client.EngineClient, debug bool, args *PushArguments) error {
	response, err := client.RemoteRegistry.PushArtifact(context.Background(), &engine.PushArtifactRequest{
		MantikId: args.MantikId,
		Token:    readLoginToken(),
	})
	if err != nil {
		return err
	}
	fmt.Printf("Pushed %s with a hull of %d elements\n", args.MantikId, len(response.Hull))
	PrintItem(response.Artifact, false, false)
	if debug {
		fmt.Printf("Dependency Hull")
		for _, i := range response.Hull {
			PrintItem(i, false, false)
		}
	}
	return nil
}

func readLoginToken() *engine.LoginToken {
	loginState, err := readLoginState()
	if err != nil {
		return nil
	}
	return &engine.LoginToken{
		Url:   loginState.Url,
		Token: loginState.Token,
	}
}
