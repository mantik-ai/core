/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
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
