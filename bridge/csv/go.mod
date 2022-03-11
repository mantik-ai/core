module csv

require (
	github.com/pkg/errors v0.9.1
	gl.ambrosys.de/mantik/go_shared v0.0.0
	github.com/stretchr/testify v1.7.0
)

replace gl.ambrosys.de/mantik/go_shared => ../../go_shared

replace gl.ambrosys.de/mantik/core/mnp/mnpgo => ../../mnp/mnpgo

go 1.16
