package pipeline

import (
	"fmt"
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/element/builder"
	"gl.ambrosys.de/mantik/go_shared/serving"
	"gl.ambrosys.de/mantik/go_shared/serving/server"
	"gl.ambrosys.de/mantik/go_shared/serving/test"
	"testing"
)

/** Holds multiple running servers for testing pipelines */
type TestContext struct {
	servers []*server.Server
}

/** Add a new server, returns the apply URL. */
func (c *TestContext) Add(algorithm serving.ExecutableAlgorithm) string {
	server, err := server.CreateServerForExecutable(algorithm, ":0")
	if err != nil {
		panic(err.Error())
	}
	err = server.Listen()
	if err != nil {
		panic(err.Error())
	}
	go func() {
		server.Serve()
	}()
	c.servers = append(c.servers, server)
	url := fmt.Sprintf("http://localhost:%d/apply", server.ListenPort)
	return url
}

func (c *TestContext) CloseAll() {
	for _, s := range c.servers {
		s.Close()
	}
}

func TestEmptyPipeline(t *testing.T) {
	pipe := Pipeline{
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

	pipe := Pipeline{
		[]PiplineStep{{url1, ds.Ref(ds.Int32)}},
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

	pipe := Pipeline{
		[]PiplineStep{
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

	pipe := Pipeline{
		[]PiplineStep{
			{url1, ds.Ref(type2)},
			{url2, ds.Ref(type3)},
			{url3, ds.Ref(type4)},
		},
		ds.Ref(type1),
		"Three Tabular Pipeline",
	}

	runner, err := CreateRequestRunner(&pipe)
	assert.NoError(t, err)

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

	// Empty test
	got, err = runner.Execute([]element.Element{})
	assert.NoError(t, err)
	assert.Equal(t, []element.Element(nil), got)
}
