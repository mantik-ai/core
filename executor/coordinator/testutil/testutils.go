package testutil

import "net"

// Returns a free TCP Port for listening
// Not safe against race conditions, but should work for testing
func GetFreeTcpListeningPort() int {
	listener, err := net.Listen("tcp", ":0")
	if err != nil {
		panic(err.Error())
	}
	port := listener.Addr().(*net.TCPAddr).Port
	err = listener.Close()
	if err != nil {
		panic(err.Error())
	}
	return port
}
