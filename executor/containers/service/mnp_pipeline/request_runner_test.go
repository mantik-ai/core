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
package mnp_pipeline

import (
	"encoding/json"
	"fmt"
	"github.com/golang/protobuf/ptypes"
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/core/mnp/mnpgo/client"
	server2 "gl.ambrosys.de/mantik/core/mnp/mnpgo/server"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/element/builder"
	"gl.ambrosys.de/mantik/go_shared/protos/mantik/bridge"
	"gl.ambrosys.de/mantik/go_shared/serving"
	"gl.ambrosys.de/mantik/go_shared/serving/mnpbridge"
	"gl.ambrosys.de/mantik/go_shared/serving/test"
	"testing"
)

/** Holds multiple running servers for testing pipelines */
type TestContext struct {
	servers []*TestMnpServer
}

type TestMnpServer struct {
	mnpServer  *server2.Server
	mnpBackend *mnpbridge.MnpBackend
}

type TestBackend struct {
	algorithm serving.ExecutableAlgorithm
}

func (t *TestBackend) LoadModel(payloadDir *string, mantikHeader serving.MantikHeader) (serving.Executable, error) {
	return t.algorithm, nil
}

/** Add a new server, returns the apply URL. */
func (c *TestContext) Add(algorithm serving.ExecutableAlgorithm) string {
	testBackend := TestBackend{algorithm}
	mnpBridge, err := mnpbridge.NewMnpBackend(&testBackend, "testBackend", nil)
	if err != nil {
		panic(err.Error())
	}
	mnpServer := server2.NewServer(mnpBridge)
	err = mnpServer.Listen("127.0.0.1:0")
	if err != nil {
		panic(err.Error())
	}
	go func() {
		mnpServer.Serve()
	}()

	// init session
	mnpClient, err := client.ConnectClient(fmt.Sprintf("127.0.0.1:%d", mnpServer.Port()))
	if err != nil {
		panic(err.Error())
	}
	testSession := "testSession"

	baseHeader := serving.AlgorithmMantikHeader{Type: algorithm.Type()}
	baseHeaderMarshalled, err := json.Marshal(baseHeader)
	if err != nil {
		panic(err.Error())
	}
	mantikHeader := string(baseHeaderMarshalled)

	init := bridge.MantikInitConfiguration{
		Header: mantikHeader,
	}
	marshalledInit, err := ptypes.MarshalAny(&init)
	if err != nil {
		panic(err.Error())
	}

	_, err = mnpClient.Init(testSession, marshalledInit, &assumedPortConfig, nil)
	if err != nil {
		panic(err.Error())
	}
	testMnpServer := &TestMnpServer{mnpServer, mnpBridge}
	c.servers = append(c.servers, testMnpServer)
	url := fmt.Sprintf("mnp://127.0.0.1:%d/%s", mnpServer.Port(), testSession)
	return url
}

func (c *TestContext) CloseAll() {
	for _, s := range c.servers {
		s.mnpServer.Stop()
		s.mnpBackend.Quit()
	}
}

func TestEmptyPipeline(t *testing.T) {
	pipe := MnpPipeline{
		nil,
		ds.Ref(ds.Int32),
		"Empty Pipeline",
	}
	runner, err := CreateRequestRunner(&pipe)
	assert.NoError(t, err)
	in := builder.PrimitiveElements(int32(42))
	out, err := runner.Execute(in)
	assert.NoError(t, err)
	assert.Equal(t, in, out)
}

func TestEmptyPipelineTabular(t *testing.T) {
	c := TestContext{}
	defer c.CloseAll()

	type1 := ds.BuildTabular().Add("x", ds.Int32).Add("y", ds.Int32).Result()

	pipe := MnpPipeline{
		nil,
		ds.Ref(type1),
		"Empty Pipeline",
	}
	runner, err := CreateRequestRunner(&pipe)
	assert.NoError(t, err)

	in := builder.RowsAsElements(
		builder.PrimitiveRow(int32(3), int32(4)),
		builder.PrimitiveRow(int32(2), int32(3)),
	)
	out, err := runner.Execute(in)
	assert.NoError(t, err)
	assert.Equal(t, in, out)

	empty := builder.RowsAsElements()
	out, err = runner.Execute(empty)
	assert.NoError(t, err)
	assert.Equal(t, empty, out)
}

func TestSingleNode(t *testing.T) {
	c := TestContext{}
	defer c.CloseAll()
	url1 := c.Add(
		test.CreateExecutableAlgorithm(ds.Int32, ds.Int32, func(e element.Element) (element.Element, error) {
			return element.Primitive{
				e.(element.Primitive).X.(int32) + 1,
			}, nil
		}),
	)

	pipe := MnpPipeline{
		[]MnpPipelineStep{{url1, ds.Ref(ds.Int32)}},
		ds.Ref(ds.Int32),
		"Simple Pipeline",
	}

	runner, err := CreateRequestRunner(&pipe)
	assert.NoError(t, err)

	in := builder.PrimitiveElements(int32(5))
	expected := builder.PrimitiveElements(int32(6))

	got, err := runner.Execute(in)
	assert.NoError(t, err)
	assert.Equal(t, expected, got)
}

