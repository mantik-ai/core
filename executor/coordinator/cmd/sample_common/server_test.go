package sample_common

import (
	"bytes"
	"context"
	"coordinator/testutil"
	"fmt"
	"github.com/stretchr/testify/assert"
	"net/http"
	"sync"
	"testing"
)

func TestServerShutdown(t *testing.T) {
	port := testutil.GetFreeTcpListeningPort()
	server, err := CreateServerWithAddress("sample", fmt.Sprintf("localhost:%d", port))
	assert.NoError(t, err)
	wg := sync.WaitGroup{}
	wg.Add(1)
	var serverResult error
	go func() {
		serverResult = server.Run()
		wg.Done()
	}()

	url := fmt.Sprintf("http://localhost:%d/", port)
	response, err := http.Get(url)
	assert.NoError(t, err)
	assert.Equal(t, 200, response.StatusCode)

	// Quitting the server
	quitUrl := fmt.Sprintf("http://localhost:%d/admin/quit", port)
	response, err = http.Post(quitUrl, "application/empty", &bytes.Buffer{})
	assert.NoError(t, err)
	assert.Equal(t, 200, response.StatusCode)
	wg.Wait()
	assert.NoError(t, serverResult)
}

func TestServerAddCalls(t *testing.T) {
	port := testutil.GetFreeTcpListeningPort()
	server, err := CreateServerWithAddress("sample", fmt.Sprintf("localhost:%d", port))
	assert.NoError(t, err)
	server.AddGet("", "/a", func(writer http.ResponseWriter, request *http.Request) {
		writer.WriteHeader(200)
	})
	server.AddPost("", "/b", func(writer http.ResponseWriter, request *http.Request) {
		writer.WriteHeader(200)
	})
	wg := sync.WaitGroup{}
	wg.Add(1)
	go func() {
		server.Run()
		wg.Done()
	}()

	aUrl := fmt.Sprintf("http://localhost:%d/a", port)

	response, err := http.Get(aUrl)
	assert.NoError(t, err)
	assert.Equal(t, http.StatusOK, response.StatusCode)

	bUrl := fmt.Sprintf("http://localhost:%d/b", port)
	response, err = http.Post(bUrl, "", &bytes.Buffer{})
	assert.NoError(t, err)
	assert.Equal(t, http.StatusOK, response.StatusCode)

	// it should not the wrong http method
	response, err = http.Post(aUrl, "", &bytes.Buffer{})
	assert.NoError(t, err)
	assert.Equal(t, http.StatusMethodNotAllowed, response.StatusCode)

	response, err = http.Get(bUrl)
	assert.NoError(t, err)
	assert.Equal(t, http.StatusMethodNotAllowed, response.StatusCode)

	server.Shutdown(context.Background())
	wg.Wait()
}
