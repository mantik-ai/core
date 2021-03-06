/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package tfadapter

import (
	"bytes"
	"encoding/json"
	"fmt"
	"github.com/mantik-ai/core/go_shared/serving"
	"github.com/mantik-ai/core/go_shared/serving/server"
	"github.com/pkg/errors"
	"github.com/stretchr/testify/assert"
	"github.com/vmihailenco/msgpack"
	"io/ioutil"
	"net/http"
	"path"
	"testing"
)

func loadMantikExecutableAlgorithm(backend serving.Backend, directory string) (serving.ExecutableAlgorithm, error) {
	mantikHeader, err := ioutil.ReadFile(directory + "/MantikHeader")

	if err != nil {
		return nil, errors.Wrap(err, "Could not read MantikHeader")
	}
	parsedMantikHeader, err := serving.ParseMantikHeader(mantikHeader)
	if err != nil {
		return nil, errors.Wrapf(err, "Could not parse MantikHeader")
	}

	payloadDir := path.Join(directory, "payload")
	algorithm, err := backend.LoadModel(&payloadDir, parsedMantikHeader)

	if err != nil {
		return nil, errors.Wrap(err, "Could not load model")
	}

	// trying to bridge it to the expected format
	adapted, err := serving.AutoAdapt(algorithm, parsedMantikHeader)
	if err != nil {
		algorithm.Cleanup()
		return nil, errors.Wrap(err, "Could not adapt algorithm")
	}
	return adapted.(serving.ExecutableAlgorithm), nil
}

type Fixture struct {
	url       string
	algorithm serving.ExecutableAlgorithm
	server    *server.Server
}

func (m *Fixture) Close() {
	m.server.Close()
	m.algorithm.Cleanup()
}

func CreateFixture(t *testing.T, dir string) *Fixture {
	algorithm, err := loadMantikExecutableAlgorithm(&TensorflowBackend{}, "../../../../test/resources/samples/double_multiply")
	assert.NoError(t, err)

	server, err := server.CreateServerForExecutable(algorithm, ":0")
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
	f := CreateFixture(t, "../../../../test/resources/samples/double_multiply")
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
		assert.Equal(t, server.MimeJson, response.Header.Get(server.HeaderContentType))
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
}

func TestServingJson(t *testing.T) {
	f := CreateFixture(t, "../../../../test/resources/samples/double_multiply")
	defer f.Close()

	t.Run("empty", func(t *testing.T) {
		sample := bytes.Buffer{}
		response, err := http.Post(f.url+"/apply", server.MimeJson, &sample)
		assert.NoError(t, err)
		assert.Equal(t, http.StatusOK, response.StatusCode)
		assert.Equal(t, server.MimeJson, response.Header.Get(server.HeaderContentType))
		content, _ := ioutil.ReadAll(response.Body)
		assert.Equal(t, 0, len(content))
	})

	t.Run("simple", func(t *testing.T) {
		sample := `[400]`
		expectedResponse := `[800]`
		response, err := http.Post(f.url+"/apply", server.MimeJson, bytes.NewBufferString(sample))
		assert.NoError(t, err)
		assert.Equal(t, http.StatusOK, response.StatusCode)
		assert.Equal(t, server.MimeJson, response.Header.Get(server.HeaderContentType))
		content, _ := ioutil.ReadAll(response.Body)
		assert.Equal(t, []byte(expectedResponse), content)
	})

	t.Run("multiple", func(t *testing.T) {
		// tricky, as the algorithm is using a single value only.
		sample := `[100][-100][200]`
		expectedResponse := `[200][-200][400]`
		response, err := http.Post(f.url+"/apply", server.MimeJson, bytes.NewBufferString(sample))
		assert.NoError(t, err)
		assert.Equal(t, http.StatusOK, response.StatusCode)
		content, _ := ioutil.ReadAll(response.Body)
		assert.Equal(t, []byte(expectedResponse), content)
	})
}

func TestServingMsgPack(t *testing.T) {
	f := CreateFixture(t, "../../../../test/resources/samples/double_multiply")
	defer f.Close()

	t.Run("empty", func(t *testing.T) {
		sample := bytes.Buffer{}
		response, err := http.Post(f.url+"/apply", server.MimeMsgPack, &sample)
		assert.NoError(t, err)
		assert.Equal(t, http.StatusOK, response.StatusCode)
		assert.Equal(t, server.MimeMsgPack, response.Header.Get(server.HeaderContentType))
		content, _ := ioutil.ReadAll(response.Body)
		assert.Equal(t, 0, len(content))
	})

	t.Run("simple", func(t *testing.T) {
		buf := bytes.Buffer{}
		encoder := msgpack.NewEncoder(&buf)
		encoder.EncodeArrayLen(1)
		encoder.EncodeFloat64(4.5)

		expectedResponse := bytes.Buffer{}
		encoder = msgpack.NewEncoder(&expectedResponse)
		encoder.EncodeArrayLen(1)
		encoder.EncodeFloat64(9.0)

		response, err := http.Post(f.url+"/apply", server.MimeMsgPack, &buf)
		assert.NoError(t, err)
		assert.Equal(t, http.StatusOK, response.StatusCode)
		assert.Equal(t, server.MimeMsgPack, response.Header.Get(server.HeaderContentType))
		content, _ := ioutil.ReadAll(response.Body)
		assert.Equal(t, expectedResponse.Bytes(), content)
	})

	t.Run("multiple", func(t *testing.T) {
		buf := bytes.Buffer{}
		encoder := msgpack.NewEncoder(&buf)
		encoder.EncodeArrayLen(1)
		encoder.EncodeFloat64(100)
		encoder.EncodeArrayLen(1)
		encoder.EncodeFloat64(-100)
		encoder.EncodeArrayLen(1)
		encoder.EncodeFloat64(400)

		expectedResponse := bytes.Buffer{}
		encoder = msgpack.NewEncoder(&expectedResponse)
		encoder.EncodeArrayLen(1)
		encoder.EncodeFloat64(200)
		encoder.EncodeArrayLen(1)
		encoder.EncodeFloat64(-200)
		encoder.EncodeArrayLen(1)
		encoder.EncodeFloat64(800)

		response, err := http.Post(f.url+"/apply", server.MimeMsgPack, &buf)
		assert.NoError(t, err)
		assert.Equal(t, http.StatusOK, response.StatusCode)
		assert.Equal(t, server.MimeMsgPack, response.Header.Get(server.HeaderContentType))
		content, _ := ioutil.ReadAll(response.Body)
		assert.Equal(t, expectedResponse.Bytes(), content)
	})
}
