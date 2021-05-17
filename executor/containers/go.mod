module coordinator

require (
	github.com/golang/protobuf v1.5.2
	github.com/google/uuid v1.1.1
	github.com/pkg/errors v0.8.1
	github.com/sirupsen/logrus v1.3.0
	github.com/stretchr/testify v1.3.0
	gl.ambrosys.de/mantik/core/mnp/mnpgo v0.0.0
	gl.ambrosys.de/mantik/go_shared v0.0.0
	golang.org/x/sync v0.0.0-20190423024810-112230192c58
	google.golang.org/grpc v1.27.1
)

replace gl.ambrosys.de/mantik/go_shared => ../../go_shared

replace gl.ambrosys.de/mantik/core/mnp/mnpgo => ../../mnp/mnpgo

go 1.13
