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
package mnpbridge

import (
	"bytes"
	"context"
	"fmt"
	"github.com/golang/protobuf/ptypes"
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/client"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/protos/mantik/mnp"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/server"
	"gl.ambrosys.de/mantik/go_shared/protos/mantik/bridge"
	"gl.ambrosys.de/mantik/go_shared/serving"
	server2 "gl.ambrosys.de/mantik/go_shared/serving/server"
	"gl.ambrosys.de/mantik/go_shared/serving/test"
	"gl.ambrosys.de/mantik/go_shared/util/dirzip"
	"gl.ambrosys.de/mantik/go_shared/util/httpext"
	"gl.ambrosys.de/mantik/go_shared/util/osext"
	"io/ioutil"
	"net/http"
	"os"
	"testing"
)

type simpleTestEnv struct {
	t          *testing.T
	executable serving.Executable
	backend    *test.TestBackend
	mnpHandler mnpgo.Handler
	mnpServer  *server.Server
	mnpClient  *client.Client
}

func makeEnv(t *testing.T, executable serving.Executable) *simpleTestEnv {
	var r simpleTestEnv
	r.backend = test.NewTestBackend(executable)
	r.t = t

	mnpHandler, err := NewMnpBackend(r.backend, "test1", r.backend.Shutdown)
	assert.NoError(t, err)
	r.mnpHandler = mnpHandler

	r.mnpServer = server.NewServer(mnpHandler)

	err = r.mnpServer.Listen("127.0.0.1:0")
	assert.NoError(t, err)

	go func() {
		r.mnpServer.Serve()
	}()

	mnpClient, err := client.ConnectClient(r.mnpServer.Address())
	assert.NoError(t, err)
	r.mnpClient = mnpClient

	return &r
}

func (t *simpleTestEnv) initWithoutPayload(
	mantikHeader string,
	portConf *mnpgo.PortConfiguration,
) mnpgo.SessionHandler {
	return t.init(
		mantikHeader,
		portConf,
		"",
		"",
		nil,
	)
}

func (t *simpleTestEnv) init(
	mantikHeader string,
	portConf *mnpgo.PortConfiguration,
	payloadContentType string,
	payloadUrl string,
	payloadContent []byte,
) mnpgo.SessionHandler {
	mantikHeaderParsed, err := serving.ParseMantikHeader([]byte(mantikHeader))
	assert.NoError(t.t, err)

	// Initializing A session
	conf := bridge.MantikInitConfiguration{
		Header:             string(mantikHeader),
		PayloadContentType: payloadContentType,
	}
	if len(payloadUrl) > 0 {
		conf.Payload = &bridge.MantikInitConfiguration_Url{Url: payloadUrl}
	}
	if len(payloadContent) > 0 {
		conf.Payload = &bridge.MantikInitConfiguration_Content{Content: payloadContent}
	}

	confEncoded, err := ptypes.MarshalAny(&conf)
	assert.NoError(t.t, err)

	var states []mnp.SessionState
	var stateCallback mnpgo.StateCallback = func(state mnp.SessionState) {
		states = append(states, state)
	}
	sessionHandler, err := t.mnpClient.Init("session1", confEncoded, portConf, stateCallback)
	assert.NoError(t.t, err)

	assert.Equal(t.t, 1, len(t.backend.Instantiations))
	instantiation := t.backend.Instantiations[0]
	assert.Equal(t.t, mantikHeaderParsed, instantiation.MantikHeader)
	if len(payloadContentType) == 0 {
		assert.Nil(t.t, instantiation.Payload)
	}
	return sessionHandler
}

const algorithmMantikFile = `
type:
  input:
    columns:
      x: int32
  output:
    columns:
      x: int32
`

func (s *simpleTestEnv) Stop() {
	s.mnpServer.Stop()
}