func TestSingleNodeTabular(t *testing.T) {
	c := TestContext{}
	defer c.CloseAll()

	type1 := ds.BuildTabular().Add("x", ds.Int32).Add("y", ds.Int32).Result()
	type2 := ds.BuildTabular().Add("sum", ds.Int32).Result()

	url1 := c.Add(
		test.CreateExecutableRowWiseTabularAlgorithm(type1, type2, func(in []element.Element) ([]element.Element, error) {
			x := in[0].(element.Primitive).X.(int32)
			y := in[1].(element.Primitive).X.(int32)
			return []element.Element{element.Primitive{int32(x + y)}}, nil
		}),
	)

	pipe := MnpPipeline{
		[]MnpPipelineStep{
			{url1, ds.Ref(type2)},
		},
		ds.Ref(type1),
		"Double Pipeline",
	}

	runner, err := CreateRequestRunner(&pipe)
	assert.NoError(t, err)

	in := builder.RowsAsElements(
		builder.PrimitiveRow(int32(3), int32(4)),
		builder.PrimitiveRow(int32(2), int32(3)),
	)
	expected := builder.RowsAsElements(
		builder.PrimitiveRow(int32(7)),
		builder.PrimitiveRow(int32(5)),
	)

	got, err := runner.Execute(in)
	assert.NoError(t, err)
	assert.Equal(t, expected, got)

	// Empty test
	got, err = runner.Execute([]element.Element{})
	assert.NoError(t, err)
	assert.Equal(t, []element.Element(nil), got)
}

func TestDoubleNode(t *testing.T) {
	c := TestContext{}
	defer c.CloseAll()
	url1 := c.Add(
		test.CreateExecutableAlgorithm(ds.Int32, ds.Float32, func(e element.Element) (element.Element, error) {
			return element.Primitive{
				float32(e.(element.Primitive).X.(int32) * 2.0),
			}, nil
		}),
	)
	url2 := c.Add(
		test.CreateExecutableAlgorithm(ds.Float32, ds.Float64, func(e element.Element) (element.Element, error) {
			return element.Primitive{
				float64(e.(element.Primitive).X.(float32) * 3.0),
			}, nil
		}),
	)

	pipe := MnpPipeline{
		[]MnpPipelineStep{
			{url1, ds.Ref(ds.Float32)},
			{url2, ds.Ref(ds.Float64)},
		},
		ds.Ref(ds.Int32),
		"Two Pipeline",
	}

	runner, err := CreateRequestRunner(&pipe)
	assert.NoError(t, err)

	in := builder.PrimitiveElements(int32(5))
	expected := builder.PrimitiveElements(float64(30))

	got, err := runner.Execute(in)
	assert.NoError(t, err)
	assert.Equal(t, expected, got)

	in2 := builder.PrimitiveElements(int32(6))
	expected2 := builder.PrimitiveElements(float64(36))

	got, err = runner.Execute(in2)
	assert.NoError(t, err)
	assert.Equal(t, expected2, got)
}

func TestThreeNodeTabular(t *testing.T) {
	c := TestContext{}
	defer c.CloseAll()

	type1 := ds.BuildTabular().Add("x", ds.Int32).Add("y", ds.Int32).Result()
	type2 := ds.BuildTabular().Add("sum", ds.Int32).Result()
	type3 := ds.BuildTabular().Add("s", ds.String).Result()
	type4 := ds.BuildTabular().Add("s", ds.String).Add("l", ds.Int32).Result()

	url1 := c.Add(
		test.CreateExecutableRowWiseTabularAlgorithm(type1, type2, func(in []element.Element) ([]element.Element, error) {
			x := in[0].(element.Primitive).X.(int32)
			y := in[1].(element.Primitive).X.(int32)
			return []element.Element{element.Primitive{int32(x + y)}}, nil
		}),
	)
	url2 := c.Add(
		test.CreateExecutableRowWiseTabularAlgorithm(type2, type3, func(in []element.Element) ([]element.Element, error) {
			sum := in[0].(element.Primitive).X.(int32)
			res := fmt.Sprintf("%d", sum)
			return []element.Element{element.Primitive{res}}, nil
		}),
	)
	url3 := c.Add(
		test.CreateExecutableRowWiseTabularAlgorithm(type3, type4, func(in []element.Element) ([]element.Element, error) {
			s := in[0].(element.Primitive).X.(string)
			l := int32(len(s))
			return []element.Element{element.Primitive{s}, element.Primitive{l}}, nil
		}),
	)

	pipe := MnpPipeline{
		[]MnpPipelineStep{
			{url1, ds.Ref(type2)},
			{url2, ds.Ref(type3)},
			{url3, ds.Ref(type4)},
		},
		ds.Ref(type1),
		"Three Tabular Pipeline",
	}

	runner, err := CreateRequestRunner(&pipe)
	assert.NoError(t, err)

	/*
		in := builder.RowsAsElements(
			builder.PrimitiveRow(int32(3), int32(4)),
			builder.PrimitiveRow(int32(2), int32(3)),
			builder.PrimitiveRow(int32(-2), int32(101)),
		)
		expected := builder.RowsAsElements(
			builder.PrimitiveRow("7", int32(1)),
			builder.PrimitiveRow("5", int32(1)),
			builder.PrimitiveRow("99", int32(2)),
		)

		got, err := runner.Execute(in)
		assert.NoError(t, err)
		assert.Equal(t, expected, got)
	*/
	// Empty test
	got, err := runner.Execute([]element.Element{})
	assert.NoError(t, err)
	assert.Equal(t, []element.Element(nil), got)
}
