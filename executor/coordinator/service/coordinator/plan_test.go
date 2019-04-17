package coordinator

import (
	"encoding/json"
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestPlanSerialization(t *testing.T) {
	jsonCode := `
{
	"nodes":{"A":{"address":"localhost:50501"}, "B":{"address":"localhost:50502"}, "C":{"url":"http://file-service"}}, 
	"flows":[[{"node": "A", "resource": "in", "contentType": "application/x-mantik-bundle"}, {"node": "B", "resource": "out"}, {"node": "C", "resource":"final"}]]
}`
	var p Plan
	err := json.Unmarshal([]byte(jsonCode), &p)
	assert.NoError(t, err)

	ct := "application/x-mantik-bundle"

	host1 := "localhost:50501"
	host2 := "localhost:50502"
	url3 := "http://file-service"

	expected := Plan{
		Nodes: map[string]Node{
			"A": {&host1, nil},
			"B": {&host2, nil},
			"C": {nil, &url3},
		},
		Flows: []Flow{
			{
				NodeResourceRef{"A", "in", &ct},
				NodeResourceRef{"B", "out", nil},
				NodeResourceRef{"C", "final", nil},
			},
		},
	}
	assert.Equal(t, expected, p)

	jsonCode2, err := json.Marshal(p)
	assert.NoError(t, err)
	var parsed2 Plan
	err = json.Unmarshal(jsonCode2, &parsed2)
	assert.NoError(t, err)
	assert.Equal(t, expected, parsed2)
}

func TestInitializiers(t *testing.T) {
	node := MakeAddressNode("127.0.0.1", 1234)
	addr := "127.0.0.1:1234"
	assert.Equal(t, node, Node{Address: &addr})
	assert.True(t, node.IsSideCarNode())
	assert.False(t, node.IsExternalNode())
	url := "http://foo-bar"
	node2 := MakeExternalNode("http://foo-bar")
	assert.Equal(t, node2, Node{Url: &url})
	assert.False(t, node2.IsSideCarNode())
	assert.True(t, node2.IsExternalNode())
}