func TestMnpBridgeAbout(t *testing.T) {
	algorithm := test.NewThreeTimes()
	env := makeEnv(t, algorithm)
	defer env.Stop()

	aboutResponse, err := env.mnpClient.About()
	assert.NoError(t, err)
	assert.Equal(t, "test1", aboutResponse.Name)

	err = env.mnpClient.Quit()
	assert.NoError(t, err)
	assert.True(t, env.backend.Closed)
}

func TestMnpSingleSession(t *testing.T) {
	algorithm := test.NewThreeTimes()
	env := makeEnv(t, algorithm)
	defer env.Stop()

	portConf := mnpgo.PortConfiguration{
		Inputs: []mnpgo.InputPortConfiguration{
			{server2.MimeJson},
		},
		Outputs: []mnpgo.OutputPortConfiguration{
			{server2.MimeJson, ""},
		},
	}

	sessionHandler := env.initWithoutPayload(
		algorithmMantikFile,
		&portConf,
	)

	// Testing data flow
	input := "[[100],[200]]"
	result, err := mnpgo.RunTaskWithBytes(
		sessionHandler,
		context.Background(),
		"task1",
		[][]byte{[]byte(input)},
	)
	assert.NoError(t, err)
	assert.Equal(t, 1, len(result))
	assert.Equal(t, "[[300],[600]]", string(result[0]))

	// Closing
	err = sessionHandler.Quit()
	assert.NoError(t, err)
}

func TestSessionWithDownload(t *testing.T) {
	algorithm := test.NewThreeTimes()
	env := makeEnv(t, algorithm)
	defer env.Stop()

	var payload bytes.Buffer
	err := dirzip.ZipDirectoryToStream("../../test/resources/dummy/payload", false, &payload)
	assert.NoError(t, err)

	httpServer := httpext.ServerExt{}

	serveMux := http.NewServeMux()
	serveMux.HandleFunc("/foobar", func(writer http.ResponseWriter, request *http.Request) {
		writer.WriteHeader(200)
		writer.Write(payload.Bytes())
	})

	httpServer.Handler = serveMux

	httpServer.Listen()

	go func() {
		err := httpServer.Serve()
		assert.NoError(t, err)
		err = httpServer.Close()
		assert.NoError(t, err)
	}()

	url := fmt.Sprintf("http://localhost:%d/foobar", httpServer.ListenPort)

	portConf := mnpgo.PortConfiguration{
		Inputs: []mnpgo.InputPortConfiguration{
			{server2.MimeJson},
		},
		Outputs: []mnpgo.OutputPortConfiguration{
			{server2.MimeJson, ""},
		},
	}

	sessionHandler := env.init(
		algorithmMantikFile,
		&portConf,
		server2.MimeZip,
		url,
		nil,
	)

	instantiation := env.backend.Instantiations[0]
	assert.NotNil(t, instantiation.Payload)
	assert.DirExists(t, *instantiation.Payload)

	err = sessionHandler.Quit()
	assert.NoError(t, err)
	assert.False(t, osext.IsDirectory(*instantiation.Payload))

	httpServer.Close()
}

func TestSessionPayloadEmbedded(t *testing.T) {
	algorithm := test.NewThreeTimes()
	env := makeEnv(t, algorithm)
	defer env.Stop()

	var payload bytes.Buffer
	err := dirzip.ZipDirectoryToStream("../../test/resources/dummy/payload", false, &payload)
	assert.NoError(t, err)

	portConf := mnpgo.PortConfiguration{
		Inputs: []mnpgo.InputPortConfiguration{
			{server2.MimeJson},
		},
		Outputs: []mnpgo.OutputPortConfiguration{
			{server2.MimeJson, ""},
		},
	}

	sessionHandler := env.init(
		algorithmMantikFile,
		&portConf,
		server2.MimeZip,
		"",
		payload.Bytes(),
	)

	instantiation := env.backend.Instantiations[0]
	assert.NotNil(t, instantiation.Payload)
	assert.DirExists(t, *instantiation.Payload)

	err = sessionHandler.Quit()
	assert.NoError(t, err)
	assert.False(t, osext.IsDirectory(*instantiation.Payload))
}

