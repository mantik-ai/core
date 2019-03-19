package protocol

import (
	"time"
)

type Settings struct {
	// Default Listen Port
	Port int
	// Connection Timeout
	ConnectTimeout time.Duration
	// Retry wait time for connecting
	RetryTime time.Duration
	// How long a side car waits for a coordinator
	WaitForCoordinatorTimeout time.Duration
	// How long a side car waits for a web service to be reachable upon startup
	// Can be set to `0` to disable waiting
	WaitForWebServiceReachable time.Duration
	// Timeout for RPC Calls
	RpcTimeout time.Duration
	// Timeout for a whole job
	JobExecutionTimeout time.Duration
}

func CreateDefaultSettings() *Settings {
	return &Settings{
		Port:                       8503,
		ConnectTimeout:             time.Minute,
		RetryTime:                  1 * time.Second,
		WaitForCoordinatorTimeout:  time.Minute,
		WaitForWebServiceReachable: time.Minute,
		RpcTimeout:                 time.Minute,
		JobExecutionTimeout:        24 * time.Hour,
	}
}

func CreateRandomPortSettings() *Settings {
	s := CreateDefaultSettings()
	s.Port = 0
	return s
}
