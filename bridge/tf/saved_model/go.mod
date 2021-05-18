module tfbridge

require (
	github.com/buger/jsonparser v1.1.1 // indirect
	github.com/golang/protobuf v1.5.2
	github.com/kr/text v0.2.0 // indirect
	github.com/pkg/errors v0.9.1
	github.com/stretchr/testify v1.7.0
	github.com/tensorflow/tensorflow v1.15.5
	github.com/vmihailenco/msgpack v4.0.4+incompatible
	gl.ambrosys.de/mantik/go_shared v0.0.0
	google.golang.org/appengine v1.6.7 // indirect
	gopkg.in/check.v1 v1.0.0-20201130134442-10cb98267c6c // indirect
)

replace gl.ambrosys.de/mantik/go_shared => ../../../go_shared

replace gl.ambrosys.de/mantik/core/mnp/mnpgo => ../../../mnp/mnpgo

go 1.16
