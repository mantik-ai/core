package main

import (
	"context"
	"coordinator/service/cli"
	"coordinator/service/coordinator"
	"coordinator/service/protocol"
	"coordinator/service/rpc_utils"
	"coordinator/service/sidecar"
	"flag"
	"fmt"
	"os"
)

var usage = `
Commands:
  coordinator - Coordinator Mode
  sidecar     - Sidecar Mode
  cancel      - Cancel a running Coordinator or SideCar
  help        - Print this help
`

var CoordinatorPlanEnv = "COORDINATOR_PLAN"
var CoordinatorIpEnv = "COORDINATOR_IP"

var RcInvalidArgument = 1
var RcInvalidPlan = 2
var RcOtherError = 3
var RcCancelled = 4

var settings = protocol.CreateDefaultSettings()

func addSettings(set *flag.FlagSet) {
	set.IntVar(
		&settings.Port,
		"port",
		settings.Port,
		fmt.Sprintf("Port for sidecar communication (default=%d)", settings.Port),
	)
	set.DurationVar(
		&settings.ConnectTimeout,
		"connectTimeout",
		settings.ConnectTimeout,
		"Connection Timeout",
	)
	set.DurationVar(
		&settings.WaitForCoordinatorTimeout,
		"waitCoordinatorTimeout",
		settings.WaitForCoordinatorTimeout,
		"Wait for Coordinator Timeout",
	)
	set.DurationVar(
		&settings.WaitForWebServiceReachable,
		"waitForWebServiceReachable",
		settings.WaitForWebServiceReachable,
		"Wait for Webservice reachable",
	)
	set.DurationVar(
		&settings.RetryTime,
		"retryTime",
		settings.RetryTime,
		"Period to wait for trying to access some other node again",
	)
}

func main() {
	if len(os.Args) <= 1 {
		println("Command expected")
		println(usage)
		os.Exit(0)
	}

	sideCarOptions := flag.NewFlagSet("sidecar", flag.ExitOnError)
	sideCarUrl := sideCarOptions.String("url", "", "URL, for which this SideCar is for.")
	sideCarShutdownWebService := sideCarOptions.Bool("shutdown", false, "If set, the URL will be called to shutdown via POST /admin/quit after the side car ends")
	addSettings(sideCarOptions)

	coordinatorOptions := flag.NewFlagSet("coordinator", flag.ExitOnError)
	coordinatorPlan := coordinatorOptions.String("plan", "", fmt.Sprintf("Plan for coordinator as JSON, otherwise read from %s environment variable", CoordinatorPlanEnv))
	coordinatorAddress := coordinatorOptions.String("address", "", fmt.Sprintf("Address of the coordinator (otherwise read IP from %s and add port)", CoordinatorIpEnv))
	addSettings(coordinatorOptions)

	cancelArgs := flag.NewFlagSet("cancel", flag.ExitOnError)
	cancelAddr := cancelArgs.String("address", fmt.Sprintf("localhost:%d", settings.Port), fmt.Sprintf("RPC Adress of the node to send quit request"))
	cancelReason := cancelArgs.String("reason", "", "Reason for cancellation")

	command := os.Args[1]
	var mainErr error
	switch command {
	case "coordinator":
		coordinatorOptions.Parse(os.Args[2:])
		var plan coordinator.Plan
		err := cli.ParseJsonFile("plan", *coordinatorPlan, CoordinatorPlanEnv, &plan)
		if err != nil {
			println("Could not parse coordinator plan", err.Error())
			os.Exit(RcInvalidPlan)
		}

		if len(*coordinatorAddress) == 0 {
			ipFromEnv := os.Getenv(CoordinatorIpEnv)
			if len(ipFromEnv) == 0 {
				println("Neither coordinator address given nor ", CoordinatorIpEnv, " is set")
				os.Exit(RcInvalidArgument)
			}
			*coordinatorAddress = fmt.Sprintf("%s:%d", ipFromEnv, settings.Port)
		}

		c, err := coordinator.CreateCoordinator(*coordinatorAddress, settings, &plan)
		if err != nil {
			println("Could not initialize coordinator", err.Error())
			os.Exit(RcOtherError)
		}
		mainErr = c.Run()
	case "sidecar":
		sideCarOptions.Parse(os.Args[2:])
		if len(*sideCarUrl) == 0 {
			println("URL is required")
			os.Exit(RcInvalidArgument)
		}
		s, err := sidecar.CreateSideCar(settings, *sideCarUrl, *sideCarShutdownWebService)
		if err != nil {
			println("Could not initialize sidecar", err.Error())
			os.Exit(RcOtherError)
		}
		mainErr = s.Run()
	case "cancel":
		cancelArgs.Parse(os.Args[2:])
		client, err := rpc_utils.ConnectRpcWithTimeoutMultiple(context.Background(), *cancelAddr, settings.ConnectTimeout, settings.RetryTime)
		if err != nil {
			println("Could not connect to", *cancelAddr)
			os.Exit(RcOtherError)
		}
		req := protocol.CancelRequest{*cancelReason}
		var res protocol.CancelResponse
		err = rpc_utils.CallRpcWithTimeout(context.Background(), settings.RpcTimeout, client, "Generic.Cancel", &req, &res)
		if err != nil {
			println("Could not send RPC Request", err.Error())
			os.Exit(RcOtherError)
		}
		if res.IsError() {
			println("Other side reported an error", res.Error)
			os.Exit(RcOtherError)
		}
		client.Close()
		os.Exit(0)
	case "help":
		println(usage)
		os.Exit(0)
	default:
		println("Unknown command", command)
		os.Exit(RcInvalidArgument)
	}
	if mainErr == context.Canceled {
		os.Exit(RcCancelled)
	} else {
		if mainErr != nil {
			println("Error", mainErr.Error())
			os.Exit(RcOtherError)
		}
	}
}
