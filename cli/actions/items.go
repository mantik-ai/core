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
	"github.com/olekukonko/tablewriter"
	"os"
)

// Show items arguments
type ItemsArguments struct {
	Deployed  bool
	Anonymous bool
	Kind      string
	NoTable   bool
}

// Show single item arguments
type ItemArguments struct {
	MantikId string
}

// Tag a single item
type TagArguments struct {
	MantikId    string
	NewMantikId string
}

func ListItems(client *client.EngineClient, args *ItemsArguments) error {
	req := engine.ListArtifactsRequest{
		Deployed:  args.Deployed,
		Anonymous: args.Anonymous,
		Kind:      args.Kind, // empty is no filter
	}
	response, err := client.LocalRegistry.ListArtifacts(context.Background(), &req)
	if err != nil {
		return err
	}
	if args.NoTable {
		return listItemsPlain(response)
	}
	table := tablewriter.NewWriter(os.Stdout)
	table.SetHeader([]string{"Item", "Kind", "Deployment"})
	table.SetCaption(true, fmt.Sprintf("%d Items", len(response.Artifacts)))
	table.SetBorder(false)
	for _, a := range response.Artifacts {
		table.Append([]string{
			formatItemId(a),
			a.ArtifactKind,
			formatDeployment(a),
		})
	}
	table.Render()
	return nil
}

func listItemsPlain(response *engine.ListArtifactResponse) error {
	for _, a := range response.Artifacts {
		fmt.Printf("%s\t%s\t%s\n", formatItemId(a), a.ArtifactKind, formatDeployment(a))
	}
	return nil
}

func formatItemId(a *engine.MantikArtifact) string {
	if len(a.NamedId) > 0 {
		return a.NamedId
	} else {
		return a.ItemId
	}
}

func formatDeployment(a *engine.MantikArtifact) string {
	if a.DeploymentInfo == nil || len(a.DeploymentInfo.InternalUrl) == 0 {
		return ""
	}
	if a.DeploymentInfo.ExternalUrl != "" {
		return a.DeploymentInfo.ExternalUrl
	}
	return fmt.Sprintf("internal %s", a.DeploymentInfo.InternalUrl)
}

func ShowItem(client *client.EngineClient, arg *ItemArguments) error {
	req := engine.GetArtifactRequest{
		MantikId: arg.MantikId,
	}
	response, err := client.LocalRegistry.GetArtifact(context.Background(), &req)
	if err != nil {
		return err
	}
	a := response.Artifact
	PrintItem(a, true, true)
	return nil
}

func PrintItem(a *engine.MantikArtifact, withDeployment bool, withMantikHeader bool) {
	fmt.Printf("NamedId:    %s\n", formatOptionalString(a.NamedId))
	fmt.Printf("ItemId:     %s\n", a.ItemId)
	fmt.Printf("Kind:       %s\n", a.ArtifactKind)
	fmt.Printf("File:       %s\n", formatOptionalString(a.FileId))
	if withDeployment {
		fmt.Printf("Deployment: %s\n", formatDeployment(a))
		if a.DeploymentInfo != nil {
			fmt.Printf("  Name:         %s\n", formatOptionalString(a.DeploymentInfo.Name))
			fmt.Printf("  Internal Url: %s\n", formatOptionalString(a.DeploymentInfo.InternalUrl))
			fmt.Printf("  External Url: %s\n", formatOptionalString(a.DeploymentInfo.ExternalUrl))
			fmt.Printf("  Timestamp: %s\n", a.DeploymentInfo.Timestamp.String())
			for s, sub := range a.DeploymentInfo.Sub {
				fmt.Printf("    Sub Deployment %s\n", s)
				fmt.Printf("      Name: %s\n", sub.Name)
				fmt.Printf("      Internal Url: %s\n", sub.InternalUrl)
			}
		}
	}
	if withMantikHeader {
		fmt.Printf("MantikHeader:\n%s\n", a.MantikHeaderJson)
	}
}

func formatOptionalString(s string) string {
	if len(s) == 0 {
		return "<empty>"
	} else {
		return s
	}
}

func TagItem(engineClient *client.EngineClient, debug bool, args *TagArguments) error {
	response, err := engineClient.LocalRegistry.TagArtifact(context.Background(), &engine.TagArtifactRequest{
		MantikId:         args.MantikId,
		NewNamedMantikId: args.NewMantikId,
	})
	if err != nil {
		return err
	}
	if debug {
		fmt.Printf("Tagged %s to %s, resulting in change: %t", args.MantikId, args.NewMantikId, response.Changed)
	}
	return nil
}
