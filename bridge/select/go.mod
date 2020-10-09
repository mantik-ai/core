module select

require (
	github.com/pkg/errors v0.8.1
	github.com/stretchr/testify v1.3.0
	gl.ambrosys.de/mantik/go_shared v0.0.0
	golang.org/x/sync v0.0.0-20190423024810-112230192c58
)

replace gl.ambrosys.de/mantik/go_shared => ../../go_shared

replace gl.ambrosys.de/mantik/core/mnp/mnpgo => ../../mnp/mnpgo

go 1.13
