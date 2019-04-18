package server

import (
	"gl.ambrosys.de/mantik/go_shared/serving"
)

type AlgorithmServer struct {
	*Server
	algorithm serving.ExecutableAlgorithm
}

func CreateAlgorithmServer(algorithm serving.ExecutableAlgorithm, address string) (*AlgorithmServer, error) {
	mainServer, err := CreateServer(address)
	s := AlgorithmServer{
		Server:    mainServer,
		algorithm: algorithm,
	}
	s.serveMux.HandleFunc("/type", CreateFixedJsonHandler(s.algorithm.Type()))
	applyHandler, err := GenerateTypedStreamHandler(
		algorithm.Type().Input.Underlying,
		algorithm.Type().Output.Underlying,
		algorithm.Execute,
	)
	if err != nil {
		return nil, err
	}
	s.serveMux.HandleFunc("/apply", applyHandler)

	return &s, err
}
