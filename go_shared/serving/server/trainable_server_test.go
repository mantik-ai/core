package server

import (
	"bytes"
	"encoding/json"
	"fmt"
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element/builder"
	"gl.ambrosys.de/mantik/go_shared/ds/formats/natural"
	"gl.ambrosys.de/mantik/go_shared/ds/util/serializer"
	"gl.ambrosys.de/mantik/go_shared/serving"
	"gl.ambrosys.de/mantik/go_shared/serving/test"
	"gl.ambrosys.de/mantik/go_shared/util/dirzip"
	"io"
	"io/ioutil"
	"log"
	"net/http"
	"os"
	"path"
	"sync"
	"testing"
	"time"
)

func TestTrainableStandardPages(t *testing.T) {
	algorithm := test.NewLearnAlgorithm()
	defer algorithm.Cleanup()

	server, err := CreateServerForExecutable(algorithm, ":0")
	assert.NoError(t, err)
	err = server.Listen()

	assert.NoError(t, err)
	url := fmt.Sprintf("http://localhost:%d", server.ListenPort)

	go server.Serve()
	defer server.Close()

	t.Run("index", func(t *testing.T) {
		response, err := http.Get(url + "/")
		assert.NoError(t, err)
		assert.Equal(t, http.StatusOK, response.StatusCode)
	})

	t.Run("type", func(t *testing.T) {
		response, err := http.Get(url + "/type")
		assert.NoError(t, err)
		assert.Equal(t, http.StatusOK, response.StatusCode)
		assert.Equal(t, MimeJson, response.Header.Get(HeaderContentType))
		content, err := ioutil.ReadAll(response.Body)
		assert.NoError(t, err)
		var algoType serving.AlgorithmType
		err = json.Unmarshal(content, &algoType)
		assert.NoError(t, err)
		assert.Equal(t, algorithm.Type(), &algoType)
	})
	t.Run("statType", func(t *testing.T) {
		response, err := http.Get(url + "/stat_type")
		assert.NoError(t, err)
		assert.Equal(t, http.StatusOK, response.StatusCode)
		assert.Equal(t, MimeJson, response.Header.Get(HeaderContentType))
		content, err := ioutil.ReadAll(response.Body)
		assert.NoError(t, err)
		statType := ds.FromJsonStringOrPanic(string(content))
		assert.Equal(t, algorithm.StatType().Underlying, statType)
	})
	t.Run("trainingType", func(t *testing.T) {
		response, err := http.Get(url + "/training_type")
		assert.NoError(t, err)
		assert.Equal(t, http.StatusOK, response.StatusCode)
		assert.Equal(t, MimeJson, response.Header.Get(HeaderContentType))
		content, err := ioutil.ReadAll(response.Body)
		assert.NoError(t, err)
		trainingType := ds.FromJsonStringOrPanic(string(content))
		assert.Equal(t, algorithm.TrainingType().Underlying, trainingType)
	})
	t.Run("unknown pages", func(t *testing.T) {
		response, err := http.Get(url + "/other")
		assert.NoError(t, err)
		assert.Equal(t, http.StatusNotFound, response.StatusCode)
	})
}

func TestLearningCycle(t *testing.T) {
	algorithm := test.NewLearnAlgorithm()
	defer algorithm.Cleanup()

	server, err := CreateServerForExecutable(algorithm, ":0")
	err = server.Listen()
	assert.NoError(t, err)
	url := fmt.Sprintf("http://localhost:%d", server.ListenPort)

	go server.Serve()
	defer server.Close()

	var learnResult bytes.Buffer
	var wg sync.WaitGroup

	// it should block waiting for the learning result
	go func() {
		defer wg.Done()
		response, err := getWith409Support(url+"/result", MimeMantikBundle)

		assert.NoError(t, err)
		assert.Equal(t, http.StatusOK, response.StatusCode)
		assert.Equal(t, MimeZip, response.Header.Get(HeaderContentType))
		io.Copy(&learnResult, response.Body)
		println("Result Done")
	}()

	go func() {
		defer wg.Done()
		response, err := getWith409Support(url+"/stats", MimeMantikBundle)

		assert.NoError(t, err)
		assert.Equal(t, http.StatusOK, response.StatusCode)
		assert.Equal(t, MimeMantikBundle, response.Header.Get(HeaderContentType))

		content, _ := ioutil.ReadAll(response.Body)

		parsed, err := natural.DecodeBundle(serializer.BACKEND_MSGPACK, content)
		assert.NoError(t, err)
		assert.Equal(t, 1, len(parsed.Rows))
		assert.Equal(t, int32(6), parsed.GetTabularPrimitive(0, 0))
	}()

	wg.Add(2)

	go func() {
		defer wg.Done()

		time.Sleep(50 * time.Millisecond) // to trigger race conditions if there are any

		format, err := ds.FromJsonString(
			`
	{
		"columns": {
			"z": "int32"
		}
	}
			`,
		)

		bundle := builder.Bundle(format,
			builder.PrimitiveRow(int32(1)),
			builder.PrimitiveRow(int32(2)),
			builder.PrimitiveRow(int32(3)),
		)

		encoded, err := natural.EncodeBundle(&bundle, serializer.BACKEND_MSGPACK)
		assert.NoError(t, err)
		response, err := http.Post(url+"/train", MimeMantikBundle, bytes.NewReader(encoded))
		assert.NoError(t, err)
		assert.Equal(t, http.StatusOK, response.StatusCode)
	}()
	wg.Add(1)
	wg.Wait()

	assert.True(t, learnResult.Len() > 0)
	tempDir, err := ioutil.TempDir("", "test")
	defer os.RemoveAll(tempDir)
	assert.NoError(t, err)
	err = dirzip.UnzipDirectoryFromZipBuffer(learnResult, tempDir, false)
	assert.NoError(t, err)
	payload, err := ioutil.ReadFile(path.Join(tempDir, "result")) // See algorithm
	assert.NoError(t, err)
	assert.Equal(t, []byte("Train result: 6"), payload)
}

// Call GET url and loops if there is a 409 error
func getWith409Support(url string, accept string) (*http.Response, error) {
	for {
		request, err := http.NewRequest("GET", url, nil)
		if err != nil {
			return nil, err
		}
		request.Header.Add("Accept", accept)
		response, err := http.DefaultClient.Do(request)
		if err != nil {
			return nil, err
		}
		if response.StatusCode == 409 {
			log.Printf("Looping GET %s because of 409", url)
			time.Sleep(100 * time.Millisecond)
			continue
		}
		return response, err
	}
}
