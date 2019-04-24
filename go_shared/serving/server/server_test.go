package server

import (
	"bytes"
	"encoding/json"
	"fmt"
	"github.com/stretchr/testify/assert"
	"github.com/vmihailenco/msgpack"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element/builder"
	"gl.ambrosys.de/mantik/go_shared/ds/formats/natural"
	"gl.ambrosys.de/mantik/go_shared/ds/util/serializer"
	"gl.ambrosys.de/mantik/go_shared/serving"
	"gl.ambrosys.de/mantik/go_shared/serving/test"
	"io/ioutil"
	"net/http"
	"testing"
)

type Fixture struct {
	url       string
	algorithm serving.ExecutableAlgorithm
	server    *Server
}

func (m *Fixture) Close() {
	m.server.Close()
	m.algorithm.Cleanup()
}

func CreateFixture(t *testing.T) *Fixture {
	algorithm := test.NewThreeTimes()

	server, err := CreateServerForExecutable(algorithm, ":0")
	err = server.Listen()
	assert.NoError(t, err)
	url := fmt.Sprintf("http://localhost:%d", server.ListenPort)
	go server.Serve()
	return &Fixture{
		url,
		algorithm,
		server,
	}
}

func TestStandardPages(t *testing.T) {
	f := CreateFixture(t)
	defer f.Close()

	t.Run("index", func(t *testing.T) {
		response, err := http.Get(f.url + "/")
		assert.NoError(t, err)
		assert.Equal(t, http.StatusOK, response.StatusCode)
	})

	t.Run("type", func(t *testing.T) {
		response, err := http.Get(f.url + "/type")
		assert.NoError(t, err)
		assert.Equal(t, http.StatusOK, response.StatusCode)
		assert.Equal(t, MimeJson, response.Header.Get(HeaderContentType))
		content, err := ioutil.ReadAll(response.Body)
		assert.NoError(t, err)
		var algoType serving.AlgorithmType
		err = json.Unmarshal(content, &algoType)
		assert.NoError(t, err)
		assert.Equal(t, f.algorithm.Type(), &algoType)
	})
	t.Run("unknown pages", func(t *testing.T) {
		response, err := http.Get(f.url + "/other")
		assert.NoError(t, err)
		assert.Equal(t, http.StatusNotFound, response.StatusCode)
	})
	t.Run("quit", func(t *testing.T) {
		response, err := http.Post(f.url+"/admin/quit", "", nil)
		assert.NoError(t, err)
		assert.Equal(t, http.StatusOK, response.StatusCode)
	})
}

func TestServingJson(t *testing.T) {
	f := CreateFixture(t)
	defer f.Close()

	t.Run("empty", func(t *testing.T) {
		sample := bytes.Buffer{}
		response, err := http.Post(f.url+"/apply", MimeJson, &sample)
		assert.NoError(t, err)
		assert.Equal(t, http.StatusOK, response.StatusCode)
		assert.Equal(t, MimeJson, response.Header.Get(HeaderContentType))
		content, _ := ioutil.ReadAll(response.Body)
		assert.Equal(t, 0, len(content))
	})

	t.Run("simple", func(t *testing.T) {
		sample := `[400]`
		expectedResponse := `[1200]`
		response, err := http.Post(f.url+"/apply", MimeJson, bytes.NewBufferString(sample))
		assert.NoError(t, err)
		assert.Equal(t, http.StatusOK, response.StatusCode)
		assert.Equal(t, MimeJson, response.Header.Get(HeaderContentType))
		content, _ := ioutil.ReadAll(response.Body)
		assert.Equal(t, []byte(expectedResponse), content)
	})

	t.Run("multiple", func(t *testing.T) {
		// tricky, as the algorithm is using a single value only.
		sample := `[100][-100][200]`
		expectedResponse := `[300][-300][600]`
		response, err := http.Post(f.url+"/apply", MimeJson, bytes.NewBufferString(sample))
		assert.NoError(t, err)
		assert.Equal(t, http.StatusOK, response.StatusCode)
		content, _ := ioutil.ReadAll(response.Body)
		assert.Equal(t, []byte(expectedResponse), content)
	})
}

