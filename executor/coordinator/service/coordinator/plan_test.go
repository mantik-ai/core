package coordinator

import (
	"encoding/json"
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestPlanSerialization(t *testing.T) {
	jsonCode := `
{
	"nodes":{"A":{"address":"localhost:50501"}, "B":{"address":"localhost:50502"}}, 
	"flows":[[{"node": "A", "resource": "in", "contentType": "application/x-mantik-bundle"}, {"node": "B", "resource": "out"}]]
}`
	var p Plan
	err := json.Unmarshal([]byte(jsonCode), &p)
	assert.NoError(t, err)

	ct := "application/x-mantik-bundle"

	expected := Plan{
		Nodes: map[string]Node{
			"A": {"localhost:50501"},
			"B": {"localhost:50502"},
		},
		Flows: []Flow{
			{NodeResourceRef{"A", "in", &ct}, NodeResourceRef{"B", "out", nil}},
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
