package main

import (
	"cli/client"
	"cli/cmd"
	"flag"
	"fmt"
	"github.com/sirupsen/logrus"
	"os"
)

// Injected by build.sh
var AppVersion string

func main() {
	args, err := cmd.ParseArguments(os.Args)
	if err != nil {
		if err == cmd.MissingCommand || err == flag.ErrHelp {
			os.Exit(0)
		}
		println(err.Error())
		os.Exit(1)
	}

	engineClient, err := client.MakeEngineClientInsecure(args.Common.Host, args.Common.Port)
	if err != nil {
		logrus.Fatal("Could not create client", err.Error())
	}
	logrus.Debugf("Initialized connection to %s", engineClient.Address())

	if args.Version != nil {
		runVersion(engineClient, args.Version)
	}

	engineClient.Close()
}

func runVersion(engineClient *client.EngineClient, arguments *cmd.VersionArguments) {
	version, err := engineClient.Version()
	if err != nil {
		logrus.Fatal("Could not connect", err.Error())
	}
	fmt.Println("Engine Version ", version.Version)
	fmt.Println("Client Version ", AppVersion)
}