func TestServingMsgPack(t *testing.T) {
	f := CreateFixture(t)
	defer f.Close()

	t.Run("empty", func(t *testing.T) {
		sample := bytes.Buffer{}
		response, err := http.Post(f.url+"/apply", MimeMsgPack, &sample)
		assert.NoError(t, err)
		assert.Equal(t, http.StatusOK, response.StatusCode)
		assert.Equal(t, MimeMsgPack, response.Header.Get(HeaderContentType))
		content, _ := ioutil.ReadAll(response.Body)
		assert.Equal(t, 0, len(content))
	})

	t.Run("simple", func(t *testing.T) {
		buf := bytes.Buffer{}
		encoder := msgpack.NewEncoder(&buf)
		encoder.EncodeArrayLen(1)
		encoder.EncodeInt32(5)

		expectedResponse := bytes.Buffer{}
		encoder = msgpack.NewEncoder(&expectedResponse)
		encoder.EncodeArrayLen(1)
		encoder.EncodeInt32(15)

		response, err := http.Post(f.url+"/apply", MimeMsgPack, &buf)
		assert.NoError(t, err)
		assert.Equal(t, http.StatusOK, response.StatusCode)
		assert.Equal(t, MimeMsgPack, response.Header.Get(HeaderContentType))
		content, _ := ioutil.ReadAll(response.Body)
		assert.Equal(t, expectedResponse.Bytes(), content)
	})

	t.Run("multiple", func(t *testing.T) {
		buf := bytes.Buffer{}
		encoder := msgpack.NewEncoder(&buf)
		encoder.EncodeArrayLen(1)
		encoder.EncodeInt32(100)
		encoder.EncodeArrayLen(1)
		encoder.EncodeInt32(-100)
		encoder.EncodeArrayLen(1)
		encoder.EncodeInt32(400)

		expectedResponse := bytes.Buffer{}
		encoder = msgpack.NewEncoder(&expectedResponse)
		encoder.EncodeArrayLen(1)
		encoder.EncodeInt32(300)
		encoder.EncodeArrayLen(1)
		encoder.EncodeInt32(-300)
		encoder.EncodeArrayLen(1)
		encoder.EncodeInt32(1200)

		response, err := http.Post(f.url+"/apply", MimeMsgPack, &buf)
		assert.NoError(t, err)
		assert.Equal(t, http.StatusOK, response.StatusCode)
		assert.Equal(t, MimeMsgPack, response.Header.Get(HeaderContentType))
		content, _ := ioutil.ReadAll(response.Body)
		assert.Equal(t, expectedResponse.Bytes(), content)
	})
}

func TestServingWithHeader(t *testing.T) {
	f := CreateFixture(t)
	defer f.Close()

	// Note: the inport format has a nother column name, it must auto adapt that.
	format, err := ds.FromJsonString(
		`
	{
		"columns": {
			"z": "int32"
		}
	}
			`,
	)
	assert.NoError(t, err)
	bundle := builder.Bundle(format,
		builder.PrimitiveRow(int32(4)),
		builder.PrimitiveRow(int32(2)),
		builder.PrimitiveRow(int32(-2)),
	)

	t.Run("msgpack", func(t *testing.T) {
		encoded, err := natural.EncodeBundle(&bundle, serializer.BACKEND_MSGPACK)
		assert.NoError(t, err)
		response, err := http.Post(f.url+"/apply", MimeMantikBundle, bytes.NewReader(encoded))
		assert.NoError(t, err)
		assert.Equal(t, http.StatusOK, response.StatusCode)
		assert.Equal(t, MimeMantikBundle, response.Header.Get(HeaderContentType))
		content, _ := ioutil.ReadAll(response.Body)

		parsed, err := natural.DecodeBundle(serializer.BACKEND_MSGPACK, content)
		assert.NoError(t, err)
		assert.Equal(t, 3, len(parsed.Rows))
		assert.Equal(t, int32(12), parsed.GetTabularPrimitive(0, 0))
		assert.Equal(t, int32(6), parsed.GetTabularPrimitive(1, 0))
		assert.Equal(t, int32(-6), parsed.GetTabularPrimitive(2, 0))
	})

	t.Run("json", func(t *testing.T) {
		encoded, err := natural.EncodeBundle(&bundle, serializer.BACKEND_JSON)
		assert.NoError(t, err)
		response, err := http.Post(f.url+"/apply", MimeMantikBundleJson, bytes.NewReader(encoded))
		assert.NoError(t, err)
		assert.Equal(t, http.StatusOK, response.StatusCode)
		assert.Equal(t, MimeMantikBundleJson, response.Header.Get(HeaderContentType))
		content, _ := ioutil.ReadAll(response.Body)

		parsed, err := natural.DecodeBundle(serializer.BACKEND_JSON, content)
		assert.NoError(t, err)
		assert.Equal(t, 3, len(parsed.Rows))
		assert.Equal(t, int32(12), parsed.GetTabularPrimitive(0, 0))
		assert.Equal(t, int32(6), parsed.GetTabularPrimitive(1, 0))
		assert.Equal(t, int32(-6), parsed.GetTabularPrimitive(2, 0))
	})
}
