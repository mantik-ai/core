package cmd

import (
	"cli/actions"
	"cli/client"
	"errors"
	"github.com/urfave/cli"
)

type Arguments struct {
	ClientArgs client.ClientArguments

	// if set the argument is requested
	Version *actions.VersionArguments
	Items   *actions.ItemsArguments
	Item    *actions.ItemArguments
	Deploy  *actions.DeployArguments
}

var MissingCommand = errors.New("missing command")
var UnexpectedCommand = errors.New("unexpected command")
var MissingArgument = errors.New("missing argument")

// Parse arguments. Error messages are already printed.
func ParseArguments(argv []string, appVersion string) (*Arguments, error) {
	var args = Arguments{}
	app := cli.NewApp()
	app.Name = "mantik"
	app.Description = "Mantik CLI Client"
	app.Version = appVersion
	app.Usage = "Mantik CLI Client"
	app.UseShortOptionHandling = true

	app.Flags = []cli.Flag{
		cli.IntFlag{Name: "port", Value: client.DefaultPort},
		cli.StringFlag{Name: "host", Value: client.DefaultHost},
	}

	app.Commands = []cli.Command{
		{
			Name:  "version",
			Usage: "Show engine version",
			Action: func(c *cli.Context) error {
				args.Version = &actions.VersionArguments{}
				return nil
			},
		},
		{
			Name:  "items",
			Usage: "List items",
			Flags: []cli.Flag{
				cli.BoolFlag{Name: "all,a", Usage: "Also list anonymous items"},
				cli.BoolFlag{Name: "deployed,d", Usage: "List deployed items"},
				cli.StringFlag{Name: "kind,k", Usage: "Filter for specific kind"},
				cli.BoolFlag{Name: "noTable", Usage: "Render pure text instead of table"},
			},
			Action: func(c *cli.Context) error {
				args.Items = &actions.ItemsArguments{}
				args.Items.Anonymous = c.Bool("all")
				args.Items.Deployed = c.Bool("deployed")
				args.Items.Kind = c.String("kind")
				args.Items.NoTable = c.Bool("noTable")
				return nil
			},
		},
		{
			Name:      "item",
			Usage:     "Show Mantik Item",
			ArgsUsage: "<MantikId>",
			Action: func(c *cli.Context) error {
				if c.NArg() < 1 {
					return MissingArgument
				}
				args.Item = &actions.ItemArguments{}
				args.Item.MantikId = c.Args().Get(0)
				return nil
			},
		},
		{
			Name:  "deploy",
			Usage: "Deploy Mantik Item",
			Flags: []cli.Flag{
				cli.StringFlag{Name: "ingress,i", Usage: "Ingress Name"},
				cli.StringFlag{Name: "nameHint,n", Usage: "Name Hint"},
			},
			ArgsUsage: "<MantikId>",
			Action: func(c *cli.Context) error {
				if c.NArg() < 1 {
					return MissingArgument
				}
				args.Deploy = &actions.DeployArguments{}
				args.Deploy.IngressName = c.String("ingress")
				args.Deploy.NameHint = c.String("nameHint")
				args.Deploy.MantikId = c.Args().Get(0)
				return nil
			},
		},
	}
	app.Before = func(c *cli.Context) error {
		args.ClientArgs.Port = c.GlobalInt("port")
		args.ClientArgs.Host = c.GlobalString("host")
		return nil
	}
	err := app.Run(argv)
	return &args, err
}
