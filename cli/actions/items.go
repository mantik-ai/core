package actions

import (
	"cli/client"
	"cli/cmd"
	"cli/protos/mantik/engine"
	"context"
	"fmt"
	"github.com/olekukonko/tablewriter"
	"os"
)

func ListItems(client *client.EngineClient, args *cmd.ItemsArguments) error {
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

func ShowItem(client *client.EngineClient, arg *cmd.ItemArguments) error {
	req := engine.GetArtifactRequest{
		MantikId: arg.MantikId,
	}
	response, err := client.LocalRegistry.GetArtifact(context.Background(), &req)
	if err != nil {
		return err
	}
	a := response.Artifact
	fmt.Printf("NamedId:    %s\n", formatOptionalString(a.NamedId))
	fmt.Printf("ItemId:     %s\n", a.ItemId)
	fmt.Printf("Kind:       %s\n", a.ArtifactKind)
	fmt.Printf("File:       %s\n", formatOptionalString(a.FileId))
	fmt.Printf("Deployment: %s\n", formatDeployment(a))
	if a.DeploymentInfo != nil {
		fmt.Printf("  Name:         %s\n", formatOptionalString(a.DeploymentInfo.Name))
		fmt.Printf("  Internal Url: %s\n", formatOptionalString(a.DeploymentInfo.InternalUrl))
		fmt.Printf("  External Url: %s\n", formatOptionalString(a.DeploymentInfo.ExternalUrl))
		fmt.Printf("  Timestamp: %s\n", a.DeploymentInfo.Timestamp.String())
	}
	fmt.Printf("Mantikfile:\n%s\n", a.MantikfileJson)
	return nil
}

func formatOptionalString(s string) string {
	if len(s) == 0 {
		return "<empty>"
	} else {
		return s
	}
}