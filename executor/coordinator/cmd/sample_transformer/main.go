package main

import (
	"coordinator/cmd/sample_common"
	"fmt"
	"io/ioutil"
	"net/http"
	"time"
)

func main() {
	s := sample_common.CrateServerWithForce("sample_transformer")

	s.AddPost("Transform input data", "/apply", func(writer http.ResponseWriter, request *http.Request) {
		bytes, err := ioutil.ReadAll(request.Body)
		if err != nil {
			println("Could not read Request", err.Error())
			writer.WriteHeader(http.StatusInternalServerError)
			return
		}
		time.Sleep(time.Second)
		response := fmt.Sprintf("Got %d bytes", len(bytes))
		writer.Write([]byte(response))
	})
	s.RunWithForce()
}
