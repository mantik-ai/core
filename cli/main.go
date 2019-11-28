package main

import (
	"cli/actions"
	"cli/client"
	"cli/cmd"
	"flag"
	"github.com/sirupsen/logrus"
	"os"
)

// Injected by build.sh
var AppVersion string

func main() {
	args, err := cmd.ParseArguments(os.Args, AppVersion)
	if err != nil {
		if err == cmd.MissingCommand || err == flag.ErrHelp {
			os.Exit(0)
		}
		println(err.Error())
		os.Exit(1)
	}

	engineClient, err := client.MakeEngineClientInsecure(&args.ClientArgs)
	if err != nil {
		logrus.Fatal("Could not create client", err.Error())
		os.Exit(2)
	}
	defer engineClient.Close()
	logrus.Debugf("Initialized connection to %s", engineClient.Address())

	if args.Version != nil {
		actions.PrintVersion(engineClient, args.Version, AppVersion)
	}
	if args.Items != nil {
		err = actions.ListItems(engineClient, args.Items)
	}
	if args.Item != nil {
		err = actions.ShowItem(engineClient, args.Item)
	}
	if args.Tag != nil {
		err = actions.TagItem(engineClient, args.Debug, args.Tag)
	}
	if args.Deploy != nil {
		err = actions.Deploy(engineClient, args.Deploy)
	}
	if args.Add != nil {
		err = actions.AddItem(engineClient, args.Debug, args.Add)
	}
	if args.Extract != nil {
		err = actions.ExtractItem(engineClient, args.Debug, args.Extract)
	}
	if args.Login != nil {
		err = actions.Login(engineClient, args.Login)
	}
	if args.Logout != nil {
		err = actions.Logout(engineClient, args.Logout)
	}
	if args.Pull != nil {
		err = actions.PullItem(engineClient, args.Debug, args.Pull)
	}
	if args.Push != nil {
		err = actions.PushItem(engineClient, args.Debug, args.Push)
	}

	if err != nil {
		println("Error", err.Error())
		os.Exit(3)
	}
}
