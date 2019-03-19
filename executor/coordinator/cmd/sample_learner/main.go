package main

import (
	"coordinator/cmd/sample_common"
	"fmt"
	"io/ioutil"
	"net/http"
	"time"
)

func main() {
	s := sample_common.CrateServerWithForce("sample_learner")

	startStatus := make(chan struct{}, 1)
	startResult := make(chan struct{}, 1)

	s.AddGet("Learn Status", "/status", func(writer http.ResponseWriter, request *http.Request) {
		<-startStatus
		writer.Write([]byte("Learned something"))
	})

	s.AddGet("Learn Result", "/result", func(writer http.ResponseWriter, request *http.Request) {
		<-startResult
		writer.Write([]byte("Learning Result"))
	})

	s.AddPost("Learning Input Data", "/in", func(writer http.ResponseWriter, request *http.Request) {
		bytes, err := ioutil.ReadAll(request.Body)
		if err != nil {
			println("Could not read Request", err.Error())
			writer.WriteHeader(http.StatusInternalServerError)
		} else {
			fmt.Printf("Consumed %d bytes", len(bytes))
			writer.WriteHeader(http.StatusOK)

			// Simulating calculation
			time.AfterFunc(time.Second*2, func() {
				startStatus <- struct{}{}
			})
			time.AfterFunc(time.Second*4, func() {
				startResult <- struct{}{}
			})
		}
	})
	s.RunWithForce()
}
