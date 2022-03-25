module csv

require (
	github.com/pkg/errors v0.9.1
	github.com/mantik-ai/core/go_shared v0.0.0
	github.com/stretchr/testify v1.7.0
)

replace github.com/mantik-ai/core/go_shared => ../../go_shared

replace github.com/mantik-ai/core/mnp/mnpgo => ../../mnp/mnpgo

go 1.16
