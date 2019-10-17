package actions

import (
	"cli/client"
	"fmt"
	"github.com/sirupsen/logrus"
)

// Show version Arguments
type VersionArguments struct {
}

func PrintVersion(engineClient *client.EngineClient, args *VersionArguments, appVersion string) {
	version, err := engineClient.Version()
	if err != nil {
		logrus.Fatal("Could not connect", err.Error())
	}
	fmt.Println("Engine Version ", version.Version)
	fmt.Println("Client Version ", appVersion)
}
