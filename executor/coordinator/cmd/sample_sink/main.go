package main

import (
	"coordinator/cmd/sample_common"
	"fmt"
	"io/ioutil"
	"net/http"
)

func main() {
	s := sample_common.CrateServerWithForce("sample_source")
	s.AddPost("Server sample content", "/in", func(writer http.ResponseWriter, request *http.Request) {
		bytes, err := ioutil.ReadAll(request.Body)
		if err != nil {
			println("Could not read Request", err.Error())
			writer.WriteHeader(http.StatusInternalServerError)
		} else {
			fmt.Printf("Consumed %d bytes", len(bytes))
			writer.WriteHeader(http.StatusOK)
		}
	})
	s.RunWithForce()
}
