module select

require (
	github.com/buger/jsonparser v1.1.1 // indirect
	github.com/pkg/errors v0.9.1
	github.com/stretchr/testify v1.7.0
	github.com/vmihailenco/msgpack v4.0.4+incompatible // indirect
	github.com/mantik-ai/core/go_shared v0.0.0
	golang.org/x/sync v0.0.0-20210220032951-036812b2e83c
	google.golang.org/appengine v1.6.7 // indirect
)

replace github.com/mantik-ai/core/go_shared => ../../go_shared

replace github.com/mantik-ai/core/mnp/mnpgo => ../../mnp/mnpgo

go 1.16