func TestSessionTrainable(t *testing.T) {
	learnAlg := test.NewLearnAlgorithm()

	mantikHeader :=
		`{
	"kind":"trainable",
	"trainingType": {
		"columns": {
			"a1": "int8"
		}
	},
	"statType": {
		"columns": {
			"o1": "int64"
		}
	},
	"type": {
		"input": {
			"columns": {
				"t1": "int8"
			}
		},
		"output": {
			"columns": {
				"t2": "int64"
			}
		}
	}
}
`
	env := makeEnv(t, learnAlg)
	defer env.Stop()

	portConf := mnpgo.PortConfiguration{
		Inputs: []mnpgo.InputPortConfiguration{
			{server2.MimeJson},
		},
		Outputs: []mnpgo.OutputPortConfiguration{
			{server2.MimeZip, ""}, {server2.MimeJson, ""},
		},
	}

	sessionHandler := env.initWithoutPayload(
		mantikHeader,
		&portConf,
	)

	// Testing data flow
	input := "[[3],[5]]"
	result, err := mnpgo.RunTaskWithBytes(
		sessionHandler,
		context.Background(),
		"task1",
		[][]byte{[]byte(input)},
	)
	assert.NoError(t, err)
	assert.Equal(t, 2, len(result))
	assert.Equal(t, "[[8]]", string(result[1]))

	tempDir, err := ioutil.TempDir("", "mantiktest")
	assert.NoError(t, err)
	defer os.RemoveAll(tempDir)
	err = dirzip.UnzipDirectoryFromZipBuffer(*bytes.NewBuffer(result[0]), tempDir, true)
	assert.NoError(t, err)

	// Closing
	err = sessionHandler.Quit()
	assert.NoError(t, err)
}

func TestSessionDataSet(t *testing.T) {
	dataset := test.NewDataSet()
	env := makeEnv(t, dataset)
	defer env.Stop()

	mantikHeader := `
kind: dataset
type:
  columns:
    x: string
    y: int32
`
	portConf := mnpgo.PortConfiguration{
		Inputs: []mnpgo.InputPortConfiguration{},
		Outputs: []mnpgo.OutputPortConfiguration{
			{server2.MimeJson, ""},
		},
	}

	sessionHandler := env.initWithoutPayload(
		mantikHeader,
		&portConf,
	)

	// Testing data flow
	result, err := mnpgo.RunTaskWithBytes(
		sessionHandler,
		context.Background(),
		"task1",
		[][]byte{},
	)
	assert.NoError(t, err)
	assert.Equal(t, 1, len(result))
	assert.Equal(t, `[["Hello",1],["World",2]]`, string(result[0]))

	// Closing
	err = sessionHandler.Quit()
	assert.NoError(t, err)
}

func TestSessionTransformer(t *testing.T) {
	transformer := test.NewTransformer()

	env := makeEnv(t, transformer)
	defer env.Stop()

	mantikHeader := `
kind: combiner
input:
  - columns:
      x: int32
  - float32
output:
  - float64
`
	portConf := mnpgo.PortConfiguration{
		Inputs: []mnpgo.InputPortConfiguration{
			{server2.MimeJson}, {server2.MimeJson},
		},
		Outputs: []mnpgo.OutputPortConfiguration{
			{server2.MimeJson, ""},
		},
	}

	sessionHandler := env.initWithoutPayload(
		mantikHeader,
		&portConf,
	)

	input1 := "[[1],[2],[3]]"
	input2 := "4.0"
	expected := "24"

	// Testing data flow
	result, err := mnpgo.RunTaskWithBytes(
		sessionHandler,
		context.Background(),
		"task1",
		[][]byte{[]byte(input1), []byte(input2)},
	)
	assert.NoError(t, err)
	assert.Equal(t, 1, len(result))
	assert.Equal(t, expected, string(result[0]))

	// Closing
	err = sessionHandler.Quit()
	assert.NoError(t, err)
}
