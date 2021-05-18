module coordinator

require (
	github.com/golang/protobuf v1.5.2
	github.com/google/uuid v1.1.2
	github.com/pkg/errors v0.9.1
	github.com/sirupsen/logrus v1.8.1
	github.com/stretchr/testify v1.7.0
	gl.ambrosys.de/mantik/core/mnp/mnpgo v0.0.0
	gl.ambrosys.de/mantik/go_shared v0.0.0
	golang.org/x/sync v0.0.0-20201020160332-67f06af15bc9
	google.golang.org/grpc v1.37.0
	google.golang.org/protobuf v1.26.0
)

replace gl.ambrosys.de/mantik/go_shared => ../../go_shared

replace gl.ambrosys.de/mantik/core/mnp/mnpgo => ../../mnp/mnpgo

go 1.16
