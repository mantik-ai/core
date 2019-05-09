package runner

import "select/services/selectbridge/ops"

// A Program which can be executed by the runner
// Note: this is part of the API
type Program struct {
	Args           int        `json:"args"`
	RetStackDepth  int        `json:"retStackDepth"`
	StackInitDepth int        `json:"stackInitDepth"`
	Ops            ops.OpList `json:"ops"`
}
