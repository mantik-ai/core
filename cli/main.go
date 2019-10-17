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
	}
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
	if args.Deploy != nil {
		err = actions.Deploy(engineClient, args.Deploy)
	}

	if err != nil {
		println("Error", err.Error())
	}
	engineClient.Close()
}
